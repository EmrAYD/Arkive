package com.eayd.arkive.viewmodel

import android.content.Context
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    data class Error(val message: String) : AuthUiState
}

sealed interface PasswordResetUiState {
    data object Idle : PasswordResetUiState
    data object Loading : PasswordResetUiState
    data class Success(val message: String) : PasswordResetUiState
    data class Error(val message: String) : PasswordResetUiState
}

/** E-posta alanının denetim sonucu. Kullanıcı yazmayı bıraktıktan yarım saniye sonra hesaplanır. */
enum class EmailCheck { Empty, Valid, Invalid }

/** Kayıt şifresinin kuralları; ekran her kuralı ayrı ayrı gösterir. */
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

/**
 * Giriş, kayıt, Google ile giriş, şifre sıfırlama ve çıkış. Giriş ve kayıt ekranları aynı ViewModel sınıfını
 * kullanır; e-posta/şifre alanlarının metni ve doğrulaması burada tutulur, ekran yalnızca çizer.
 */
class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    // Alan metinleri Compose durumu olarak tutulur: yazarken imleç zıplamaz.
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    private val _emailCheck = MutableStateFlow(EmailCheck.Empty)
    val emailCheck: StateFlow<EmailCheck> = _emailCheck.asStateFlow()

    val passwordRules: PasswordRules get() = PasswordRules.of(password)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Şifre sıfırlama kendi state'ini taşır; giriş/kayıt state'iyle karışmaz.
    private val _passwordResetState = MutableStateFlow<PasswordResetUiState>(PasswordResetUiState.Idle)
    val passwordResetState: StateFlow<PasswordResetUiState> = _passwordResetState.asStateFlow()

    private var emailJob: Job? = null

    // --- Form ---------------------------------------------------------------------------------------

    fun onEmailChange(value: String) {
        email = value.trim()
        _passwordResetState.value = PasswordResetUiState.Idle
        emailJob?.cancel()
        if (email.isBlank()) {
            _emailCheck.value = EmailCheck.Empty
            return
        }
        // Debounce: kullanıcı yazarken hata göstermemek için yarım saniye beklenir.
        emailJob = viewModelScope.launch {
            delay(500.milliseconds)
            _emailCheck.value =
                if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) EmailCheck.Valid else EmailCheck.Invalid
        }
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    // --- Oturum -------------------------------------------------------------------------------------

    fun checkAuthStatus() {
        _uiState.value = if (auth.currentUser != null) AuthUiState.Success else AuthUiState.Unauthenticated
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun register() {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Lütfen tüm alanları doldurun")
            return
        }
        if (!passwordRules.longEnough) {
            _uiState.value = AuthUiState.Error("Şifre en az ${PasswordRules.MIN_LENGTH} karakter olmalı")
            return
        }
        authenticate(
            failure = "Kayıt başarısız. Lütfen tekrar deneyin.",
            noUser = "Kullanıcı oluşturulamadı",
        ) { auth.createUserWithEmailAndPassword(email, password).await() }
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Lütfen e-posta ve şifrenizi girin")
            return
        }
        authenticate(
            failure = "Giriş başarısız. Lütfen tekrar deneyin.",
            noUser = "Kullanıcı bulunamadı",
        ) { auth.signInWithEmailAndPassword(email, password).await() }
    }

    /** [idToken] null ise Google penceresi iptal edilmiş ya da başarısız olmuş demektir. */
    fun loginWithGoogle(idToken: String?) {
        if (idToken == null) {
            _uiState.value = AuthUiState.Error("Google ile giriş işlemi iptal edildi veya başarısız oldu.")
            return
        }
        authenticate(
            failure = "Google girişi başarısız. Lütfen tekrar deneyin.",
            noUser = "Google girişi başarısız oldu",
        ) { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }
    }

    /** Üç giriş yolunun ortak akışı: yükleniyor → sonuç (başarı ya da Türkçe hata mesajı). */
    private fun authenticate(failure: String, noUser: String, call: suspend () -> AuthResult) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = if (call().user == null) AuthUiState.Error(noUser) else AuthUiState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.toAuthErrorMessage(failure))
            }
        }
    }

    /**
     * Firebase oturumunu kapatır ve Credential Manager'daki Google durumunu temizler.
     * Navigasyon, temizlik bittikten sonra [onDone] ile yapılmalı: navigasyon bu
     * ViewModel'in ekranını back stack'ten attığı için ViewModel iptal olur ve
     * devam eden temizlik yarım kalabilir.
     */
    fun logout(context: Context, onDone: () -> Unit) {
        val appContext = context.applicationContext
        auth.signOut()
        viewModelScope.launch {
            clearGoogleCredentialState(appContext)
            onDone()
        }
    }

    // --- Şifre sıfırlama ----------------------------------------------------------------------------

    fun sendPasswordReset() {
        if (email.isBlank()) {
            _passwordResetState.value = PasswordResetUiState.Error("Önce e-posta adresinizi girin")
            return
        }

        _passwordResetState.value = PasswordResetUiState.Loading
        viewModelScope.launch {
            // E-posta kayıtlı olmasa bile aynı mesajı gösteriyoruz; böylece bir
            // e-postanın sistemde olup olmadığı dışarıdan öğrenilemez.
            val successMessage = "E-posta kayıtlıysa şifre sıfırlama bağlantısı gönderildi"
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
                        PasswordResetUiState.Error(e.toAuthErrorMessage("Bağlantı gönderilemedi"))
                    }
            }
        }
    }
}
