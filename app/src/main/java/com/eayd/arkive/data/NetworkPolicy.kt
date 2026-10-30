package com.eayd.arkive.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/** "Yalnızca Wi-Fi" açıkken kablosuz ağda olunmadığını anlatır. Mesajı doğrudan kullanıcıya gösterilir. */
class WifiRequiredException :
    IOException("Yalnızca Wi-Fi ile aktarım açık. Dosya yüklemek/indirmek için kablosuz ağa bağlanın.")

/**
 * Dosya aktarımı (yükleme, indirme, ön izleme) başlamadan önce çağrılır. Ayar açıksa ve cihaz Wi-Fi (ya da
 * ethernet) üzerinde değilse [WifiRequiredException] fırlatır; ayar kapalıysa hiçbir şey yapmaz.
 */
object NetworkPolicy {

    /** Aktarım şu an yapılabilir mi? (Ayar kapalıysa ya da Wi-Fi/ethernet varsa evet.) */
    fun isAllowed(): Boolean = !AppSettings.wifiOnly.value || isOnWifi()

    fun requireAllowed() {
        if (!isAllowed()) throw WifiRequiredException()
    }

    private fun isOnWifi(): Boolean {
        val manager = AppSettings.appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        return try {
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: SecurityException) {
            // AndroidManifest'te ACCESS_NETWORK_STATE izni yoksa ağ türü okunamaz; kullanıcıyı kilitlememek için izin verilir.
            true
        }
    }
}
