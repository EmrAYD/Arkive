package com.eayd.arkive.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

/** Depolama hatası; mesajı kullanıcıya olduğu gibi gösterilir. */
class StorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** OkHttp çağrısını coroutine'e çevirir; iptal edilirse istek de iptal olur. Dönen yanıtı çağıran kapatmalı. */
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

/** İsteği depolamaya gönderir ve başarılı yanıtı [onSuccess]'e verir; hatayı anlaşılır bir mesajla atar. */
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
