package com.eayd.arkive.screens.folders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.ui.components.FileActionsMenu
import com.eayd.arkive.ui.components.FileGlyph
import com.eayd.arkive.ui.components.FolderActionsMenu
import com.eayd.arkive.ui.components.FolderGlyph
import com.eayd.arkive.ui.components.ItemRow
import com.eayd.arkive.ui.components.RowActions
import com.eayd.arkive.ui.components.folderAccent
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.viewmodel.UploadItem
import com.eayd.arkive.viewmodel.UploadPhase

// Liste ekranının satırları. Hepsi ortak [ItemRow] üzerine kurulu; fark yalnızca içerik ve menülerdir.

/** Satırda yapılabilecek işlemler: ekran bunları ViewModel'e bağlar. */
class FolderRowActions(
    val onToggleFavorite: () -> Unit,
    val onEdit: () -> Unit,
    val onMove: () -> Unit,
    val onDelete: () -> Unit,
)

class FileRowActions(
    val onToggleFavorite: () -> Unit,
    val onToggleAi: () -> Unit,
    val onDownload: () -> Unit,
    val onMove: () -> Unit,
    val onRename: () -> Unit,
    val onInfo: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
fun FolderRow(
    folder: FolderDto,
    actions: FolderRowActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    highlighted: Boolean = false,   // sürüklenen öğe şu an bu klasörün üstünde
    dimmed: Boolean = false,        // sürüklenen klasörün yerindeki satır soluk görünür
    menuEnabled: Boolean = true,    // internetsizken 3 nokta menüsü pasiftir
) {
    ItemRow(
        title = folder.name,
        icon = { FolderGlyph(folderAccent(folder.color), folder.emoji) },
        onClick = onOpen,
        modifier = modifier,
        selected = selected,
        highlighted = highlighted,
        dimmed = dimmed,
        trailing = {
            if (selectionMode) {
                SelectionCheck(selected)
            } else {
                RowActions(folder.favorite, actions.onToggleFavorite, menuEnabled = menuEnabled) { expanded, dismiss ->
                    FolderActionsMenu(
                        expanded = expanded,
                        onDismiss = dismiss,
                        canMove = folder.location == FolderLocations.HOME,
                        onEdit = actions.onEdit,
                        onMove = actions.onMove,
                        onDelete = actions.onDelete,
                    )
                }
            }
        },
    )
}

@Composable
fun FileRow(
    file: FileDto,
    actions: FileRowActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    dimmed: Boolean = false,   // sürüklenen dosyanın yerindeki satır soluk görünür
    ai: Boolean? = false,      // dosyanın özeti var mı (AI düğmesi dolu görünür); null ise düğme hiç gösterilmez
    aiWorking: Boolean = false, // özet hazırlanıyor mu (AI düğmesi yerine yükleme simgesi)
    aiStatus: String? = null,   // özetleme sürerken hangi adımda olduğu (boyutun yanında gösterilir)
    enabled: Boolean = true,    // false: satır pasif (internetsizken telefonda kopyası olmayan dosya)
    menuEnabled: Boolean = true, // internetsizken 3 nokta menüsü pasiftir
) {
    ItemRow(
        title = file.name,
        subtitle = if (aiStatus != null) "${formatBytes(file.size)} · $aiStatus" else formatBytes(file.size),
        icon = { FileGlyph(file.mimeType) },
        onClick = onOpen,
        modifier = modifier,
        selected = selected,
        dimmed = dimmed,
        enabled = enabled,
        trailing = {
            if (selectionMode) {
                SelectionCheck(selected)
            } else {
                RowActions(
                    file.favorite, actions.onToggleFavorite,
                    ai = ai, aiWorking = aiWorking, onToggleAi = actions.onToggleAi, menuEnabled = menuEnabled,
                ) { expanded, dismiss ->
                    FileActionsMenu(
                        expanded = expanded,
                        onDismiss = dismiss,
                        onDownload = actions.onDownload,
                        onMove = actions.onMove,
                        onRename = actions.onRename,
                        onInfo = actions.onInfo,
                        onDelete = actions.onDelete,
                    )
                }
            }
        },
    )
}

/** Seçim modunda satırın sağındaki onay kutusu. Satırın tamamı tıklanır, kutunun kendisi yalnızca gösterir. */
@Composable
private fun SelectionCheck(checked: Boolean) {
    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
}

/** Devam eden ya da başarısız olmuş bir yüklemenin satırı: ad, ilerleme çubuğu ve iptal/kapat düğmesi. */
@Composable
fun UploadRow(item: UploadItem, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val failed = item.phase == UploadPhase.Failed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = if (failed) onDismiss else onCancel) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (failed) "Kapat" else "Yüklemeyi iptal et",
                    )
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                when (item.phase) {
                    UploadPhase.Waiting -> UploadStatus("Sırada bekliyor…")
                    UploadPhase.Uploading -> {
                        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        UploadStatus("Yükleniyor… %${(item.progress * 100).toInt()}")
                    }
                    UploadPhase.Finishing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        UploadStatus("Tamamlanıyor…")
                    }
                    UploadPhase.Failed -> UploadStatus(
                        text = item.error ?: "Yükleme başarısız oldu.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadStatus(text: String, color: Color = Color.Unspecified) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
