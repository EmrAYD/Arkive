package com.eayd.arkive

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.ai.AppVisibility
import com.eayd.arkive.ai.SummaryOpenRequest
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.navigation.ArkiveNavHost
import com.eayd.arkive.ui.theme.ArkiveTheme
import java.util.Locale

// enableEdgeToEdge varsayılanlarıyla aynı gezinme çubuğu perdeleri.
private val NAV_BAR_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAV_BAR_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {

    /** Bu Activity oluşturulurken uygulanan tema. Ayar bundan farklı bir değere geçerse Activity yeniden kurulur. */
    private var appliedThemeMode = ThemeMode.System

    /** Bu Activity oluşturulurken uygulanan dil. Ayar değişirse Activity yeniden kurulur. */
    private var appliedLanguage = AppLanguage.Turkish

    /**
     * Seçilen dili ve temayı kaynak (resource) yapılandırmasına işler. Dil: Android'in kendi metinleri (erişilebilirlik
     * açıklamaları, tarih seçiciler) ve tarih/sayı biçimleri uygulamanın diline uyar. Tema: logolar gibi `drawable-night`
     * ile hazırlanmış görseller telefonun değil UYGULAMANIN seçtiği temaya göre yüklenir; "Sistem varsayılanı" seçiliyse
     * gece modu alanına dokunulmaz ve telefonun ayarı izlenir.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        appliedThemeMode = AppSettings.storedThemeMode(newBase)
        appliedLanguage = AppSettings.storedAppLanguage(newBase)
        Locale.setDefault(appliedLanguage.locale)
        // Yalnızca dil ve (gerekirse) gece modu alanı dolu bir yapılandırma: gerisi (yön, ekran boyutu…) telefondan gelir.
        val override = Configuration().apply { setLocale(appliedLanguage.locale) }
        when (appliedThemeMode) {
            ThemeMode.System -> Unit
            ThemeMode.Light -> override.uiMode = Configuration.UI_MODE_NIGHT_NO
            ThemeMode.Dark -> override.uiMode = Configuration.UI_MODE_NIGHT_YES
        }
        applyOverrideConfiguration(override)
    }

    /** Uygulama zaten açıkken özetleme bildirimine dokunuldu. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSummaryFrom(intent)
    }

    /** Intent özetleme bildiriminden geliyorsa isteği kaydeder (gezinme [com.eayd.arkive.navigation.ArkiveNavHost]'ta). */
    private fun openSummaryFrom(intent: Intent?) {
        val request = SummaryOpenRequest.fromExtras(
            intent?.getStringExtra(SummaryOpenRequest.EXTRA_FILE_ID),
            intent?.getStringExtra(SummaryOpenRequest.EXTRA_FILE_NAME),
        ) ?: return
        // Bir kez işlenir: Activity yeniden kurulsa da aynı Intent ikinci kez özet açmasın.
        intent?.removeExtra(SummaryOpenRequest.EXTRA_FILE_ID)
        intent?.removeExtra(SummaryOpenRequest.EXTRA_FILE_NAME)
        AiSummarizer.requestOpenSummary(request)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.visible = true
    }

    override fun onStop() {
        AppVisibility.visible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this) // tema ve "yalnızca Wi-Fi" ayarları ekranlardan önce okunur
        // Özetleme bildirimine dokunularak açıldıysa o dosyanın özeti açılır. Yeniden kurulumda (tema değişimi, döndürme)
        // aynı Intent yeniden gelir; yalnızca ilk kurulumda işlenir.
        if (savedInstanceState == null) openSummaryFrom(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val appLanguage by AppSettings.appLanguage.collectAsState()
            // Tema değişince Activity yeniden kurulur ki kaynaklar (logolar) yeni temaya göre yüklensin.
            LaunchedEffect(themeMode) { if (themeMode != appliedThemeMode) recreate() }
            // Dil değişince de: bütün ekranlar metinlerini yeni dilde yeniden okur.
            LaunchedEffect(appLanguage) { if (appLanguage != appliedLanguage) recreate() }
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Seçilen tema telefonunkinden farklı olabilir: durum/gezinme çubuğu simgeleri temaya göre yeniden ayarlanır.
            DisposableEffect(darkTheme) {
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(NAV_BAR_LIGHT_SCRIM, NAV_BAR_DARK_SCRIM) { darkTheme },
                )
                onDispose { }
            }
            ArkiveTheme(darkTheme = darkTheme) {
                // Scaffold'un innerPadding'i kaldırıldı: her ekran arka planını sistem çubuklarının arkasına
                // kadar çizer, içeriğini safeDrawingPadding() ile çubukların dışında tutar.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ArkiveNavHost()
                }
            }
        }
    }
}
