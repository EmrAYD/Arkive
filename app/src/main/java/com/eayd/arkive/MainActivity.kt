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

private val NAV_BAR_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAV_BAR_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {

    /** Bu ekran açılırken kullanılan tema; ayar değişince Activity yeniden kurulur. */
    private var appliedThemeMode = ThemeMode.System

    /** Bu ekran açılırken kullanılan dil; ayar değişince Activity yeniden kurulur. */
    private var appliedLanguage = AppLanguage.Turkish

    /** Seçilen dili ve temayı Android kaynaklarına uygular. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        appliedThemeMode = AppSettings.storedThemeMode(newBase)
        appliedLanguage = AppSettings.storedAppLanguage(newBase)
        Locale.setDefault(appliedLanguage.locale)
        val override = Configuration().apply { setLocale(appliedLanguage.locale) }
        when (appliedThemeMode) {
            ThemeMode.System -> Unit
            ThemeMode.Light -> override.uiMode = Configuration.UI_MODE_NIGHT_NO
            ThemeMode.Dark -> override.uiMode = Configuration.UI_MODE_NIGHT_YES
        }
        applyOverrideConfiguration(override)
    }

    /** Uygulama açıkken bildirime dokunulunca çağrılır. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSummaryFrom(intent)
    }

    /** Intent bir özet bildiriminden geldiyse özeti açma isteğini kaydeder. */
    private fun openSummaryFrom(intent: Intent?) {
        val request = SummaryOpenRequest.fromExtras(
            intent?.getStringExtra(SummaryOpenRequest.EXTRA_FILE_ID),
            intent?.getStringExtra(SummaryOpenRequest.EXTRA_FILE_NAME),
        ) ?: return
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
        AppSettings.init(this)
        if (savedInstanceState == null) openSummaryFrom(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val appLanguage by AppSettings.appLanguage.collectAsState()
            LaunchedEffect(themeMode) { if (themeMode != appliedThemeMode) recreate() }
            LaunchedEffect(appLanguage) { if (appLanguage != appliedLanguage) recreate() }
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            DisposableEffect(darkTheme) {
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(NAV_BAR_LIGHT_SCRIM, NAV_BAR_DARK_SCRIM) { darkTheme },
                )
                onDispose { }
            }
            ArkiveTheme(darkTheme = darkTheme) {
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
