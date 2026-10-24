package com.eayd.arkive.screens.folders

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.ui.components.CenteredHint
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.rememberFileDownloader
import com.eayd.arkive.viewmodel.BrowserDialog
import com.eayd.arkive.viewmodel.BrowserUiState
import com.eayd.arkive.viewmodel.BrowserViewModel
import com.eayd.arkive.viewmodel.fileKey
import com.eayd.arkive.viewmodel.folderKey

/**
 * Bir seviyedeki klasörler ve dosyalar, üstlerinde işlem satırı ("+" düğmesi, filtreler) ve devam eden yüklemeler.
 * Ana sayfa, AI sonuçları, Favorilerim ve klasör içi ekranı aynı bileşeni kullanır; fark yalnızca verilen
 * ViewModel'dir.
 *
 * Bu bileşen yalnızca çizer: liste, süzme/sıralama, seçim, pencereler ve sunucu işlemleri [BrowserViewModel]
 * içindedir; buradaki her etkileşim ViewModel'e bir çağrı olarak iletilir.
 */
@Composable
fun BrowserContent(
    viewModel: BrowserViewModel,
    onOpenFolder: (FolderDto) -> Unit,
    onOpenFile: (FileDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val selection = state.selection
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val startDownload = rememberFileDownloader(viewModel::showNotice)
    val listState = rememberLazyListState()
    val dragState = remember { FileDragState() }

    // Ekran her görününce liste tazelenir (başka ekranda değişmiş olabilir: ör. Favorilerim).
    LaunchedEffect(viewModel) { viewModel.onShown() }
    NoticeEffect(state.notice, snackbar, viewModel::clearNotice)

    // Geri tuşu önce seçimi kapatır.
    BackHandler(enabled = selection.active, onBack = viewModel::clearSelection)

    // Sistemin dosya seçicisi. Seçilen her dosya için geçici okuma izni verilir (içerik "content://" adresidir).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.uploadFiles(context.applicationContext.contentResolver, uris)
    }

    Column(modifier.fillMaxSize()) {
        if (selection.active) {
            val selectedFolders = state.selectedFolders
            SelectionBar(
                count = selection.count,
                allFavorite = state.selectedItems.allFavorite,
                // AI sonuçları klasörleri taşınamaz.
                canMove = selectedFolders.all { it.location == FolderLocations.HOME },
                onClose = viewModel::clearSelection,
                onSelectAll = viewModel::selectAllVisible,
                onFavorite = viewModel::favoriteSelection,
                onMove = { viewModel.openDialog(BrowserDialog.MoveSelection) },
                onDelete = { viewModel.openDialog(BrowserDialog.DeleteSelection) },
            )
        } else {
            // Favorilerde "ekle" anlamsızdır (favori, başka yerdeki öğelere yıldız konarak oluşur).
            FolderActionRow(
                canUpload = state.canUpload,
                showAdd = !viewModel.favoritesOnly,
                onNewFolder = { viewModel.openDialog(BrowserDialog.NewFolder) },
                onUploadFiles = { picker.launch("*/*") },
                filters = {
                    BrowserFilters(state.sort, state.category, viewModel::setSort, viewModel::setCategory)
                },
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .itemDragSource(
                    state = dragState,
                    onLongPress = viewModel::longPress,
                    onDrop = viewModel::dropItems,
                )
        ) {
            val nothingToShow = state.folders.isEmpty() && state.files.isEmpty() && state.uploads.isEmpty()
            val nothingVisible = state.visibleFolders.isEmpty() && state.visibleFiles.isEmpty() && state.uploads.isEmpty()
            val error = state.errorMessage
            when {
                state.isLoading && nothingToShow -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                error != null -> ErrorRetry(error, viewModel::load, Modifier.align(Alignment.Center))

                nothingToShow -> CenteredHint(
                    text = emptyText(state, viewModel.favoritesOnly),
                    modifier = Modifier.align(Alignment.Center),
                )

                nothingVisible -> CenteredHint(
                    text = "Seçili kategoriye uyan bir şey yok.",
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> BrowserList(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    dragState = dragState,
                    onOpenFolder = onOpenFolder,
                    onOpenFile = onOpenFile,
                    onDownload = { file ->
                        // Dosyaya dokunmak ön izleme ekranını açar; bu yalnızca menüdeki "İndir" içindir.
                        // Uygulama içinden İndirilenler klasörüne kaydedilir (tarayıcı açılmaz).
                        startDownload(file.id, file.name, file.mimeType)
                    },
                )
            }

            AutoScrollWhileDragging(dragState, listState)
            DragGhost(dragState)

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }

    BrowserDialogs(viewModel)
}

private fun emptyText(state: BrowserUiState, favoritesOnly: Boolean): String = when {
    favoritesOnly -> "Henüz favori eklemediniz.\nKlasör ya da dosyaların yanındaki yıldıza dokunun."
    state.canUpload -> "Burada henüz klasör ya da dosya yok.\nSağ üstteki + düğmesiyle ekleyebilirsiniz."
    else -> "Burada henüz klasör yok.\nSağ üstteki + düğmesiyle oluşturabilirsiniz."
}

@Composable
private fun BrowserList(
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    listState: LazyListState,
    dragState: FileDragState,
    onOpenFolder: (FolderDto) -> Unit,
    onOpenFile: (FileDto) -> Unit,
    onDownload: (FileDto) -> Unit,
) {
    val selection = state.selection
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // key: listede ekleme/silme olunca satırların kimliği korunsun (id'ler birbirine karışmasın diye
        // klasör ve dosya anahtarları farklı ön ekle başlar).
        items(state.uploads, key = { "upload-${it.id}" }) { upload ->
            UploadRow(
                item = upload,
                onCancel = { viewModel.cancelUpload(upload.id) },
                onDismiss = { viewModel.dismissUpload(upload.id) },
            )
        }
        items(state.visibleFolders, key = { folderKey(it.id) }) { folder ->
            val rowKey = folderKey(folder.id)
            var rowModifier = Modifier.trackBounds(dragState.itemCoords, rowKey)
            // Dosyalar ve klasörler yalnızca "Ana Sayfa" klasörlerine bırakılabilir (AI sonuçları klasörlerine değil).
            if (folder.location == FolderLocations.HOME) {
                rowModifier = rowModifier.trackBounds(dragState.folderCoords, folder.id)
            }
            FolderRow(
                folder = folder,
                modifier = rowModifier,
                actions = FolderRowActions(
                    onToggleFavorite = { viewModel.toggleFavorite(folder) },
                    onEdit = { viewModel.openDialog(BrowserDialog.EditFolder(folder)) },
                    onMove = { viewModel.openDialog(BrowserDialog.MoveFolder(folder)) },
                    onDelete = { viewModel.openDialog(BrowserDialog.DeleteFolder(folder)) },
                ),
                highlighted = dragState.hoverFolderId == folder.id,
                dimmed = dragState.dragged?.keys?.contains(rowKey) == true,
                selectionMode = selection.active,
                selected = rowKey in selection.keys,
                onOpen = { if (selection.active) viewModel.toggleSelection(rowKey) else onOpenFolder(folder) },
            )
        }
        items(state.visibleFiles, key = { fileKey(it.id) }) { file ->
            val rowKey = fileKey(file.id)
            FileRow(
                file = file,
                modifier = Modifier.trackBounds(dragState.itemCoords, rowKey),
                actions = FileRowActions(
                    onToggleFavorite = { viewModel.toggleFavorite(file) },
                    onDownload = { onDownload(file) },
                    onMove = { viewModel.openDialog(BrowserDialog.MoveFile(file)) },
                    onRename = { viewModel.openDialog(BrowserDialog.RenameFile(file)) },
                    onInfo = { viewModel.openDialog(BrowserDialog.FileInfo(file)) },
                    onDelete = { viewModel.openDialog(BrowserDialog.DeleteFile(file)) },
                ),
                dimmed = dragState.dragged?.keys?.contains(rowKey) == true,
                selectionMode = selection.active,
                selected = rowKey in selection.keys,
                onOpen = { if (selection.active) viewModel.toggleSelection(rowKey) else onOpenFile(file) },
            )
        }
    }
}
