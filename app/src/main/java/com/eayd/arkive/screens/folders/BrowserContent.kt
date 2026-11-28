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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.ai.InputCache
import com.eayd.arkive.ai.SummaryDone
import com.eayd.arkive.ai.SummaryStore
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.components.AiIcons
import com.eayd.arkive.ui.components.CenteredHint
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.LeaveEffect
import com.eayd.arkive.ui.components.ListSkeleton
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.NoticeHost
import com.eayd.arkive.ui.components.rememberNoticeHostState
import com.eayd.arkive.ui.components.SummaryDoneBanner
import com.eayd.arkive.ui.components.SwipeAction
import com.eayd.arkive.ui.components.SwipeActions
import com.eayd.arkive.ui.components.SwipeKind
import com.eayd.arkive.ui.components.rememberFileDownloader
import com.eayd.arkive.viewmodel.BrowserDialog
import com.eayd.arkive.viewmodel.BrowserUiState
import com.eayd.arkive.viewmodel.BrowserViewModel
import com.eayd.arkive.viewmodel.fileKey
import com.eayd.arkive.viewmodel.folderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Klasör ve dosya listesi, üstündeki işlem satırı ve süren yüklemeler.
 *
 * Ana sayfa, Favorilerim ve klasör içi bu bileşeni kullanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserContent(
    viewModel: BrowserViewModel,
    onOpenFolder: (FolderDto) -> Unit,
    onOpenFile: (FileDto) -> Unit,
    modifier: Modifier = Modifier,
    /** false ise sayfa görünmüyor; seçim modu kapatılır. */
    active: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    val selection = state.selection
    val notices = rememberNoticeHostState()
    val context = LocalContext.current
    val startDownload = rememberFileDownloader(viewModel::showNotice)
    val listState = rememberLazyListState()
    val dragState = remember { FileDragState() }

    LaunchedEffect(viewModel) { viewModel.onShown() }
    LeaveEffect(viewModel) { viewModel.clearSelection() }
    LaunchedEffect(active) { if (!active) viewModel.clearSelection() }
    LaunchedEffect(viewModel) { AiSummarizer.notices.collect(viewModel::showNotice) }
    val finished = remember { mutableStateListOf<SummaryDone>() }
    LaunchedEffect(viewModel) { AiSummarizer.done.collect { finished.add(it) } }
    LaunchedEffect(viewModel) { AiSummarizer.completed.collect { viewModel.reloadQuietly() } }
    LaunchedEffect(viewModel) { AiSummarizer.flushPending() }
    LaunchedEffect(state.files, state.offline) { if (!state.offline) AiSummarizer.prefetch(state.files) }
    val offlineNow by rememberUpdatedState(state.offline)
    LaunchedEffect(viewModel) { AiSummarizer.networkBack.collect { if (offlineNow) viewModel.reloadQuietly() } }
    NoticeEffect(state.notice, notices, viewModel::clearNotice)

    BackHandler(enabled = selection.active, onBack = viewModel::clearSelection)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.uploadFiles(context.applicationContext.contentResolver, uris)
    }

    Column(modifier.fillMaxSize()) {
        if (selection.active) {
            SelectionBar(
                count = selection.count,
                allFavorite = state.selectedItems.allFavorite,
                canMove = !viewModel.favoritesOnly && state.moveLocation != null,
                onClose = viewModel::clearSelection,
                onSelectAll = viewModel::selectAllVisible,
                onFavorite = viewModel::favoriteSelection,
                onMove = { viewModel.openDialog(BrowserDialog.MoveSelection) },
                onDelete = { viewModel.openDialog(BrowserDialog.DeleteSelection) },
            )
        } else {
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
                text = Txt.browser.offlineBanner,
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
                    onLongPress = { key -> viewModel.longPress(key).takeUnless { viewModel.favoritesOnly } },
                    onDrop = viewModel::dropItems,
                )
        ) {
            val nothingToShow = state.folders.isEmpty() && state.files.isEmpty() && state.uploads.isEmpty()
            val nothingVisible = state.visibleFolders.isEmpty() && state.visibleFiles.isEmpty() && state.uploads.isEmpty()
            val error = state.errorMessage
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
            when {
                state.isLoading && nothingToShow -> ListSkeleton(Modifier.fillMaxSize())

                error != null -> PullableCenter { ErrorRetry(error, viewModel::load) }

                nothingToShow -> PullableCenter {
                    CenteredHint(text = emptyText(state, viewModel.favoritesOnly))
                }

                nothingVisible -> PullableCenter { CenteredHint(text = Txt.browser.noCategoryMatch) }

                else -> BrowserList(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    dragState = dragState,
                    onOpenFolder = onOpenFolder,
                    onOpenFile = onOpenFile,
                    onDownload = { file ->
                        startDownload(file.id, file.name, file.mimeType)
                    },
                )
            }
            }

            AutoScrollWhileDragging(dragState, listState)
            DragGhost(dragState)

            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                NoticeHost(notices)
                SummaryDoneBanner(
                    done = finished.firstOrNull(),
                    onOpen = { done ->
                        finished.remove(done)
                        AiSummarizer.openSummary(done.file)
                    },
                    onDismiss = { done -> finished.remove(done) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }

    BrowserDialogs(viewModel)
}

/** Ortalanmış içerik; boş listede de aşağı çekerek yenilemeye izin verir. */
@Composable
private fun PullableCenter(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() } }
    }
}

private fun emptyText(state: BrowserUiState, favoritesOnly: Boolean): String = when {
    favoritesOnly -> Txt.browser.emptyFavorites
    state.canUpload -> Txt.browser.emptyHome
    else -> Txt.browser.emptyFolders
}

/**
 * Klasör ve dosya satırları.
 *
 * Satır sağa kaydırılınca silme onayı açılır, dosya satırı sola kaydırılınca özetlenir (özeti varsa gösterilir).
 */
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
    val summarizing by AiSummarizer.running.collectAsState()
    val waitingUpload by SummaryStore.pendingIds.collectAsState()
    val summaryStatus by AiSummarizer.status.collectAsState()
    val summaryProgress by AiSummarizer.progress.collectAsState()
    val availableOffline by produceState(emptySet<String>(), state.files, state.offline) {
        value = if (!state.offline) emptySet() else withContext(Dispatchers.IO) {
            state.files.filter { InputCache.get(it.id, it.size) != null }.map { it.id }.toSet()
        }
    }
    val swipeEnabled = !selection.active && dragState.dragged == null
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.uploads, key = { "upload-${it.id}" }) { upload ->
            UploadRow(
                item = upload,
                onCancel = { viewModel.cancelUpload(upload.id) },
                onDismiss = { viewModel.dismissUpload(upload.id) },
            )
        }
        items(state.visibleFolders, key = { folderKey(it.id) }) { folder ->
            val rowKey = folderKey(folder.id)
            val rowModifier = Modifier
                .trackBounds(dragState.itemCoords, rowKey)
                .trackDropFolder(dragState, folder.id, folder.location)
            SwipeActions(
                toEnd = deleteSwipe { viewModel.openDialog(BrowserDialog.DeleteFolder(folder)) },
                toStart = null,
                enabled = swipeEnabled && !state.offline,
                modifier = rowModifier,
            ) {
                FolderRow(
                    folder = folder,
                    actions = FolderRowActions(
                        onToggleFavorite = { viewModel.toggleFavorite(folder) },
                        onEdit = { viewModel.openDialog(BrowserDialog.EditFolder(folder)) },
                        onMove = if (viewModel.favoritesOnly) null else ({ viewModel.openDialog(BrowserDialog.MoveFolder(folder)) }),
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
        }
        items(state.visibleFiles, key = { fileKey(it.id) }) { file ->
            val rowKey = fileKey(file.id)
            val hasSummary = file.hasSummary || file.id in waitingUpload
            val rowEnabled = !state.offline || file.id in availableOffline
            SwipeActions(
                toEnd = if (state.offline) null else deleteSwipe { viewModel.openDialog(BrowserDialog.DeleteFile(file)) },
                toStart = if (file.id in summarizing) null else SwipeAction(
                    label = if (hasSummary) Txt.ai.showSummary else Txt.ai.summarize,
                    icon = AiIcons.Filled,
                    kind = SwipeKind.Primary,
                ) { AiSummarizer.onAiClicked(file, hasSummary = hasSummary) },
                enabled = swipeEnabled && rowEnabled,
                modifier = Modifier.trackBounds(dragState.itemCoords, rowKey),
            ) {
                FileRow(
                    file = file,
                    actions = FileRowActions(
                        onToggleFavorite = { viewModel.toggleFavorite(file) },
                        onToggleAi = { AiSummarizer.onAiClicked(file, hasSummary = hasSummary) },
                        onDownload = { onDownload(file) },
                        onMove = if (viewModel.favoritesOnly) null else ({ viewModel.openDialog(BrowserDialog.MoveFile(file)) }),
                        onRename = { viewModel.openDialog(BrowserDialog.RenameFile(file)) },
                        onInfo = { viewModel.openDialog(BrowserDialog.FileInfo(file)) },
                        onDelete = { viewModel.openDialog(BrowserDialog.DeleteFile(file)) },
                    ),
                    ai = hasSummary,
                    aiWorking = file.id in summarizing,
                    aiStatus = summaryProgress[file.id]?.let { Txt.ai.progressPercent(it) } ?: summaryStatus[file.id],
                    enabled = rowEnabled,
                    menuEnabled = !state.offline,
                    dimmed = dragState.dragged?.keys?.contains(rowKey) == true,
                    selectionMode = selection.active,
                    selected = rowKey in selection.keys,
                    onOpen = { if (selection.active) viewModel.toggleSelection(rowKey) else onOpenFile(file) },
                )
            }
        }
    }
}

/** Sağa kaydırınca silme onayını açan işlem. */
private fun deleteSwipe(onSwipe: () -> Unit) =
    SwipeAction(label = Txt.common.delete, icon = Icons.Filled.Delete, kind = SwipeKind.Destructive, onSwipe = onSwipe)
