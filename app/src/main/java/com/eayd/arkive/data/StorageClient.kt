package com.eayd.arkive.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

/**
 * Depolama sunucusuyla (RustFS) konuşan yükleme/indirme kodunun ortak parçaları.
 * Bu adresler backend'e değil RustFS'e gider ve kendi imzaları vardır; bu yüzden [ApiClient.storageHttp] kullanılır.
 */

/** Depolama sunucusuyla ilgili hata; mesajı kullanıcıya olduğu gibi gösterilir. */
class StorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * OkHttp çağrısını coroutine'e çevirir; coroutine iptal edilirse (ör. kullanıcı yüklemeyi iptal etti ya da
 * ön izleme ekranından çıktı) ağ isteği de iptal edilir. Dönen [Response] kullanıcı tarafından kapatılmalıdır
 * (`.use { }`).
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

/**
 * [request]'i depolamaya gönderir; başarılıysa yanıtı [onSuccess]'e verir ve sonra kapatır.
 *  - Sunucu reddederse (2xx değilse) [rejected] ile üretilen mesajla [StorageException] fırlatılır.
 *  - Ağ hatasında, nereye gidilemediği mesaja eklenir: "localhost" görünüyorsa backend'de S3_PUBLIC_ENDPOINT
 *    ayarlanmamıştır, "10.0.2.2" görünüp bağlanamıyorsa RustFS'e emülatörden ulaşılamıyordur.
 */
internal suspend fun <T> executeOnStorage(
    request: Request,
    failureMessage: String,
    rejected: (httpCode: Int) -> String,
    onSuccess: suspend (Response) -> T,
): T {
    return try {
        ApiClient.storageHttp.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw StorageException(rejected(response.code))
            onSuccess(response)
        }
    } catch (e: StorageException) {
        throw e
    } catch (e: IOException) {
        val target = "${request.url.host}:${request.url.port}"
        throw StorageException("$failureMessage ($target): ${e.message ?: e.javaClass.simpleName}", e)
    }
}
