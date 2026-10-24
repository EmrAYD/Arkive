package com.eayd.arkive.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * [url]'i tarayıcıda / indirme yöneticisinde açar (imzalı indirme adresleri için). Adresi açacak bir uygulama
 * yoksa false döner.
 */
fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/**
 * Uygulamanın sistem bildirim ayarları sayfasını açar (Android 8+; daha eskide uygulama ayrıntıları sayfası).
 * Açacak ekran yoksa false döner.
 */
fun Context.openNotificationSettings(): Boolean = try {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}
