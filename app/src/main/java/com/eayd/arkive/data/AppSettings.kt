package com.eayd.arkive.data

import android.content.Context
import android.content.SharedPreferences
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.ai.SummaryTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uygulama teması. [System]: telefonun açık/koyu tema ayarını izler (varsayılan). */
enum class ThemeMode(val label: String) {
    System("Sistem varsayılanı"),
    Light("Açık"),
    Dark("Koyu"),
}

/**
 * Bu cihaza özgü uygulama ayarları (tema, yalnızca Wi-Fi ile aktarım, yapay zeka işlem yeri). Sunucuya gitmez; SharedPreferences'ta
 * saklanır, değerler StateFlow olarak dinlenir. [init] uygulama açılırken (MainActivity) bir kez çağrılır.
 */
object AppSettings {

    private const val PREFS_NAME = "arkive_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_PENDING_EULA_UID = "pending_eula_uid"
    private const val KEY_AI_MODE = "ai_mode"
    private const val KEY_SUMMARY_TEMPLATE = "summary_template"

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
     * Yapay zeka özetlemesinin nerede yapılacağı: yerel (telefon), bulut (sunucu) ya da ikisi (uygun olan yerel,
     * olmayan bulut). Varsayılan ikisi. Kurallar için bkz. [com.eayd.arkive.ai.AiPolicy].
     */
    private val _aiMode = MutableStateFlow(AiMode.Hybrid)
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    /** Yeni üretilen özet PDF'inin şablonu; sunucuya her özet isteğiyle birlikte gider. Varsayılan klasik. */
    private val _summaryTemplate = MutableStateFlow(SummaryTemplate.Default)
    val summaryTemplate: StateFlow<SummaryTemplate> = _summaryTemplate.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeMode.value = storedThemeMode(appContext)
        _wifiOnly.value = prefs.getBoolean(KEY_WIFI_ONLY, false)
        _aiMode.value = AiMode.entries.firstOrNull { it.name == prefs.getString(KEY_AI_MODE, null) } ?: AiMode.Hybrid
        _summaryTemplate.value = SummaryTemplate.fromId(prefs.getString(KEY_SUMMARY_TEMPLATE, null))
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

    fun setAiMode(mode: AiMode) {
        _aiMode.value = mode
        prefs.edit().putString(KEY_AI_MODE, mode.name).apply()
    }

    fun setSummaryTemplate(template: SummaryTemplate) {
        _summaryTemplate.value = template
        prefs.edit().putString(KEY_SUMMARY_TEMPLATE, template.id).apply()
    }

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }
}
