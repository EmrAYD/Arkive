package com.eayd.arkive.screens.folders

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.ai.InputCache
import com.eayd.arkive.ai.SummaryStore
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.ui.components.AiSummaryDialogHost
import com.eayd.arkive.ui.components.CenteredHint
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.ListSkeleton
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.rememberFileDownloader
import com.eayd.arkive.viewmodel.BrowserDialog
import com.eayd.arkive.viewmodel.BrowserUiState
import com.eayd.arkive.viewmodel.BrowserViewModel
import com.eayd.arkive.viewmodel.fileKey
import com.eayd.arkive.viewmodel.folderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bir seviyedeki klasörler ve dosyalar, üstlerinde işlem satırı ("+" düğmesi, filtreler) ve devam eden yüklemeler.
 * Ana sayfa, AI sonuçları, Favorilerim ve klasör içi ekranı aynı bileşeni kullanır; fark yalnızca verilen
 * ViewModel'dir.
 *
 * Bu bileşen yalnızca çizer: liste, süzme/sıralama, seçim, pencereler ve sunucu işlemleri [BrowserViewModel]
 * içindedir; buradaki her etkileşim ViewModel'e bir çağrı olarak iletilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    // Özetleme arka planda sürer; bitince ya da olmazsa kısa bir bildirimle haber verilir.
    LaunchedEffect(viewModel) { AiSummarizer.notices.collect(viewModel::showNotice) }
    // Özet bitince liste sessizce yenilenir (AI düğmesi dolar); dolu düğmeye basılınca özet dosyasının ön izlemesi açılır.
    val openPreview by rememberUpdatedState(onOpenFile)
    LaunchedEffect(viewModel) { AiSummarizer.completed.collect { viewModel.reloadQuietly() } }
    LaunchedEffect(viewModel) { AiSummarizer.flushPending() } // internetsizken üretilen özetler varsa yükle
    // Liste her yenilendiğinde küçük dosyalar arka planda telefona kopyalanır: internetsiz de yerel özetlenebilsinler.
    LaunchedEffect(state.files, state.offline) { if (!state.offline) AiSummarizer.prefetch(state.files) }
    // İnternet geri gelince, liste telefondaki görüntüden gösteriliyorsa sunucudan tazelenir.
    val offlineNow by rememberUpdatedState(state.offline)
    LaunchedEffect(viewModel) { AiSummarizer.networkBack.collect { if (offlineNow) viewModel.reloadQuietly() } }
    LaunchedEffect(viewModel) { AiSummarizer.openFile.collect { openPreview(it) } }
    NoticeEffect(state.notice, snackbar, viewModel::clearNotice)

    // Geri tuşu önce seçimi kapatır.
    BackHandler(enabled = selection.active, onBack = viewModel::clearSelection)

    // Sistemin dosya seçicisi. Seçilen her dosya için geçici okuma izni verilir (içerik "content://" adresidir).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.uploadFiles(context.applicationContext.contentResolver, uris)
    }

    Column(modifier.fillMaxSize()) {
        if (selection.active) {
            SelectionBar(
                count = selection.count,
                allFavorite = state.selectedItems.allFavorite,
                // AI sonuçları klasörleri taşınamaz; normal öğeler ile yapay zeka sonuç dosyaları da birlikte taşınamaz.
                canMove = state.moveLocation != null,
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

        if (state.offline) {
            Text(
                text = "Sunucuya ulaşılamadı. Liste telefondaki son kayıttan gösteriliyor; telefonda kopyası olan küçük dosyalar yine de özetlenebilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
            // Listeyi (ya da boş durum yazısını) aşağı çekmek sunucudan yeniler. Boş durumlar da kaydırılabilir bir
            // kapta durur, yoksa çekme hareketini yakalayacak bir şey olmazdı.
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
            when {
                state.isLoading && nothingToShow -> ListSkeleton(Modifier.fillMaxSize())

                error != null -> PullableCenter { ErrorRetry(error, viewModel::load) }

                nothingToShow -> PullableCenter {
                    CenteredHint(text = emptyText(state, viewModel.favoritesOnly, viewModel.isAiResultsRoot))
                }

                nothingVisible -> PullableCenter { CenteredHint(text = "Seçili kategoriye uyan bir şey yok.") }

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
    AiSummaryDialogHost()
}

/** Ortalanmış bir içerik; kaydırılabilir olduğu için aşağı çekerek yenileme boş durumlarda da çalışır. */
@Composable
private fun PullableCenter(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() } }
    }
}

private fun emptyText(state: BrowserUiState, favoritesOnly: Boolean, aiResultsRoot: Boolean): String = when {
    aiResultsRoot -> "Henüz yapay zeka sonucu yok.\nBir dosyanın yanındaki yapay zeka düğmesiyle özet oluşturabilirsiniz."
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
    // AI düğmesi, özeti olan dosyalarda dolu görünür; özet hazırlanırken yerinde yükleme simgesi döner.
    val summarizing by AiSummarizer.running.collectAsState()
    val waitingUpload by SummaryStore.pendingIds.collectAsState() // telefonda üretilmiş, yüklenmeyi bekleyen özetler
    val summaryStatus by AiSummarizer.status.collectAsState()
    // İnternetsizken yalnızca telefonda kopyası olan dosyalar açılabilir; diğerleri pasif görünür.
    val availableOffline by produceState(emptySet<String>(), state.files, state.offline) {
        value = if (!state.offline) emptySet() else withContext(Dispatchers.IO) {
            state.files.filter { InputCache.get(it.id, it.size) != null }.map { it.id }.toSet()
        }
    }
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
                menuEnabled = !state.offline,
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
                    onToggleAi = { AiSummarizer.onAiClicked(file, hasSummary = file.hasSummary || file.id in waitingUpload) },
                    onDownload = { onDownload(file) },
                    onMove = { viewModel.openDialog(BrowserDialog.MoveFile(file)) },
                    onRename = { viewModel.openDialog(BrowserDialog.RenameFile(file)) },
                    onInfo = { viewModel.openDialog(BrowserDialog.FileInfo(file)) },
                    onDelete = { viewModel.openDialog(BrowserDialog.DeleteFile(file)) },
                ),
                ai = if (file.inAiResults) null else file.hasSummary || file.id in waitingUpload,
                aiWorking = file.id in summarizing,
                aiStatus = summaryStatus[file.id],
                enabled = !state.offline || file.id in availableOffline,
                menuEnabled = !state.offline,
                dimmed = dragState.dragged?.keys?.contains(rowKey) == true,
                selectionMode = selection.active,
                selected = rowKey in selection.keys,
                onOpen = { if (selection.active) viewModel.toggleSelection(rowKey) else onOpenFile(file) },
            )
        }
    }
}
