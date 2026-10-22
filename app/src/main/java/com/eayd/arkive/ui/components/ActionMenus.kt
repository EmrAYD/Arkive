package com.eayd.arkive.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bir dosyanın 3 nokta menüsü. Dosya listesinde ve ön izleme ekranında aynı menü kullanılır:
 * İndir, Taşı, Yeniden adlandır, Bilgi, Sil (kırmızı).
 */
@Composable
fun FileActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("İndir", Icons.Filled.Download) { onDismiss(); onDownload() }
        MenuItem("Taşı", Icons.AutoMirrored.Filled.DriveFileMove) { onDismiss(); onMove() }
        MenuItem("Yeniden adlandır", Icons.Filled.Edit) { onDismiss(); onRename() }
        MenuItem("Bilgi", Icons.Outlined.Info) { onDismiss(); onInfo() }
        MenuItem("Sil", Icons.Filled.Delete, destructive = true) { onDismiss(); onDelete() }
    }
}

/** Çöpteki bir dosyanın ön izleme menüsü: yalnızca İndir ve Bilgi (çöpteki dosya değiştirilemez). */
@Composable
fun TrashedFileActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("İndir", Icons.Filled.Download) { onDismiss(); onDownload() }
        MenuItem("Bilgi", Icons.Outlined.Info) { onDismiss(); onInfo() }
    }
}

/** Bir klasörün 3 nokta menüsü: Düzenle, Taşı (AI sonuçları klasörlerinde yok), Sil (kırmızı). */
@Composable
fun FolderActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canMove: Boolean,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("Düzenle", Icons.Filled.Edit) { onDismiss(); onEdit() }
        if (canMove) MenuItem("Taşı", Icons.AutoMirrored.Filled.DriveFileMove) { onDismiss(); onMove() }
        MenuItem("Sil", Icons.Filled.Delete, destructive = true) { onDismiss(); onDelete() }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick,
    )
}
