package com.eayd.arkive.utils

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.eayd.arkive.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlin.coroutines.cancellation.CancellationException

suspend fun getGoogleIdToken(context: Context): String? {
    val credentialManager = CredentialManager.create(context)
    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trimEnd('/')

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential

        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Çıkış yaparken Credential Manager'daki Google oturum durumunu temizler.
 * Bunu yapmazsak setAutoSelectEnabled(true) yüzünden bir sonraki girişte
 * aynı Google hesabı kendiliğinden seçilebilir.
 * Hata olsa bile çıkış akışını bozmaması için hatalar yutulur.
 */
suspend fun clearGoogleCredentialState(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
