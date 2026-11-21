package com.eayd.arkive.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Lang
import com.eayd.arkive.i18n.Txt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uygulama teması. [System]: telefonun açık/koyu tema ayarını izler (varsayılan). */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    /** Seçili dilde adı (dil değişince yeniden okunur). */
    val label: String
        get() = when (this) {
            System -> Txt.profile.themeSystem
            Light -> Txt.profile.themeLight
            Dark -> Txt.profile.themeDark
        }
}

/**
 * Bu cihaza özgü uygulama ayarları (tema, dil, yalnızca Wi-Fi ile aktarım, yapay zeka işlem yeri). Sunucuya gitmez;
 * SharedPreferences'ta saklanır, değerler StateFlow olarak dinlenir. [init] uygulama açılırken (MainActivity) bir kez çağrılır.
 */
object AppSettings {

    private const val PREFS_NAME = "arkive_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_PENDING_EULA_UID = "pending_eula_uid"
    private const val KEY_AI_MODE = "ai_mode"
    // Kaldırılan ayarlar (özet şablonu): eski kayıtları silmek için.
    private val OLD_KEYS = listOf("summary_template")
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_SUMMARY_LANGUAGE = "summary_language"

    private lateinit var prefs: SharedPreferences

    /** Uygulama bağlamı (bellek sızdırmaz): ağ durumunu sormak için kullanılır. */
    lateinit var appContext: Context
        private set

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** true: dosya yükleme/indirme yalnızca kablosuz ağdayken yapılır. Varsayılan kapalı. */
    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    /**
     * Uygulamanın dili. Kullanıcı seçmediyse telefonun dili (Türkçe telefonda Türkçe, diğerlerinde İngilizce). Dil
     * değişince Activity yeniden kurulur (bkz. MainActivity).
     */
    private val _appLanguage = MutableStateFlow(AppLanguage.Turkish)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    /**
     * Yapay zeka özetlemesinin nerede yapılacağı: yerel (telefon), bulut (sunucu) ya da ikisi (küçük dosyalar telefonda,
     * gerisi sunucuda). Varsayılan ikisi. Bulut Plus'a özeldir; ücretsiz planda ayar ne olursa olsun yerel geçerlidir
     * ([PlanPolicy.effectiveMode]). Kurallar için bkz. [com.eayd.arkive.ai.AiPolicy].
     */
    private val _aiMode = MutableStateFlow(AiMode.Hybrid)
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    /** Özetin yazılacağı dil. Kullanıcı ayrıca seçmediyse uygulamanın diliyle aynıdır. */
    private val _summaryLanguage = MutableStateFlow(AppLanguage.Turkish)
    val summaryLanguage: StateFlow<AppLanguage> = _summaryLanguage.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _appLanguage.value = storedAppLanguage(appContext)
        Lang.app = _appLanguage.value
        _summaryLanguage.value = AppLanguage.fromTag(prefs.getString(KEY_SUMMARY_LANGUAGE, null)) ?: _appLanguage.value
        _themeMode.value = storedThemeMode(appContext)
        _wifiOnly.value = prefs.getBoolean(KEY_WIFI_ONLY, false)
        _aiMode.value = AiMode.entries.firstOrNull { it.name == prefs.getString(KEY_AI_MODE, null) } ?: AiMode.Hybrid
        if (OLD_KEYS.any(prefs::contains)) prefs.edit().apply { OLD_KEYS.forEach(::remove) }.apply()
        AiSummarizer.start(appContext) // bekleyen telefon özetlerini yükler
    }

    /**
     * Kayıtlı temayı [init]'ten ÖNCE de okuyabilir: Activity, bağlamı kurulurken (attachBaseContext) temayı
     * kaynak yapılandırmasına işlemek için buna ihtiyaç duyar.
     */
    fun storedThemeMode(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    /**
     * Kayıtlı uygulama dilini [init]'ten ÖNCE de okuyabilir (Activity, bağlamı kurulurken dili kaynak yapılandırmasına
     * işler). Kayıt yoksa telefonun dili kullanılır; telefonun dili, uygulamanın kendi ayarından etkilenmeyen sistem
     * yapılandırmasından okunur. Okunan dil [Lang.app]'e de yazılır.
     */
    fun storedAppLanguage(context: Context): AppLanguage {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_APP_LANGUAGE, null)
        val language = AppLanguage.fromTag(saved) ?: AppLanguage.fromSystem(Resources.getSystem().configuration.locales[0])
        Lang.app = language
        return language
    }

    /** Uygulamanın dilini değiştirir. Özet dili ayrıca seçilmemişse o da bu dile geçer. */
    fun setAppLanguage(language: AppLanguage) {
        val summaryFollowsApp = prefs.getString(KEY_SUMMARY_LANGUAGE, null) == null
        _appLanguage.value = language
        Lang.app = language
        prefs.edit().putString(KEY_APP_LANGUAGE, language.tag).apply()
        if (summaryFollowsApp) _summaryLanguage.value = language
    }

    fun setSummaryLanguage(language: AppLanguage) {
        _summaryLanguage.value = language
        prefs.edit().putString(KEY_SUMMARY_LANGUAGE, language.tag).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    /**
     * Google ile yeni açılan ama Kullanıcı Sözleşmesi'ni henüz onaylamamış hesabın kimliği (yoksa null).
     * Sözleşme penceresi açıkken uygulama kapanırsa, bir sonraki açılışta bu hesap onaysız sayılıp silinir;
     * silme başarısız olursa işaret kalır ve aynı hesapla tekrar girilince sözleşme yine sorulur.
     */
    var pendingEulaUid: String?
        get() = prefs.getString(KEY_PENDING_EULA_UID, null)
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_PENDING_EULA_UID) else putString(KEY_PENDING_EULA_UID, value) }.commit()
        }

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun setAiMode(mode: AiMode) {
        _aiMode.value = mode
        prefs.edit().putString(KEY_AI_MODE, mode.name).apply()
    }
}
