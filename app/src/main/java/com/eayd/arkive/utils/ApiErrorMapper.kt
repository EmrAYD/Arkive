package com.eayd.arkive.utils

import com.eayd.arkive.i18n.Txt
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject
import retrofit2.HttpException

/** Sunucunun hata gövdesindeki "detail" mesajı. */
private fun HttpException.serverDetail(): String? = try {
    val body = response()?.errorBody()?.string()
    if (body.isNullOrBlank()) null else JSONObject(body).opt("detail") as? String
} catch (e: Exception) {
    null
}

/** Ağ ve sunucu hatalarını kullanıcıya gösterilecek mesaja çevirir. */
fun Throwable.toApiErrorMessage(): String = when (this) {
    is HttpException -> when (val c = code()) {
        400 -> serverDetail() ?: Txt.errors.badRequest
        409 -> serverDetail() ?: Txt.errors.conflict
        413 -> serverDetail() ?: Txt.errors.tooLargeOrNoSpace
        503 -> serverDetail() ?: Txt.errors.serviceUnavailable
        401 -> Txt.errors.sessionInvalid
        403 -> Txt.errors.forbidden
        404 -> Txt.errors.notFound
        422 -> Txt.errors.invalidInput
        in 500..599 -> Txt.errors.serverError
        else -> Txt.errors.requestFailed(c)
    }
    is SocketTimeoutException -> Txt.errors.serverTimeout
    is UnknownHostException, is ConnectException -> Txt.errors.serverUnreachable
    is IOException -> message ?: Txt.errors.connectionError
    else -> message ?: Txt.errors.unexpected
}
