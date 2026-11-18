package com.eayd.arkive.utils

import com.eayd.arkive.i18n.Txt
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject
import retrofit2.HttpException

/** Sunucunun hata gövdesindeki {"detail": "..."} mesajını okur (yalnızca düz metinse). */
private fun HttpException.serverDetail(): String? = try {
    val body = response()?.errorBody()?.string()
    if (body.isNullOrBlank()) null else JSONObject(body).opt("detail") as? String
} catch (e: Exception) {
    null
}

/** Ağ/sunucu hatalarını kullanıcıya gösterilecek Türkçe mesaja çevirir. */
fun Throwable.toApiErrorMessage(): String = when (this) {
    is HttpException -> when (val c = code()) {
        // 400 ve 409'da sunucunun kendi (Türkçe) açıklaması kullanıcıya uygundur,
        // ör. "Bu konumda aynı adlı bir klasör zaten var".
        400 -> serverDetail() ?: Txt.errors.badRequest
        409 -> serverDetail() ?: Txt.errors.conflict
        // 413: dosya çok büyük / depolama alanı yetersiz; 503: depolama (RustFS) hazır değil.
        // İkisinde de sunucunun açıklaması neyin yanlış olduğunu en iyi anlatır.
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
