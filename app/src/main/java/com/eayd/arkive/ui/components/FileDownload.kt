package com.eayd.arkive.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.eayd.arkive.viewmodel.saveFileToDevice

private class PendingDownload(val fileId: String, val name: String, val mimeType: String?)

/**
 * "İndir" için kullanılacak işlevi döndürür: dosyayı uygulama içinden İndirilenler/Arkive klasörüne kaydeder.
 * Android 9 ve eskide önce depolama iznini ister (verilmişse sormaz); izin reddedilirse [notify] ile bildirir.
 * Android 10+ için izin gerekmez, doğrudan indirir. [notify]: kısa bildirim (Snackbar) göstericisi.
 */
@Composable
fun rememberFileDownloader(notify: (String) -> Unit): (fileId: String, name: String, mimeType: String?) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingDownload?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pending
        pending = null
        if (request != null) {
            if (granted) saveFileToDevice(request.fileId, request.name, request.mimeType, notify)
            else notify("Dosyayı indirmek için depolama izni gerekli.")
        }
    }

    return { fileId, name, mimeType ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = PendingDownload(fileId, name, mimeType)
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveFileToDevice(fileId, name, mimeType, notify)
        }
    }
}
