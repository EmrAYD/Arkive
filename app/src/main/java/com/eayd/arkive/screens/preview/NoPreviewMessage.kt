package com.eayd.arkive.screens.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.utils.formatBytes

/** Ön izleme yapılamayan dosyanın ortada gösterilen kartı: simge, ad, boyut, [reason] ve düğmeler ([actions]). */
@Composable
private fun FileMessage(file: FileDto, icon: ImageVector, reason: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(file.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(reason, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

@Composable
internal fun NoPreviewMessage(file: FileDto, reason: String, onOpenExternally: () -> Unit) {
    FileMessage(file, Icons.Filled.Description, reason) {
        Button(onClick = onOpenExternally) { Text("İndir") }
    }
}

/** Parolalı dosya: parola penceresi kapatıldıysa buradan yeniden açılır. */
@Composable
internal fun PasswordProtectedMessage(file: FileDto, onEnterPassword: () -> Unit, onDownload: () -> Unit) {
    FileMessage(file, Icons.Filled.Lock, "Bu dosya parola ile korunuyor.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onEnterPassword) { Text("Parola gir") }
            OutlinedButton(onClick = onDownload) { Text("İndir") }
        }
    }
}
