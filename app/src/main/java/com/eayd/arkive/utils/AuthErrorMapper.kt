package com.eayd.arkive.utils

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/**
 * Firebase hatalarını kullanıcıya gösterilecek Türkçe mesajlara çevirir.
 * Tanınmayan hatalarda ham (İngilizce) mesaj yerine [default] döner.
 *
 * Güvenlik notu: "kullanıcı yok" ve "şifre yanlış" aynı mesajla döner,
 * böylece bir e-postanın kayıtlı olup olmadığı dışarıdan anlaşılamaz.
 */
fun Throwable.toAuthErrorMessage(default: String): String = when (this) {
    is FirebaseNetworkException ->
        "İnternet bağlantısı kurulamadı. Bağlantınızı kontrol edip tekrar deneyin."

    is FirebaseTooManyRequestsException ->
        "Çok fazla deneme yapıldı. Lütfen bir süre sonra tekrar deneyin."

    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "Geçerli bir e-posta adresi giriniz."
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND" -> "E-posta veya şifre hatalı."
        "ERROR_USER_DISABLED" -> "Bu hesap devre dışı bırakılmış."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Bu e-posta adresiyle zaten bir hesap var."
        "ERROR_WEAK_PASSWORD" -> "Şifre çok zayıf. Daha güçlü bir şifre seçin."
        "ERROR_TOO_MANY_REQUESTS" -> "Çok fazla deneme yapıldı. Lütfen bir süre sonra tekrar deneyin."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Bu giriş yöntemi şu anda kullanılamıyor."
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "Bu e-posta adresi başka bir giriş yöntemiyle kayıtlı."
        "ERROR_NETWORK_REQUEST_FAILED" ->
            "İnternet bağlantısı kurulamadı. Bağlantınızı kontrol edip tekrar deneyin."
        else -> default
    }

    else -> default
}