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
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.viewmodel.saveFileToDevice

private class PendingDownload(val fileId: String, val name: String, val mimeType: String?)

/** Dosyayı İndirilenler/Arkive klasörüne kaydeden işlevi döner. Android 9 ve öncesinde önce depolama izni ister. */
@Composable
fun rememberFileDownloader(notify: (String) -> Unit): (fileId: String, name: String, mimeType: String?) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingDownload?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pending
        pending = null
        if (request != null) {
            if (granted) saveFileToDevice(request.fileId, request.name, request.mimeType, notify)
            else notify(Txt.errors.storagePermissionNeeded)
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
