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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.components.ConfirmDialog
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.FileGlyph
import com.eayd.arkive.ui.components.FolderGlyph
import com.eayd.arkive.ui.components.ItemRow
import com.eayd.arkive.ui.components.LeaveEffect
import com.eayd.arkive.ui.components.ListSkeleton
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.NoticeHost
import com.eayd.arkive.ui.components.rememberNoticeHostState
import com.eayd.arkive.ui.components.SelectCircle
import com.eayd.arkive.ui.components.folderAccent
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.viewmodel.TrashUiState
import com.eayd.arkive.viewmodel.TrashViewModel
import com.eayd.arkive.viewmodel.fileKey
import com.eayd.arkive.viewmodel.folderKey

/**
 * Çöp kutusu. Normal modda: solda geri oku (ana ekranın içindeyken, yani [onOpenMenu] verilmişse bunun yerine menü düğmesi), başlık + öğe sayısı, sağda "Tümünü sil" (kökte; onay sorar) ve "Seç"; altında saklama süresini anlatan bilgi
 * metni ve silinen öğeler (her birinde kalıcı silinmeye kalan gün). "Seç"e ya da bir öğeye basınca seçim modu
 * açılır: solda "Hepsi", ortada başlık, sağda "İptal"; öğelerde seçim daireleri; altta "Geri yükle" ve "Kalıcı olarak sil".
 * Ekran yalnızca [TrashViewModel] durumunu çizer; seçim, geri yükleme ve silme ViewModel'dedir.
 */
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onBack: () -> Unit,
    onOpenFolder: (folderId: String) -> Unit,
    onOpenFile: (fileId: String) -> Unit,
    /**
     * Ana ekranın içindeki (kök) çöp kutusunda verilir: soldaki geri oku yerine, diğer sayfalardaki gibi yan menüyü açan
     * hamburger düğmesi gösterilir. Çöpteki bir klasörün içinde (ayrı ekran) verilmez; orada geri oku kalır.
     */
    onOpenMenu: (() -> Unit)? = null,
    /** false: sayfa şu an görünmüyor (üstünde profil sayfası açık); seçim modu kapatılır. */
    active: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    val notices = rememberNoticeHostState()
    val inFolder = viewModel.isFolderView

    LaunchedEffect(viewModel) { viewModel.onShown() }
    // Sayfadan çıkılınca (menüden başka bölüme geçildi, bir klasöre girildi) seçim modu kapanır; geri gelince sürmez.
    // Ekran döndürülünce kapanmaz.
    LeaveEffect(viewModel) { viewModel.stopSelecting() }
    LaunchedEffect(active) { if (!active) viewModel.stopSelecting() }
    NoticeEffect(state.notice, notices, viewModel::clearNotice)

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
                    title = state.folderName ?: Txt.home.trash,
                    subtitle = headerSubtitle(state, inFolder),
                    showSelect = !state.isLoading && state.itemCount > 0,
                    // Tüm çöp kutusunu boşaltma yalnızca kökte; çöpteki bir klasörün içinde gösterilmez.
                    showEmpty = !state.isLoading && state.itemCount > 0 && !inFolder,
                    emptyEnabled = !state.busy,
                    onBack = onBack,
                    onOpenMenu = onOpenMenu,
                    onSelect = viewModel::startSelecting,
                    onEmpty = viewModel::askEmptyTrash,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val error = state.errorMessage
                when {
                    state.isLoading -> ListSkeleton(Modifier.fillMaxSize())
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

        NoticeHost(notices, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }

    if (state.confirmEmpty) {
        ConfirmDialog(
            title = Txt.trash.emptyAllTitle,
            message = Txt.trash.emptyAllBody(state.itemCount),
            confirmLabel = Txt.trash.deletePermanently,
            saving = state.busy,
            error = null,
            onConfirm = viewModel::emptyTrash,
            onDismiss = viewModel::dismissEmptyConfirm,
        )
    }

    if (state.confirmDelete) {
        ConfirmDialog(
            title = Txt.trash.deleteSelectedTitle(state.selection.count),
            message = Txt.trash.deleteSelectedBody,
            confirmLabel = Txt.trash.deletePermanently,
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
    showEmpty: Boolean,
    emptyEnabled: Boolean,
    onBack: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onSelect: () -> Unit,
    onEmpty: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenMenu != null) {
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Filled.Menu, contentDescription = Txt.home.openMenu)
            }
        } else {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Txt.common.back)
            }
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
        if (showEmpty) {
            // "Seç"in solunda: kırmızı, çünkü her şeyi kalıcı siler (önce onay penceresi açılır).
            TextButton(
                onClick = onEmpty,
                enabled = emptyEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(Txt.trash.deleteAll) }
        }
        if (showSelect) {
            TextButton(onClick = onSelect) { Text(Txt.trash.select) }
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
            Text(Txt.trash.all, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = if (count == 0) Txt.trash.selectItems else Txt.trash.selectedCount(count),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        TextButton(onClick = onCancel, enabled = enabled) { Text(Txt.common.cancel) }
    }
}

/** Başlığın altındaki satır: kökte öğe sayısı, klasör içinde "Çöp kutusunda · N gün kaldı". */
private fun headerSubtitle(state: TrashUiState, inFolder: Boolean): String = when {
    inFolder -> Txt.trash.inTrash + (state.folderDaysLeft?.let {
        " · " + if (it <= 0) Txt.trash.deletedToday else Txt.trash.daysLeft(it)
    } ?: "")
    state.itemCount == 0 -> Txt.trash.empty
    else -> Txt.trash.itemCount(state.itemCount)
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
                subtitle = if (folder.fileCount == 0) Txt.trash.folder else Txt.trash.folderMeta(folder.fileCount, formatBytes(folder.size)),
                daysLeft = folder.daysLeft.takeUnless { inFolder },   // klasörün içindekiler klasörle birlikte silindi: aynı gün sayısını tekrarlamaya gerek yok
                icon = { FolderGlyph(folderAccent(folder.color), folder.emoji) },
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

private val FOLDER_INFO: String
    get() = Txt.trash.folderInTrashNote

private fun infoText(days: Int): String =
    Txt.trash.retentionNote(days)

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
                    text = if (daysLeft <= 0) Txt.trash.today else Txt.trash.daysShort(daysLeft),
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
                Text(Txt.trash.restore, maxLines = 1)
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
                Text(Txt.trash.deletePermanently, maxLines = 1)
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
        Text(if (inFolder) Txt.trash.folderEmpty else Txt.trash.trashEmpty, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (inFolder) Txt.trash.allHandled
            else Txt.trash.emptyNote(retentionDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}
