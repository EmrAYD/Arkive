package com.eayd.arkive.screens.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.components.ConfirmDialog
import com.eayd.arkive.ui.components.ErrorRetry
import com.eayd.arkive.ui.components.FileActionsMenu
import com.eayd.arkive.ui.components.FileInfoDialog
import com.eayd.arkive.ui.components.MoveFolderDialog
import com.eayd.arkive.ui.components.NameDialog
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.NoticeHost
import com.eayd.arkive.ui.components.rememberNoticeHostState
import com.eayd.arkive.ui.components.PasswordDialog
import com.eayd.arkive.ui.components.SkeletonBox
import com.eayd.arkive.ui.components.TrashedFileActionsMenu
import com.eayd.arkive.ui.components.rememberFileDownloader
import com.eayd.arkive.viewmodel.PreviewContent
import com.eayd.arkive.viewmodel.PreviewDialog
import com.eayd.arkive.viewmodel.PreviewState
import com.eayd.arkive.viewmodel.PreviewViewModel

/** Dosya ön izleme ekranı. [readOnly] true ise dosya değiştirilemez (çöpteki dosyalar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(fileId: String, onBack: () -> Unit, readOnly: Boolean = false) {
    val context = LocalContext.current
    val viewModel: PreviewViewModel = viewModel(
        key = "preview-$fileId",
        factory = PreviewViewModel.factory(fileId, context.applicationContext.cacheDir),
    )
    val state by viewModel.state.collectAsState()
    val title by viewModel.title.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val notices = rememberNoticeHostState()
    var menuOpen by remember { mutableStateOf(false) }

    NoticeEffect(notice, notices, viewModel::clearNotice)
    LaunchedEffect(deleted) { if (deleted) onBack() }

    val startDownload = rememberFileDownloader(viewModel::showNotice)
    val download = {
        (state as? PreviewState.Ready)?.file?.let { f -> startDownload(f.id, f.name, f.mimeType) }
        Unit
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopAppBar(
            title = { Text(title ?: Txt.preview.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Txt.common.back)
                }
            },
            actions = {
                if (state is PreviewState.Ready) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = Txt.common.more)
                        }
                        if (readOnly) {
                            TrashedFileActionsMenu(
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                onDownload = download,
                                onInfo = { viewModel.openDialog(PreviewDialog.Info) },
                            )
                        } else {
                            FileActionsMenu(
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                onDownload = download,
                                onMove = { viewModel.openDialog(PreviewDialog.Move) },
                                onRename = { viewModel.openDialog(PreviewDialog.Rename) },
                                onInfo = { viewModel.openDialog(PreviewDialog.Info) },
                                onDelete = { viewModel.openDialog(PreviewDialog.Delete) },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                is PreviewState.Loading -> PreviewSkeleton(s.progress)

                is PreviewState.Error -> ErrorRetry(s.message, viewModel::load, Modifier.align(Alignment.Center))

                is PreviewState.Ready -> PreviewContentView(
                    s.file, s.content, download,
                    onEnterPassword = { viewModel.openDialog(PreviewDialog.Password) },
                )
            }

            NoticeHost(notices, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }

    (state as? PreviewState.Ready)?.file?.let { PreviewDialogs(viewModel, it) }
}

/** Dosya türüne uygun görüntüleyiciyi seçer. */
@Composable
private fun PreviewContentView(file: FileDto, content: PreviewContent, download: () -> Unit, onEnterPassword: () -> Unit) {
    when (content) {
        is PreviewContent.Image -> ZoomableImage(content.bitmap, file.name)
        is PreviewContent.Pdf -> PdfViewer(content.doc)
        is PreviewContent.Text -> TextViewer(content.text, content.truncated)
        is PreviewContent.Word -> WordViewer(content.doc)
        is PreviewContent.Media ->
            if (content.isAudio) AudioPlayer(content.url, file, download)
            else VideoPlayer(content.url, file, download)
        is PreviewContent.PasswordProtected -> PasswordProtectedMessage(file, onEnterPassword, download)
        is PreviewContent.NoPreview -> NoPreviewMessage(file, content.reason, download)
    }
}

/** 3 nokta menüsünden açılan pencereler. */
@Composable
private fun PreviewDialogs(viewModel: PreviewViewModel, file: FileDto) {
    val host by viewModel.dialogs.state.collectAsState()
    val storageLimit by viewModel.storageLimit.collectAsState()
    val close = viewModel.dialogs::close

    when (host.current) {
        null -> Unit

        PreviewDialog.Rename -> NameDialog(
            title = Txt.common.rename,
            confirmLabel = Txt.common.save,
            label = Txt.browser.fileName,
            initialName = file.name,
            showColor = false,
            saving = host.saving,
            serverError = host.error,
            onConfirm = { name, _, _ -> viewModel.renameFile(name) },
            onDismiss = close,
        )

        PreviewDialog.Move -> MoveFolderDialog(
            summary = Txt.browser.quoted(file.name),
            currentParentIds = listOf(file.folderId),
            excludedFolderIds = emptySet(),
            moving = host.saving,
            error = host.error,
            onMove = viewModel::moveFile,
            onDismiss = close,
        )

        PreviewDialog.Info -> FileInfoDialog(file = file, storageLimit = storageLimit, onDismiss = close)

        PreviewDialog.Password -> PasswordDialog(
            title = Txt.ai.passwordTitle,
            message = Txt.preview.passwordBody(file.name),
            note = Txt.preview.passwordNote,
            confirmLabel = Txt.common.open,
            busy = host.saving,
            error = host.error,
            onConfirm = viewModel::unlock,
            onDismiss = close,
        )

        PreviewDialog.Delete -> ConfirmDialog(
            title = Txt.browser.fileToTrashTitle,
            message = Txt.browser.fileToTrashBody(file.name),
            confirmLabel = Txt.browser.moveToTrash,
            saving = host.saving,
            error = host.error,
            onConfirm = viewModel::deleteFile,
            onDismiss = close,
        )
    }
}

/** Ön izleme hazırlanırken gösterilen iskelet; dosya iniyorsa yüzde görünür. */
@Composable
private fun PreviewSkeleton(progress: Float?) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
        if (progress != null) Text(Txt.preview.percent((progress * 100).toInt()), style = MaterialTheme.typography.titleMedium)
    }
}
