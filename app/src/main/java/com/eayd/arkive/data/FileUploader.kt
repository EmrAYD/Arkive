package com.eayd.arkive.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.eayd.arkive.i18n.Txt
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

/** Yükleme için gereken dosya bilgileri. */
private class FileMeta(val name: String, val size: Long, val mimeType: String)

/** Dosyayı sunucuya yükler: önce kayıt açılır, dosya doğrudan depolamaya gider, sonra sunucu doğrular. */
object FileUploader {

    private val mimeRegex = Regex("[A-Za-z0-9!#\\$&^_.+-]{1,127}/[A-Za-z0-9!#\\$&^_.+-]{1,127}")
    private const val FALLBACK_MIME = "application/octet-stream"

    /** Seçilen dosyanın görünen adı. */
    fun queryName(resolver: ContentResolver, uri: Uri): String {
        val raw = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "dosya"
        return raw.replace('/', '_').replace('\\', '_').trim().take(255).ifEmpty { "dosya" }
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()
        if (fromProvider != null && fromProvider >= 0) return fromProvider

        val input = resolver.openInputStream(uri) ?: throw IOException(Txt.errors.fileOpenFailed)
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
     *
     * [onProgress] 0f..1f arası, yalnızca yüzde değişince çağrılır. [onFinishing] doğrulama adımı başlayınca çağrılır.
     */
    suspend fun upload(
        resolver: ContentResolver,
        uri: Uri,
        folderId: String?,
        onProgress: (Float) -> Unit,
        onFinishing: () -> Unit,
    ): FileDto {
        NetworkPolicy.requireAllowed()
        val meta = withContext(Dispatchers.IO) { readMeta(resolver, uri) }
        if (meta.size == 0L) throw IOException(Txt.errors.emptyFileUpload)

        val init = ApiClient.api.initUpload(
            InitUploadRequest(meta.name, meta.size, meta.mimeType, folderId)
        )
        val fileId = init.file.id

        try {
            putToStorage(resolver, uri, meta, init, onProgress)
        } catch (e: Throwable) {
            deleteQuietly(fileId)
            throw e
        }

        onFinishing()
        try {
            return ApiClient.api.completeUpload(fileId)
        } catch (e: Throwable) {
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
                init.uploadHeaders.forEach { (k, v) ->
                    if (!k.equals("Content-Type", ignoreCase = true)) header(k, v)
                }
            }
            .build()

        executeOnStorage(
            request = request,
            failureMessage = Txt.errors.sendToStorageFailed,
            rejected = { code ->
                if (code == 403) Txt.errors.storageRejectedSignature
                else Txt.errors.storageRejectedUpload(code)
            },
        ) { }
    }

    /** Kaydı sessizce siler; iptal edilmiş coroutine içinde de çalışır. */
    private suspend fun deleteQuietly(fileId: String) {
        withContext(NonCancellable) {
            runCatching { ApiClient.api.deleteFile(fileId) }
        }
    }
}

/** Dosyayı belleğe almadan parça parça gönderen istek gövdesi. */
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
        val input = resolver.openInputStream(uri) ?: throw IOException(Txt.errors.fileOpenFailed)
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
            if (sent != size) throw IOException(Txt.errors.fileChangedDuringUpload)
        }
    }
}
