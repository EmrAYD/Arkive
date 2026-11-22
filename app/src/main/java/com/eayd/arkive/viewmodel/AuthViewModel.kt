package com.eayd.arkive.viewmodel

import android.content.Context
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.clearGoogleCredentialState
import com.eayd.arkive.utils.toAuthErrorMessage
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data object Success : AuthUiState

    /** Google ile yeni hesap açıldı; sözleşme onaylanana kadar beklenir. */
    data object EulaRequired : AuthUiState
    data class Error(val message: String) : AuthUiState
}

sealed interface PasswordResetUiState {
    data object Idle : PasswordResetUiState
    data object Loading : PasswordResetUiState
    data class Success(val message: String) : PasswordResetUiState
    data class Error(val message: String) : PasswordResetUiState
}

/** E-posta alanının denetim sonucu. */
enum class EmailCheck { Empty, Valid, Invalid }

/** Kayıt şifresinin kuralları. */
data class PasswordRules(
    val longEnough: Boolean,
    val hasSpecialChar: Boolean,
    val hasUpperCase: Boolean,
    val hasLowerCase: Boolean,
) {
    val allMet: Boolean get() = longEnough && hasSpecialChar && hasUpperCase && hasLowerCase

    companion object {
        const val MIN_LENGTH = 8

        fun of(password: String) = PasswordRules(
            longEnough = password.length >= MIN_LENGTH,
            hasSpecialChar = password.any { !it.isLetterOrDigit() },
            hasUpperCase = password.any { it.isUpperCase() },
            hasLowerCase = password.any { it.isLowerCase() },
        )
    }
}

/** Giriş, kayıt, Google ile giriş, şifre sıfırlama ve çıkış. */
class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    private val _emailCheck = MutableStateFlow(EmailCheck.Empty)
    val emailCheck: StateFlow<EmailCheck> = _emailCheck.asStateFlow()

    val passwordRules: PasswordRules get() = PasswordRules.of(password)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _passwordResetState = MutableStateFlow<PasswordResetUiState>(PasswordResetUiState.Idle)
    val passwordResetState: StateFlow<PasswordResetUiState> = _passwordResetState.asStateFlow()

    private var emailJob: Job? = null

    fun onEmailChange(value: String) {
        email = value.trim()
        _passwordResetState.value = PasswordResetUiState.Idle
        emailJob?.cancel()
        if (email.isBlank()) {
            _emailCheck.value = EmailCheck.Empty
            return
        }
        emailJob = viewModelScope.launch {
            delay(500.milliseconds)
            _emailCheck.value =
                if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) EmailCheck.Valid else EmailCheck.Invalid
        }
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun checkAuthStatus() {
        val user = auth.currentUser
        if (user != null && AppSettings.pendingEulaUid == user.uid) {
            viewModelScope.launch {
                discardUnacceptedAccount()
                _uiState.value = AuthUiState.Unauthenticated
            }
            return
        }
        _uiState.value = if (user != null) AuthUiState.Success else AuthUiState.Unauthenticated
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun register() {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error(Txt.auth.fillAllFields)
            return
        }
        if (!passwordRules.longEnough) {
            _uiState.value = AuthUiState.Error(Txt.auth.passwordMinLength(PasswordRules.MIN_LENGTH))
            return
        }
        authenticate(
            failure = Txt.auth.registerFailed,
            noUser = Txt.auth.userNotCreated,
        ) { auth.createUserWithEmailAndPassword(email, password).await() }
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error(Txt.auth.enterEmailAndPassword)
            return
        }
        authenticate(
            failure = Txt.auth.loginFailed,
            noUser = Txt.auth.userNotFound,
        ) { auth.signInWithEmailAndPassword(email, password).await() }
    }

    /** Google ile giriş. [idToken] null ise pencere iptal edilmiş ya da başarısız olmuştur. */
    fun loginWithGoogle(idToken: String?, eulaAlreadyAccepted: Boolean = false) {
        if (idToken == null) {
            _uiState.value = AuthUiState.Error(Txt.auth.googleCancelledOrFailed)
            return
        }
        authenticate(
            failure = Txt.auth.googleFailedRetry,
            noUser = Txt.auth.googleFailed,
            eulaRequiredForNewUser = !eulaAlreadyAccepted,
        ) { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }
    }

    /** Giriş yollarının ortak akışı. */
    private fun authenticate(
        failure: String,
        noUser: String,
        eulaRequiredForNewUser: Boolean = false,
        call: suspend () -> AuthResult,
    ) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val result = call()
                val user = result.user
                _uiState.value = when {
                    user == null -> AuthUiState.Error(noUser)
                    eulaRequiredForNewUser &&
                        (result.additionalUserInfo?.isNewUser == true || AppSettings.pendingEulaUid == user.uid) -> {
                        AppSettings.pendingEulaUid = user.uid
                        AuthUiState.EulaRequired
                    }
                    else -> AuthUiState.Success
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.toAuthErrorMessage(failure))
            }
        }
    }

    /** Sözleşme onaylandı; yeni hesap kullanıma açılır. */
    fun acceptEula() {
        AppSettings.pendingEulaUid = null
        _uiState.value = AuthUiState.Success
    }

    /** Sözleşme reddedildi; yeni hesap silinir ve oturum kapanır. */
    fun declineEula(context: Context) {
        val appContext = context.applicationContext
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            discardUnacceptedAccount()
            clearGoogleCredentialState(appContext)
            _uiState.value = AuthUiState.Error(Txt.auth.eulaDeclined)
        }
    }

    /**
     * Sözleşmeyi onaylamamış hesabı siler. Silinemezse işaret kalır ve aynı hesapla girilince sözleşme yeniden sorulur.
     */
    private suspend fun discardUnacceptedAccount() {
        var deleted = true
        try {
            auth.currentUser?.delete()?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deleted = false
            e.printStackTrace()
        }
        auth.signOut()
        if (deleted) AppSettings.pendingEulaUid = null
    }

    /** Oturumu kapatır. Ekran geçişini [onDone] içinde yapın; yoksa temizlik yarım kalabilir. */
    fun logout(context: Context, onDone: () -> Unit) {
        val appContext = context.applicationContext
        auth.signOut()
        viewModelScope.launch {
            clearGoogleCredentialState(appContext)
            onDone()
        }
    }

    fun sendPasswordReset() {
        if (email.isBlank()) {
            _passwordResetState.value = PasswordResetUiState.Error(Txt.auth.enterEmailFirst)
            return
        }

        _passwordResetState.value = PasswordResetUiState.Loading
        viewModelScope.launch {
            val successMessage = Txt.auth.resetLinkSent
            try {
                auth.sendPasswordResetEmail(email).await()
                _passwordResetState.value = PasswordResetUiState.Success(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _passwordResetState.value =
                    if (e is FirebaseAuthException && e.errorCode == "ERROR_USER_NOT_FOUND") {
                        PasswordResetUiState.Success(successMessage)
                    } else {
                        PasswordResetUiState.Error(e.toAuthErrorMessage(Txt.auth.resetLinkFailed))
                    }
            }
        }
    }
}
