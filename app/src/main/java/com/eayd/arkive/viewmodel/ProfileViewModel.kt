package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.ai.SummaryTemplate
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
    /** "Şifreyi değiştir": mevcut şifre, yeni şifre ve tekrarı. */
    data object ChangePassword : ProfileDialog

    /** Çöp kutusunda saklama süresi seçimi. */
    data object TrashRetention : ProfileDialog

    /** Tema seçimi (sistem / açık / koyu). */
    data object Theme : ProfileDialog

    /** Yapay zeka işlem yeri seçimi (yerel / bulut / yerel + bulut). */
    data object AiProcessing : ProfileDialog

    /** Özet PDF'inin şablon seçimi. */
    data object SummaryTemplateChoice : ProfileDialog

    /** Dil: uygulamanın dili ve özetin dili (bulut özetinin ve özet başlıklarının dili) aynı pencerede seçilir. */
    data object Language : ProfileDialog
}

data class ProfileUiState(
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
    this is FirebaseAuthInvalidCredentialsException -> Txt.profile.currentPasswordWrong
    this is FirebaseAuthException && (errorCode == "ERROR_WRONG_PASSWORD" || errorCode == "ERROR_INVALID_CREDENTIAL") ->
        Txt.profile.currentPasswordWrong
    this is FirebaseAuthRecentLoginRequiredException ->
        Txt.profile.reloginRequired
    else -> toAuthErrorMessage(Txt.profile.passwordChangeFailed)
}

/**
 * Profil ekranının durumu: oturumdaki kullanıcının e-postası (Firebase), planı ve sınırları (GET /me),
 * çöp kutusu saklama süresi ayarı (PATCH /me) ve şifre değiştirme (Firebase).
 */
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

    /** Bu cihaza özgü ayarlar ([AppSettings]); sunucuya gitmez. */
    val themeMode: StateFlow<ThemeMode> = AppSettings.themeMode
    val wifiOnly: StateFlow<Boolean> = AppSettings.wifiOnly
    val aiMode: StateFlow<AiMode> = AppSettings.aiMode
    val summaryTemplate: StateFlow<SummaryTemplate> = AppSettings.summaryTemplate
    val appLanguage: StateFlow<AppLanguage> = AppSettings.appLanguage
    val summaryLanguage: StateFlow<AppLanguage> = AppSettings.summaryLanguage

    /**
     * Dil penceresinde seçilenleri kaydeder. Özet dili önce yazılır: uygulamanın dili değişince Activity yeniden kurulur
     * ve bütün ekranlar yeni dilde açılır.
     */
    fun saveLanguages(app: AppLanguage, summary: AppLanguage) {
        dialogs.close()
        AppSettings.setSummaryLanguage(summary)
        AppSettings.setAppLanguage(app)
    }

    fun setSummaryTemplate(template: SummaryTemplate) {
        AppSettings.setSummaryTemplate(template)
        dialogs.close()
    }

    fun setAiMode(mode: AiMode) {
        dialogs.close()
        // Bulut özeti Plus'a özel: ücretsiz planda "Bulut" ve "Yerel + Bulut" seçilince ayar değişmez, Plus tanıtılır.
        if (mode.allowsCloud && PlanStatus.quota.value?.cloud == false) {
            PlusOffer.show(PlanPolicy.CLOUD_MODE_MESSAGE)
            return
        }
        AppSettings.setAiMode(mode)
    }

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

    /** DENEME İÇİN: ücretsiz plana döner (ödeme sistemi yokken Plus'ı denedikten sonra geri dönmek için). */
    fun switchToFree() {
        launchCatching(onError = { message -> _state.update { it.copy(notice = message) } }) {
            val me = PlanSwitcher.switchTo("Free")
            _state.update { it.copy(storage = me.toStorageInfo(), notice = Txt.profile.switchedToFree) }
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
                    notice = Txt.profile.retentionSaved(me.trashRetentionDays),
                )
            }
        }
    }
}
