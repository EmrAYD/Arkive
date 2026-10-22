package com.eayd.arkive.screens.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ui.components.ConfirmDialog
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.FileGlyph
import com.eayd.arkive.ui.components.FolderGlyph
import com.eayd.arkive.ui.components.ItemRow
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.SelectCircle
import com.eayd.arkive.ui.components.folderAccent
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.viewmodel.TrashUiState
import com.eayd.arkive.viewmodel.TrashViewModel
import com.eayd.arkive.viewmodel.fileKey
import com.eayd.arkive.viewmodel.folderKey

/**
 * Çöp kutusu. Normal modda: geri oku, başlık + öğe sayısı, sağda "Seç"; altında saklama süresini anlatan bilgi
 * metni ve silinen öğeler (her birinde kalıcı silinmeye kalan gün). "Seç"e ya da bir öğeye basınca seçim modu
 * açılır: solda "Hepsi", ortada başlık, sağda "İptal"; öğelerde seçim daireleri; altta "Geri yükle" ve "Kalıcı sil".
 * Ekran yalnızca [TrashViewModel] durumunu çizer; seçim, geri yükleme ve silme ViewModel'dedir.
 */
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onBack: () -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    onOpenFile: (fileId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val inFolder = viewModel.isFolderView

    LaunchedEffect(viewModel) { viewModel.onShown() }
    NoticeEffect(state.notice, snackbar, viewModel::clearNotice)

    // Geri tuşu: önce seçim modundan çıkar, sonra çöp kutusundan.
    BackHandler { if (state.selection.active) viewModel.stopSelecting() else onBack() }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            if (state.selection.active) {
                SelectionHeader(
                    count = state.selection.count,
                    allSelected = state.allSelected,
                    enabled = !state.busy,
                    onToggleAll = viewModel::toggleAll,
                    onCancel = viewModel::stopSelecting,
                )
            } else {
                TrashHeader(
                    title = state.folderName ?: "Çöp kutusu",
                    subtitle = headerSubtitle(state, inFolder),
                    showSelect = !state.isLoading && state.itemCount > 0,
                    onBack = onBack,
                    onSelect = viewModel::startSelecting,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val error = state.errorMessage
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> ErrorRetry(error, viewModel::load, Modifier.align(Alignment.Center))
                    state.itemCount == 0 -> EmptyTrash(state.retentionDays, inFolder)
                    else -> TrashList(state, viewModel, inFolder, onOpenFolder, onOpenFile)
                }
            }

            if (state.selection.active) {
                ActionBar(
                    enabled = state.selection.count > 0 && !state.busy,
                    onRestore = viewModel::restoreSelected,
                    onDeleteForever = viewModel::askDeleteForever,
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (state.confirmDelete) {
        ConfirmDialog(
            title = "${state.selection.count} öğe kalıcı olarak silinsin mi?",
            message = "Seçilen öğeler (klasörlerin içindekilerle birlikte) çöp kutusundan ve depolama alanınızdan " +
                "silinecek. Bu işlem geri alınamaz.",
            confirmLabel = "Kalıcı sil",
            saving = state.busy,
            error = null,
            onConfirm = viewModel::deleteSelectedForever,
            onDismiss = viewModel::dismissDeleteConfirm,
        )
    }
}

// --- Başlıklar --------------------------------------------------------------------------------------

@Composable
private fun TrashHeader(
    title: String,
    subtitle: String,
    showSelect: Boolean,
    onBack: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (showSelect) {
            TextButton(onClick = onSelect) { Text("Seç") }
        }
    }
}

/** Seçim modunun başlığı: solda daire + "Hepsi", ortada "Öğeleri seç" / "N öğe seçildi", sağda "İptal". */
@Composable
private fun SelectionHeader(
    count: Int,
    allSelected: Boolean,
    enabled: Boolean,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggleAll)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectCircle(checked = allSelected)
            Spacer(Modifier.width(8.dp))
            Text("Hepsi", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = if (count == 0) "Öğeleri seç" else "$count öğe seçildi",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        TextButton(onClick = onCancel, enabled = enabled) { Text("İptal") }
    }
}

/** Başlığın altındaki satır: kökte öğe sayısı, klasör içinde "Çöp kutusunda · N gün kaldı". */
private fun headerSubtitle(state: TrashUiState, inFolder: Boolean): String = when {
    inFolder -> "Çöp kutusunda" + (state.folderDaysLeft?.let {
        " · " + if (it <= 0) "bugün silinecek" else "$it gün kaldı"
    } ?: "")
    state.itemCount == 0 -> "Boş"
    else -> "${state.itemCount} öğe"
}

// --- Liste ------------------------------------------------------------------------------------------

@Composable
private fun TrashList(
    state: TrashUiState,
    viewModel: TrashViewModel,
    inFolder: Boolean,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val selection = state.selection
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "info") {
            Text(
                text = if (inFolder) FOLDER_INFO else infoText(state.retentionDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(state.folders, key = { folderKey(it.id) }) { folder ->
            val key = folderKey(folder.id)
            TrashRow(
                title = folder.name,
                subtitle = if (folder.fileCount == 0) "Klasör" else "${folder.fileCount} dosya · ${formatBytes(folder.size)}",
                daysLeft = folder.daysLeft.takeUnless { inFolder },   // klasörün içindekiler klasörle birlikte silindi: aynı gün sayısını tekrarlamaya gerek yok
                icon = { FolderGlyph(folderAccent(folder.color)) },
                selecting = selection.active,
                selected = key in selection.keys,
                onClick = { if (selection.active) viewModel.toggle(key) else onOpenFolder(folder.id) },
                onLongClick = { viewModel.longPress(key) },
            )
        }
        items(state.files, key = { fileKey(it.id) }) { file ->
            val key = fileKey(file.id)
            TrashRow(
                title = file.name,
                subtitle = formatBytes(file.size),
                daysLeft = file.daysLeft.takeUnless { inFolder },
                icon = { FileGlyph(file.mimeType) },
                selecting = selection.active,
                selected = key in selection.keys,
                onClick = { if (selection.active) viewModel.toggle(key) else onOpenFile(file.id) },
                onLongClick = { viewModel.longPress(key) },
            )
        }
    }
}

private const val FOLDER_INFO =
    "Bu klasör çöp kutusunda. İçindeki dosya ve klasörleri seçip geri yükleyebilir ya da kalıcı olarak " +
        "silebilirsiniz. Tek başına geri yüklenenler, klasör hâlâ çöpte olduğu için Ana Sayfa'ya gelir; " +
        "klasörün tamamını geri yüklemek için çöp kutusuna dönüp klasörü seçin."

private fun infoText(days: Int): String =
    "Silinen dosya ve klasörler burada $days gün saklanır; süre dolunca kalıcı olarak silinir. " +
        "Bu süreyi ayarlardan değiştirebilirsiniz. " +
        "Çöp kutusundakiler, kalıcı olarak silinene kadar depolama alanınızı kullanmaya devam eder."

/** Çöp kutusu satırı: ortak [ItemRow] + sağda kalıcı silinmeye kalan gün ([daysLeft] null ise gösterilmez). */
@Composable
private fun TrashRow(
    title: String,
    subtitle: String,
    daysLeft: Int?,
    icon: @Composable () -> Unit,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ItemRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        selectMark = if (selecting) selected else null,
        trailing = {
            if (daysLeft != null) {
                Text(
                    text = if (daysLeft <= 0) "Bugün" else "$daysLeft gün",
                    style = MaterialTheme.typography.labelMedium,
                    // Süresi dolmaya 3 gün ya da daha az kaldıysa dikkat çekmesi için kırmızı.
                    color = if (daysLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    )
}

// --- Alt çubuk, boş durum ---------------------------------------------------------------------------

@Composable
private fun ActionBar(enabled: Boolean, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onRestore, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Geri yükle", maxLines = 1)
            }
            Button(
                onClick = onDeleteForever,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kalıcı olarak sil", maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyTrash(retentionDays: Int, inFolder: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(if (inFolder) "Bu klasör boş" else "Çöp kutusu boş", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (inFolder) "Buradaki her şey geri yüklendi ya da silindi."
            else "Sildiğiniz dosya ve klasörler burada $retentionDays gün saklanır. Bu süreyi ayarlardan değiştirebilirsiniz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}
