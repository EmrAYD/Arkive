package com.eayd.arkive.data

import com.eayd.arkive.i18n.Txt
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody

/** Dosyaları depolamadan indirir; backend'den geçmez. */
object FileDownloader {

    /** Adrese GET atar ve gövdeyi [block]'a verir; ağ hatalarını anlaşılır mesaja çevirir. */
    private suspend fun <T> get(url: String, block: suspend (ResponseBody) -> T): T =
        executeOnStorage(
            request = Request.Builder().url(url).build(),
            failureMessage = Txt.errors.downloadFromStorageFailed,
            rejected = { code -> Txt.errors.storageRefusedFile(code) },
        ) { response ->
            val body = response.body ?: throw StorageException(Txt.errors.storageEmptyResponse)
            block(body)
        }

    /** Dosyayı [dest]'e indirir; [maxBytes]'ı aşarsa yarıda keser. */
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
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (done > maxBytes) throw StorageException(Txt.errors.tooBigForPreview)
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

    /** Dosyayı [output]'a yazar; [output]'u çağıran kapatır. */
    suspend fun copyTo(url: String, output: OutputStream) {
        get(url) { body ->
            withContext(Dispatchers.IO) {
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                }
            }
        }
    }

    /** Metin dosyasının ilk [maxBytes] baytını okur; ikinci değer devamının kesilip kesilmediği. */
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
