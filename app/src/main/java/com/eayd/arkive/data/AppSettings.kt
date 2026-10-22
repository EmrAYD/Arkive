package com.eayd.arkive.data

import android.content.Context
import android.content.SharedPreferences
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
 * Bu cihaza özgü uygulama ayarları (tema, yalnızca Wi-Fi ile aktarım). Sunucuya gitmez; SharedPreferences'ta
 * saklanır, değerler StateFlow olarak dinlenir. [init] uygulama açılırken (MainActivity) bir kez çağrılır.
 */
object AppSettings {

    private const val PREFS_NAME = "arkive_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_PENDING_EULA_UID = "pending_eula_uid"

    private lateinit var prefs: SharedPreferences

    /** Uygulama bağlamı (bellek sızdırmaz): ağ durumunu sormak için kullanılır. */
    lateinit var appContext: Context
        private set

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** true: dosya yükleme/indirme yalnızca kablosuz ağdayken yapılır. Varsayılan kapalı. */
    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeMode.value = storedThemeMode(appContext)
        _wifiOnly.value = prefs.getBoolean(KEY_WIFI_ONLY, false)
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

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }
}
