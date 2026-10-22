package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.utils.resolvedName
import com.eayd.arkive.utils.toAuthErrorMessage
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/** Profil ekranından açılan pencereler. */
sealed interface ProfileDialog {
    /** "Şifreyi değiştir": mevcut şifre, yeni şifre ve tekrarı. */
    data object ChangePassword : ProfileDialog

    /** Çöp kutusunda saklama süresi seçimi. */
    data object TrashRetention : ProfileDialog

    /** Tema seçimi (sistem / açık / koyu). */
    data object Theme : ProfileDialog
}

data class ProfileUiState(
    val name: String,
    val email: String?,
    /** E-posta + şifreyle açılmış hesap. Google ile giriş yapanların şifresi Google'dadır; burada değiştirilemez. */
    val hasPassword: Boolean,
    val isLoading: Boolean = true,
    /** Plan adı, toplam kota ve dosya başına sınır (GET /me). Yüklenene kadar null. */
    val storage: StorageInfo? = null,
    val trashRetentionDays: Int = 30,
    /** Bilgiler hiç yüklenemediyse ekranın ortasında "Tekrar dene" ile gösterilir. */
    val error: String? = null,
    /** Kısa bildirim (Snackbar). */
    val notice: String? = null,
)

/** Şifre değiştirirken Firebase hatalarını kullanıcıya gösterilecek Türkçe mesaja çevirir. */
private fun Throwable.toPasswordChangeMessage(): String = when {
    this is FirebaseAuthInvalidCredentialsException -> "Mevcut şifre hatalı."
    this is FirebaseAuthException && (errorCode == "ERROR_WRONG_PASSWORD" || errorCode == "ERROR_INVALID_CREDENTIAL") ->
        "Mevcut şifre hatalı."
    this is FirebaseAuthRecentLoginRequiredException ->
        "Güvenlik nedeniyle çıkış yapıp yeniden giriş yapmanız gerekiyor."
    else -> toAuthErrorMessage("Şifre değiştirilemedi. Lütfen tekrar deneyin.")
}

/**
 * Profil ekranının durumu: oturumdaki kullanıcının adı/e-postası (Firebase), planı ve sınırları (GET /me),
 * çöp kutusu saklama süresi ayarı (PATCH /me) ve şifre değiştirme (Firebase).
 */
class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(
        auth.currentUser.let { user ->
            ProfileUiState(
                name = user?.resolvedName() ?: "Kullanıcı",
                email = user?.email,
                hasPassword = user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true,
            )
        }
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    val dialogs = DialogHost<ProfileDialog>(viewModelScope)

    /** Bu cihaza özgü ayarlar ([AppSettings]); sunucuya gitmez. */
    val themeMode: StateFlow<ThemeMode> = AppSettings.themeMode
    val wifiOnly: StateFlow<Boolean> = AppSettings.wifiOnly

    fun setThemeMode(mode: ThemeMode) {
        AppSettings.setThemeMode(mode)
        dialogs.close()
    }

    fun setWifiOnly(enabled: Boolean) = AppSettings.setWifiOnly(enabled)

    /**
     * Plan ve sınırları sunucudan alır. Sayfa her açıldığında çağrılır (ekran çağırır); bilgi zaten ekrandaysa
     * yükleniyor göstermeden sessizce tazelenir.
     */
    fun load() {
        _state.update { if (it.storage == null) it.copy(isLoading = true, error = null) else it }
        launchCatching(
            onError = { message ->
                _state.update {
                    // Eski bilgi ekrandaysa onu bozmadan kısa bildirim göster; hiç yoksa tam ekran hata.
                    if (it.storage == null) it.copy(isLoading = false, error = message)
                    else it.copy(isLoading = false, notice = message)
                }
            },
        ) {
            val me = ApiClient.api.getMe()
            _state.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    storage = StorageInfo(me.planName, me.usedStorage, me.storageLimit, me.maxFileSize),
                    trashRetentionDays = me.trashRetentionDays,
                )
            }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun showNotice(message: String) = _state.update { it.copy(notice = message) }

    fun openDialog(dialog: ProfileDialog) = dialogs.open(dialog)

    /**
     * Şifreyi değiştirir: önce [current] ile kimlik yeniden doğrulanır (Firebase hassas işlemlerde bunu ister), sonra
     * şifre [new] yapılır. Hata olursa pencerede Türkçe mesajla gösterilir; başarıda pencere kapanır.
     */
    fun changePassword(current: String, new: String) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            dialogs.close()
            _state.update { it.copy(notice = "Bu hesapta şifre değiştirilemez.") }
            return
        }
        dialogs.submit {
            try {
                user.reauthenticate(EmailAuthProvider.getCredential(email, current)).await()
                user.updatePassword(new).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw Exception(e.toPasswordChangeMessage())
            }
            _state.update { it.copy(notice = "Şifreniz değiştirildi") }
        }
    }

    /** Çöp kutusunda saklama süresini değiştirir; sunucu 1-365 gün arasını kabul eder. */
    fun saveTrashRetention(days: Int) {
        if (days == _state.value.trashRetentionDays) {
            dialogs.close()
            return
        }
        dialogs.submit {
            val me = ApiClient.api.updateMe(mapOf("trash_retention_days" to days))
            _state.update {
                it.copy(
                    trashRetentionDays = me.trashRetentionDays,
                    notice = "Çöp kutusu saklama süresi ${me.trashRetentionDays} gün olarak kaydedildi",
                )
            }
        }
    }
}
