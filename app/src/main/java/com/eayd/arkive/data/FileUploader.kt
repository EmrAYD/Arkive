package com.eayd.arkive.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException

/** Seçilen dosyanın yükleme için gereken bilgileri. */
private class FileMeta(val name: String, val size: Long, val mimeType: String)

/**
 * Bir dosyayı sunucuya yükleyen akış. Backend'in 3 adımlı tasarımını uygular:
 *
 *  1. `POST /files/init`        → backend kuralları denetler, dosya kaydını açar, imzalı adres verir.
 *  2. `PUT <imzalı adres>`      → dosya DOĞRUDAN RustFS'e gider (backend'den geçmez), ilerleme burada izlenir.
 *  3. `POST /files/{id}/complete` → backend dosyanın depolamada gerçekten var olduğunu doğrular, "ready" yapar.
 *
 * Bir yerde hata olur ya da kullanıcı iptal ederse, 1. adımda açılan kayıt sunucudan silinir; böylece
 * "yükleniyor" durumunda takılı kalan ve kotadan yer tutan kayıtlar birikmez.
 */
object FileUploader {

    // Sunucudaki mime_type doğrulamasının aynısı; uymayan tür "genel ikili dosya" sayılır.
    private val mimeRegex = Regex("[A-Za-z0-9!#\\$&^_.+-]{1,127}/[A-Za-z0-9!#\\$&^_.+-]{1,127}")
    private const val FALLBACK_MIME = "application/octet-stream"

    /** Seçici penceresinde görünen dosya adı. Yüklenme sırasında listede göstermek için hızlıca okunur. */
    fun queryName(resolver: ContentResolver, uri: Uri): String {
        val raw = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "dosya"
        // Sunucu '/' ve '\' içeren adı ve 255 karakterden uzununu reddeder.
        return raw.replace('/', '_').replace('\\', '_').trim().take(255).ifEmpty { "dosya" }
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()
        if (fromProvider != null && fromProvider >= 0) return fromProvider

        // Bazı kaynaklar boyutu bildirmez: akışı sayarak buluruz (yalnızca bu nadir durumda tüm dosya okunur).
        val input = resolver.openInputStream(uri) ?: throw IOException("Dosya açılamadı")
        return input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
            }
            total
        }
    }

    private fun queryMime(resolver: ContentResolver, uri: Uri, name: String): String {
        val fromResolver = resolver.getType(uri)
        val fromExtension = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        val candidate = (fromResolver ?: fromExtension)?.lowercase()
        return if (candidate != null && mimeRegex.matches(candidate)) candidate else FALLBACK_MIME
    }

    private fun readMeta(resolver: ContentResolver, uri: Uri): FileMeta {
        val name = queryName(resolver, uri)
        return FileMeta(name, querySize(resolver, uri), queryMime(resolver, uri, name))
    }

    /**
     * Dosyayı yükler ve sunucudaki hazır kaydı döner.
     * @param onProgress  0f..1f; yalnızca yüzde değeri değiştiğinde çağrılır (arayüzü boğmamak için).
     * @param onFinishing dosya depolamaya ulaştı, sunucu doğrulaması (3. adım) başlıyor.
     */
    suspend fun upload(
        resolver: ContentResolver,
        uri: Uri,
        folderId: String?,
        onProgress: (Float) -> Unit,
        onFinishing: () -> Unit,
    ): FileDto {
        NetworkPolicy.requireAllowed() // "Yalnızca Wi-Fi" açıksa sunucuda kayıt açılmadan reddedilir
        val meta = withContext(Dispatchers.IO) { readMeta(resolver, uri) }
        if (meta.size == 0L) throw IOException("Boş dosya yüklenemez")

        // 1. adım: hata olursa (boyut sınırı, kota…) henüz sunucuda kayıt açılmadığı için temizlik gerekmez.
        val init = ApiClient.api.initUpload(
            InitUploadRequest(meta.name, meta.size, meta.mimeType, folderId)
        )
        val fileId = init.file.id

        // 2. adım
        try {
            putToStorage(resolver, uri, meta, init, onProgress)
        } catch (e: Throwable) {
            deleteQuietly(fileId)
            throw e
        }

        // 3. adım
        onFinishing()
        try {
            return ApiClient.api.completeUpload(fileId)
        } catch (e: Throwable) {
            // Sunucu açıkça reddettiyse (HttpException) ya da kullanıcı iptal ettiyse kaydı sileriz.
            // Ağ kesintisinde (IOException) sonuç belirsizdir: sunucu belki tamamlamıştır, silersek
            // başarılı bir yüklemeyi yok etmiş oluruz. O durumda kayıt bırakılır (bkz. README: periyodik temizlik).
            if (e is HttpException || e is CancellationException) deleteQuietly(fileId)
            throw e
        }
    }

    private suspend fun putToStorage(
        resolver: ContentResolver,
        uri: Uri,
        meta: FileMeta,
        init: InitUploadResponse,
        onProgress: (Float) -> Unit,
    ) {
        val contentType = init.uploadHeaders.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value ?: meta.mimeType

        val request = Request.Builder()
            .url(init.uploadUrl)
            .put(UriRequestBody(resolver, uri, contentType, meta.size, onProgress))
            .apply {
                // Sunucunun istediği ek başlıklar aynen eklenir. Content-Type gövdeden gelir (yukarıda).
                init.uploadHeaders.forEach { (k, v) ->
                    if (!k.equals("Content-Type", ignoreCase = true)) header(k, v)
                }
            }
            .build()

        executeOnStorage(
            request = request,
            failureMessage = "Dosya depolamaya gönderilemedi",
            rejected = { code ->
                if (code == 403) "Depolama yükleme imzasını reddetti (HTTP 403). Backend ve RustFS ayarlarını kontrol edin."
                else "Depolama yüklemeyi reddetti (HTTP $code)."
            },
        ) { }
    }

    /** İptal edilmiş bir coroutine içinde bile çalışsın diye NonCancellable. Hata olursa yutulur. */
    private suspend fun deleteQuietly(fileId: String) {
        withContext(NonCancellable) {
            runCatching { ApiClient.api.deleteFile(fileId) }
        }
    }
}

/**
 * Dosyayı hiç belleğe tümüyle almadan, parça parça ağa yazan istek gövdesi.
 * Boyut önceden bilindiği için Content-Length başlığı gönderilir (imzalı adres bunu bekler).
 */
private class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val type: String,
    private val size: Long,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType() = type.toMediaTypeOrNull()
    override fun contentLength() = size

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Dosya açılamadı")
        input.source().use { source ->
            var sent = 0L
            var lastPercent = -1
            while (sent < size) {
                val read = source.read(sink.buffer, 64 * 1024L)
                if (read == -1L) break
                sink.emit()
                sent += read
                val percent = (sent * 100 / size).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent / 100f)
                }
            }
            // Dosya seçildikten sonra küçüldüyse Content-Length tutmaz; OkHttp zaten hata verir, ama anlaşılır olsun.
            if (sent != size) throw IOException("Dosya yükleme sırasında değişti")
        }
    }
}
