package com.eayd.arkive.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.i18n.Lang
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.fileTypeLabel
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.utils.formatServerDateTime

/** Dosya bilgi penceresi. [storageLimit] bilinmiyorsa kota oranı gösterilmez. */
@Composable
fun FileInfoDialog(
    file: FileDto,
    storageLimit: Long?,
    onDismiss: () -> Unit,
) {
    val usedSpace = buildString {
        append(formatBytes(file.size))
        if (storageLimit != null && storageLimit > 0) {
            val percent = file.size * 100.0 / storageLimit
            append(
                if (percent < 0.1) "\n" + Txt.files.quotaLessThanTenth
                else "\n" + Txt.files.quotaPercent(String.format(Lang.locale, "%.1f", percent))
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = file.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                InfoLine(Txt.files.type, fileTypeLabel(file.mimeType, file.name))
                Spacer(Modifier.height(12.dp))
                InfoLine(Txt.files.size, formatBytes(file.size))
                Spacer(Modifier.height(12.dp))
                InfoLine(Txt.files.spaceUsed, usedSpace)
                Spacer(Modifier.height(12.dp))
                InfoLine(Txt.files.created, formatServerDateTime(file.createdAt))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Txt.common.close) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
