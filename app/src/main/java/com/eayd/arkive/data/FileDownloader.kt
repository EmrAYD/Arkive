package com.eayd.arkive.data

import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody

/**
 * Ön izleme için dosyayı imzalı adresten (RustFS) alır. Yükleme gibi bu da backend'den geçmez ve
 * Authorization başlığı göndermez (bkz. [ApiClient.storageHttp]).
 */
object FileDownloader {

    /** Adrese GET atar, başarılıysa gövdeyi [block]'a verir; ağ hatalarını anlaşılır bir mesaja çevirir. */
    private suspend fun <T> get(url: String, block: suspend (ResponseBody) -> T): T =
        executeOnStorage(
            request = Request.Builder().url(url).build(),
            failureMessage = "Dosya depolamadan alınamadı",
            rejected = { code -> "Depolama dosyayı vermedi (HTTP $code)." },
        ) { response ->
            val body = response.body ?: throw StorageException("Depolamadan boş yanıt geldi.")
            block(body)
        }

    /**
     * Dosyayı [dest]'e indirir. [maxBytes]'tan büyükse yarıda keser ve hata verir.
     * [onProgress]: 0f..1f (boyut bilinmiyorsa hiç çağrılmaz); yalnızca yüzde değişince.
     */
    suspend fun downloadTo(url: String, dest: File, maxBytes: Long, onProgress: (Float) -> Unit) {
        get(url) { body ->
            val total = body.contentLength()
            withContext(Dispatchers.IO) {
                dest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            ensureActive()   // ekrandan çıkıldıysa indirmeyi bırak
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (done > maxBytes) throw StorageException("Dosya ön izleme için çok büyük.")
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Metin dosyasının en çok [maxBytes] baytını okur. Döner: (metin, dosyanın devamı kesildi mi). */
    suspend fun readText(url: String, maxBytes: Int): Pair<String, Boolean> = get(url) { body ->
        withContext(Dispatchers.IO) {
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            var truncated = false
            body.byteStream().use { input ->
                while (true) {
                    ensureActive()
                    val n = input.read(chunk)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) {
                        out.write(chunk, 0, maxBytes - out.size())
                        truncated = true
                        break
                    }
                    out.write(chunk, 0, n)
                }
            }
            String(out.toByteArray(), Charsets.UTF_8) to truncated
        }
    }
}
