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
import com.eayd.arkive.utils.TurkishLocale
import com.eayd.arkive.utils.fileTypeLabel
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.utils.formatServerDateTime

/**
 * "Bilgi" penceresi: tür, boyut, kullandığı alan ve oluşturulma tarihi.
 *
 * @param storageLimit hesabın toplam kotası (bayt). Henüz gelmediyse ya da alınamadıysa null: "Kullandığı alan"
 *                     satırı kota oranı olmadan gösterilir.
 */
@Composable
fun FileInfoDialog(
    file: FileDto,
    storageLimit: Long?,
    onDismiss: () -> Unit,
) {
    // "Kullandığı alan": dosyanın kotadan kapladığı yer. Dosya kotaya tam boyutuyla yazıldığı için boyutla aynıdır;
    // asıl bilgi, bunun kotanın ne kadarı olduğudur.
    val usedSpace = buildString {
        append(formatBytes(file.size))
        if (storageLimit != null && storageLimit > 0) {
            val percent = file.size * 100.0 / storageLimit
            append(
                if (percent < 0.1) "\nKotanızın %0,1'inden azı"
                else "\nKotanızın %" + String.format(TurkishLocale, "%.1f", percent) + " kadarı"
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
                InfoLine("Tür", fileTypeLabel(file.mimeType, file.name))
                Spacer(Modifier.height(12.dp))
                InfoLine("Boyut", formatBytes(file.size))
                Spacer(Modifier.height(12.dp))
                InfoLine("Kullandığı alan", usedSpace)
                Spacer(Modifier.height(12.dp))
                InfoLine("Oluşturulma", formatServerDateTime(file.createdAt))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
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
