package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.ai.LocalModel
import com.eayd.arkive.ai.LocalModelState
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.PlanPolicy
import com.eayd.arkive.data.PlanStatus
import com.eayd.arkive.data.PlanSwitcher
import com.eayd.arkive.data.PlusOffer
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Txt
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
    /** Şifre değiştirme. */
    data object ChangePassword : ProfileDialog

    /** Çöp kutusu saklama süresi. */
    data object TrashRetention : ProfileDialog

    /** Tema seçimi. */
    data object Theme : ProfileDialog

    /** Uygulamanın ve özetin dili. */
    data object Language : ProfileDialog

    /** Yapay zeka işlem yeri. */
    data object AiProcessing : ProfileDialog

    /** Telefondaki yapay zeka modeli. */
    data object LocalAi : ProfileDialog
}

data class ProfileUiState(
    val email: String?,
    /** E-posta ve şifreyle açılmış hesap mı; Google hesaplarının şifresi burada değiştirilemez. */
    val hasPassword: Boolean,
    val isLoading: Boolean = true,
    /** Plan ve sınırlar; yüklenene kadar null. */
    val storage: StorageInfo? = null,
    val trashRetentionDays: Int = 30,
    /** Bilgiler hiç yüklenemedi. */
    val error: String? = null,
    /** Kısa bildirim. */
    val notice: String? = null,
)

/** Şifre değiştirirken çıkan Firebase hatalarını kullanıcıya gösterilecek mesaja çevirir. */
private fun Throwable.toPasswordChangeMessage(): String = when {
    this is FirebaseAuthInvalidCredentialsException -> Txt.profile.currentPasswordWrong
    this is FirebaseAuthException && (errorCode == "ERROR_WRONG_PASSWORD" || errorCode == "ERROR_INVALID_CREDENTIAL") ->
        Txt.profile.currentPasswordWrong
    this is FirebaseAuthRecentLoginRequiredException ->
        Txt.profile.reloginRequired
    else -> toAuthErrorMessage(Txt.profile.passwordChangeFailed)
}

/** Profil ekranının durumu: hesap, plan, ayarlar ve şifre değiştirme. */
class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(
        auth.currentUser.let { user ->
            ProfileUiState(
                email = user?.email,
                hasPassword = user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true,
            )
        }
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    val dialogs = DialogHost<ProfileDialog>(viewModelScope)

    /** Bu cihaza ait ayarlar. */
    val themeMode: StateFlow<ThemeMode> = AppSettings.themeMode
    val wifiOnly: StateFlow<Boolean> = AppSettings.wifiOnly

    /** Kayıtlı yapay zeka işlem yeri. */
    val aiMode: StateFlow<AiMode> = AppSettings.aiMode

    /** Telefondaki modelin durumu. */
    val localModel: StateFlow<LocalModelState> = LocalModel.state
    val appLanguage: StateFlow<AppLanguage> = AppSettings.appLanguage
    val summaryLanguage: StateFlow<AppLanguage> = AppSettings.summaryLanguage

    /** Dilleri kaydeder. Özet dili önce yazılır; uygulamanın dili değişince Activity yeniden kurulur. */
    fun saveLanguages(app: AppLanguage, summary: AppLanguage) {
        dialogs.close()
        AppSettings.setSummaryLanguage(summary)
        AppSettings.setAppLanguage(app)
    }

    fun setThemeMode(mode: ThemeMode) {
        AppSettings.setThemeMode(mode)
        dialogs.close()
    }

    fun setWifiOnly(enabled: Boolean) = AppSettings.setWifiOnly(enabled)

    /** İşlem yerini kaydeder. Ücretsiz planda bulut seçilirse ayar değişmez, Plus tanıtılır. */
    fun setAiMode(mode: AiMode) {
        dialogs.close()
        if (mode.allowsCloud && PlanStatus.quota.value?.cloud == false) {
            PlusOffer.show(PlanPolicy.CLOUD_MODE_MESSAGE)
            return
        }
        AppSettings.setAiMode(mode)
    }

    /** Planı sunucudan alır; bilgi zaten ekrandaysa sessizce tazeler. */
    fun load() {
        _state.update { if (it.storage == null) it.copy(isLoading = true, error = null) else it }
        launchCatching(
            onError = { message ->
                _state.update {
                    if (it.storage == null) it.copy(isLoading = false, error = message)
                    else it.copy(isLoading = false, notice = message)
                }
            },
        ) {
            val me = ApiClient.api.getMe()
            PlanStatus.update(me.summaryQuota())
            _state.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    storage = me.toStorageInfo(),
                    trashRetentionDays = me.trashRetentionDays,
                )
            }
        }
    }

    /** Deneme için ücretsiz plana döner. */
    fun switchToFree() {
        launchCatching(onError = { message -> _state.update { it.copy(notice = message) } }) {
            val me = PlanSwitcher.switchTo("Free")
            _state.update { it.copy(storage = me.toStorageInfo(), notice = Txt.profile.switchedToFree) }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun showNotice(message: String) = _state.update { it.copy(notice = message) }

    /** Modeli indirmeye başlar; başlayamazsa nedeni bildirilir. */
    fun downloadLocalModel() = showNotice(LocalModel.startDownload() ?: Txt.localAi.downloadStarted)

    fun cancelLocalModelDownload() = LocalModel.cancelDownload()

    fun deleteLocalModel() {
        LocalModel.delete()
        dialogs.close()
        showNotice(Txt.localAi.deleted)
    }

    fun openDialog(dialog: ProfileDialog) = dialogs.open(dialog)

    /** Şifreyi değiştirir. Firebase önce mevcut şifreyle yeniden doğrulama ister. */
    fun changePassword(current: String, new: String) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            dialogs.close()
            _state.update { it.copy(notice = Txt.profile.passwordChangeUnavailable) }
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
            _state.update { it.copy(notice = Txt.profile.passwordChanged) }
        }
    }

    /** Çöp kutusunda saklama süresini değiştirir (1-365 gün). */
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
                    notice = Txt.profile.retentionSaved(me.trashRetentionDays),
                )
            }
        }
    }
}
