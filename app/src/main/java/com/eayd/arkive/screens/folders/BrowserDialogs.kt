package com.eayd.arkive.screens.folders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.components.ConfirmDialog
import com.eayd.arkive.ui.components.FileInfoDialog
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.ui.components.MoveFolderDialog
import com.eayd.arkive.ui.components.NameDialog
import com.eayd.arkive.viewmodel.BrowserDialog
import com.eayd.arkive.viewmodel.BrowserViewModel

/**
 * Liste ekranında açık olan pencere. Hangi pencerenin açık olduğu, "kaydediliyor" ve sunucu hatası
 * [BrowserViewModel.dialogs] içindedir; işlemler de ViewModel'de çalışır. Burası yalnızca çizer.
 */
@Composable
internal fun BrowserDialogs(viewModel: BrowserViewModel) {
    val host by viewModel.dialogs.state.collectAsState()
    val state by viewModel.state.collectAsState()
    val storageLimit by viewModel.storageLimit.collectAsState()
    val close = viewModel.dialogs::close

    when (val dialog = host.current) {
        null -> Unit

        BrowserDialog.NewFolder -> NameDialog(
            title = Txt.browser.newFolder,
            confirmLabel = Txt.common.create,
            label = Txt.browser.folderName,
            initialName = "",
            saving = host.saving,
            serverError = host.error,
            onConfirm = viewModel::createFolder,
            onDismiss = close,
        )

        is BrowserDialog.EditFolder -> NameDialog(
            title = Txt.browser.editFolder,
            confirmLabel = Txt.common.save,
            label = Txt.browser.folderName,
            initialName = dialog.folder.name,
            initialColor = dialog.folder.color,
            initialEmoji = dialog.folder.emoji,
            saving = host.saving,
            serverError = host.error,
            onConfirm = { name, color, emoji -> viewModel.editFolder(dialog.folder, name, color, emoji) },
            onDismiss = close,
        )

        is BrowserDialog.RenameFile -> NameDialog(
            title = Txt.common.rename,
            confirmLabel = Txt.common.save,
            label = Txt.browser.fileName,
            initialName = dialog.file.name,
            showColor = false,
            saving = host.saving,
            serverError = host.error,
            onConfirm = { name, _, _ -> viewModel.renameFile(dialog.file, name) },
            onDismiss = close,
        )

        is BrowserDialog.DeleteFolder -> ConfirmDialog(
            title = Txt.browser.folderToTrashTitle,
            message = Txt.browser.folderToTrashBody(dialog.folder.name),
            confirmLabel = Txt.browser.moveToTrash,
            saving = host.saving,
            error = host.error,
            onConfirm = { viewModel.deleteFolder(dialog.folder) },
            onDismiss = close,
        )

        is BrowserDialog.DeleteFile -> ConfirmDialog(
            title = Txt.browser.fileToTrashTitle,
            message = Txt.browser.fileToTrashBody(dialog.file.name),
            confirmLabel = Txt.browser.moveToTrash,
            saving = host.saving,
            error = host.error,
            onConfirm = { viewModel.deleteFile(dialog.file) },
            onDismiss = close,
        )

        BrowserDialog.DeleteSelection -> ConfirmDialog(
            title = Txt.browser.itemsToTrashTitle(state.selection.count),
            message = Txt.browser.itemsToTrashBody +
                (if (state.selectedFolders.isNotEmpty()) Txt.browser.itemsToTrashFolders else "") +
                Txt.browser.itemsToTrashRestore,
            confirmLabel = Txt.browser.moveToTrash,
            saving = host.saving,
            error = host.error,
            onConfirm = viewModel::confirmDeleteSelection,
            onDismiss = close,
        )

        is BrowserDialog.MoveFile -> MoveFolderDialog(
            summary = Txt.browser.quoted(dialog.file.name),
            currentParentIds = listOf(dialog.file.folderId),
            excludedFolderIds = emptySet(),
            moving = host.saving,
            error = host.error,
            onMove = viewModel::confirmMove,
            onDismiss = close,
            location = if (dialog.file.inAiResults) FolderLocations.AI_RESULTS else FolderLocations.HOME,
        )

        is BrowserDialog.MoveFolder -> MoveFolderDialog(
            summary = Txt.browser.quoted(dialog.folder.name),
            currentParentIds = listOf(dialog.folder.parentId),
            // Klasör kendi içine taşınamaz: listede gösterilmez (alt klasörlerine de ancak içinden girilir).
            excludedFolderIds = setOf(dialog.folder.id),
            moving = host.saving,
            error = host.error,
            onMove = viewModel::confirmMove,
            onDismiss = close,
        )

        BrowserDialog.MoveSelection -> MoveFolderDialog(
            summary = Txt.browser.itemCount(state.selection.count),
            currentParentIds = state.selectedFiles.map { it.folderId } + state.selectedFolders.map { it.parentId },
            // Taşınan klasörler listede gösterilmez: bir klasör kendi içine taşınamaz.
            excludedFolderIds = state.selectedFolders.map { it.id }.toSet(),
            moving = host.saving,
            error = host.error,
            onMove = viewModel::confirmMove,
            onDismiss = close,
            location = state.moveLocation ?: FolderLocations.HOME,
        )

        is BrowserDialog.FileInfo -> FileInfoDialog(file = dialog.file, storageLimit = storageLimit, onDismiss = close)
    }
}
