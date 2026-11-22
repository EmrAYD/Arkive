package com.eayd.arkive.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
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
import com.eayd.arkive.i18n.Txt

/** Dosyanın 3 nokta menüsü; listede ve ön izlemede aynı. [onMove] null ise "Taşı" yok. */
@Composable
fun FileActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onMove: (() -> Unit)?,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(Txt.common.download, Icons.Filled.Download) { onDismiss(); onDownload() }
        if (onMove != null) MenuItem(Txt.common.move, Icons.AutoMirrored.Filled.DriveFileMove) { onDismiss(); onMove() }
        MenuItem(Txt.common.rename, Icons.Filled.Edit) { onDismiss(); onRename() }
        MenuItem(Txt.common.info, Icons.Outlined.Info) { onDismiss(); onInfo() }
        MenuItem(Txt.common.delete, Icons.Filled.Delete, destructive = true) { onDismiss(); onDelete() }
    }
}

/** Çöpteki dosyanın menüsü: yalnızca İndir ve Bilgi. */
@Composable
fun TrashedFileActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(Txt.common.download, Icons.Filled.Download) { onDismiss(); onDownload() }
        MenuItem(Txt.common.info, Icons.Outlined.Info) { onDismiss(); onInfo() }
    }
}

/** Klasörün 3 nokta menüsü. */
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
        MenuItem(Txt.common.edit, Icons.Filled.Edit) { onDismiss(); onEdit() }
        if (canMove) MenuItem(Txt.common.move, Icons.AutoMirrored.Filled.DriveFileMove) { onDismiss(); onMove() }
        MenuItem(Txt.common.delete, Icons.Filled.Delete, destructive = true) { onDismiss(); onDelete() }
    }
}

/** AI Sonuçlarım'daki özetin menüsü: dosyayı aç ve özeti sil. */
@Composable
fun SummaryActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(Txt.ai.openSourceFile, Icons.Filled.Description) { onDismiss(); onOpenFile() }
        MenuItem(Txt.ai.deleteSummary, Icons.Filled.Delete, destructive = true) { onDismiss(); onDelete() }
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
