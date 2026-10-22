package com.eayd.arkive

import android.content.Context
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
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.navigation.ArkiveNavHost
import com.eayd.arkive.ui.theme.ArkiveTheme

// enableEdgeToEdge varsayılanlarıyla aynı gezinme çubuğu perdeleri.
private val NAV_BAR_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAV_BAR_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {

    /** Bu Activity oluşturulurken uygulanan tema. Ayar bundan farklı bir değere geçerse Activity yeniden kurulur. */
    private var appliedThemeMode = ThemeMode.System

    /**
     * Seçilen temayı kaynak (resource) yapılandırmasına işler. Böylece logolar gibi `drawable-night` ile hazırlanmış
     * görseller telefonun değil UYGULAMANIN seçtiği temaya göre yüklenir. "Sistem varsayılanı" seçiliyse hiçbir şey
     * değiştirilmez ve telefonun ayarı izlenir.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        appliedThemeMode = AppSettings.storedThemeMode(newBase)
        val nightMode = when (appliedThemeMode) {
            ThemeMode.System -> return
            ThemeMode.Light -> Configuration.UI_MODE_NIGHT_NO
            ThemeMode.Dark -> Configuration.UI_MODE_NIGHT_YES
        }
        // Yalnızca "gece modu" alanı dolu bir yapılandırma: gerisi (yön, dil, ekran boyutu…) telefondan gelmeye devam eder.
        applyOverrideConfiguration(Configuration().apply { uiMode = nightMode })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this) // tema ve "yalnızca Wi-Fi" ayarları ekranlardan önce okunur
        enableEdgeToEdge()
        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            // Tema değişince Activity yeniden kurulur ki kaynaklar (logolar) yeni temaya göre yüklensin.
            LaunchedEffect(themeMode) { if (themeMode != appliedThemeMode) recreate() }
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
