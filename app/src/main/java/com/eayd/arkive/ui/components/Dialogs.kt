package com.eayd.arkive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Ekranların ortak pencereleri. Pencerenin açık/kapalı olması, "kaydediliyor" ve sunucu hatası ViewModel'de
// (DialogHost) tutulur; buradaki bileşenler yalnızca verilen değerleri çizer ve olayları iletir.

/** Onay penceresi (silme gibi geri alınması zor işler için). [error]: sunucunun verdiği hata, pencerede gösterilir. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    saving: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Sil",
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("İptal") } },
    )
}

// Klasör renk seçenekleri. null = renksiz (varsayılan tema rengi kullanılır).
private val FolderColorChoices: List<String?> =
    listOf(null, "#1E88E5", "#43A047", "#FB8C00", "#E53935", "#8E24AA", "#00897B")

/**
 * Ad (ve istenirse renk) giriş penceresi: klasör oluşturma/düzenleme ve dosya yeniden adlandırma için ortak.
 * Dosyada renk yoktur, o yüzden [showColor] = false verilir.
 */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    label: String,
    initialName: String,
    saving: Boolean,
    serverError: String?,
    onConfirm: (name: String, color: String?) -> Unit,
    onDismiss: () -> Unit,
    initialColor: String? = null,
    showColor: Boolean = true,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }

    // Sunucudaki kuralların aynısı istemcide de denetlenir; kullanıcı daha gönderirken uyarılır.
    val trimmed = name.trim()
    val localError = when {
        trimmed.length > 255 -> "En fazla 255 karakter olabilir"
        '/' in trimmed || '\\' in trimmed -> "Ad '/' veya '\\' içeremez"
        else -> null
    }
    val shownError = localError ?: serverError

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(label) },
                    singleLine = true,
                    enabled = !saving,
                    isError = shownError != null,
                    supportingText = { shownError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showColor) {
                    Spacer(Modifier.height(12.dp))
                    Text("Renk", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    ColorSwatches(current = color, enabled = !saving, onSelect = { color = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && trimmed.isNotEmpty() && localError == null,
                onClick = { onConfirm(trimmed, color) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("İptal") } },
    )
}

@Composable
private fun ColorSwatches(current: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FolderColorChoices.forEach { hex ->
            val isSelected = hex == current
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(folderColorOrNull(hex) ?: Color.Transparent)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(hex) }
                    .semantics {
                        contentDescription = if (hex == null) "Renksiz" else "Renk $hex"
                        selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (hex == null) Text("–", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
