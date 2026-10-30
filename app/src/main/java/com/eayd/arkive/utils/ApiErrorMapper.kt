package com.eayd.arkive.utils

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
        400 -> serverDetail() ?: "Geçersiz istek."
        409 -> serverDetail() ?: "Bu işlem bir çakışma nedeniyle yapılamadı."
        // 413: dosya çok büyük / depolama alanı yetersiz; 503: depolama (RustFS) hazır değil.
        // İkisinde de sunucunun açıklaması neyin yanlış olduğunu en iyi anlatır.
        413 -> serverDetail() ?: "Dosya çok büyük ya da depolama alanınız yetersiz."
        503 -> serverDetail() ?: "Sunucu şu anda hizmet veremiyor."
        401 -> "Oturum doğrulanamadı. Çıkış yapıp tekrar giriş yapın."
        403 -> "Bu işlem için yetkiniz yok."
        404 -> "İstenen kayıt bulunamadı."
        422 -> "Girilen bilgi geçersiz."
        in 500..599 -> "Sunucu hatası, biraz sonra tekrar deneyin."
        else -> "İstek başarısız oldu ($c)."
    }
    is SocketTimeoutException -> "Sunucu yanıt vermedi."
    is UnknownHostException, is ConnectException -> "Sunucuya ulaşılamadı. Backend çalışıyor mu?"
    is IOException -> message ?: "Bağlantı hatası."
    else -> message ?: "Beklenmeyen bir hata oluştu."
}
