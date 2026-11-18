package com.eayd.arkive.utils

import com.eayd.arkive.i18n.Txt
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
        Txt.auth.noInternet

    is FirebaseTooManyRequestsException ->
        Txt.auth.tooManyAttempts

    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> Txt.auth.invalidEmail
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND" -> Txt.auth.wrongCredentials
        "ERROR_USER_DISABLED" -> Txt.auth.accountDisabled
        "ERROR_EMAIL_ALREADY_IN_USE" -> Txt.auth.emailInUse
        "ERROR_WEAK_PASSWORD" -> Txt.auth.weakPassword
        "ERROR_TOO_MANY_REQUESTS" -> Txt.auth.tooManyAttempts
        "ERROR_OPERATION_NOT_ALLOWED" -> Txt.auth.methodUnavailable
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            Txt.auth.otherProvider
        "ERROR_NETWORK_REQUEST_FAILED" ->
            Txt.auth.noInternet
        else -> default
    }

    else -> default
}