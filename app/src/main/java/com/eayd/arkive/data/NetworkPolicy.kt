package com.eayd.arkive.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.eayd.arkive.i18n.Txt
import java.io.IOException

/** "Yalnızca Wi-Fi" açıkken Wi-Fi'da olunmadığını söyler. */
class WifiRequiredException :
    IOException(Txt.errors.wifiOnly)

/** Dosya aktarımından önce çağrılır; "Yalnızca Wi-Fi" açıksa ve Wi-Fi yoksa [WifiRequiredException] atar. */
object NetworkPolicy {

    /** Aktarım şu an yapılabilir mi. */
    fun isAllowed(): Boolean = !AppSettings.wifiOnly.value || isOnWifi()

    fun requireAllowed() {
        if (!isAllowed()) throw WifiRequiredException()
    }

    /** Wi-Fi ya da kablolu ağda mı. Manifest'te ACCESS_NETWORK_STATE izni yoksa engellememek için true döner. */
    private fun isOnWifi(): Boolean {
        val manager = AppSettings.appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        return try {
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: SecurityException) {
            true
        }
    }
}
