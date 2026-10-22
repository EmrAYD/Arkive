package com.eayd.arkive.screens.folders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.eayd.arkive.ui.components.ConfirmDialog
import com.eayd.arkive.ui.components.FileInfoDialog
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
            title = "Yeni klasör",
            confirmLabel = "Oluştur",
            label = "Klasör adı",
            initialName = "",
            saving = host.saving,
            serverError = host.error,
            onConfirm = viewModel::createFolder,
            onDismiss = close,
        )

        is BrowserDialog.EditFolder -> NameDialog(
            title = "Klasörü düzenle",
            confirmLabel = "Kaydet",
            label = "Klasör adı",
            initialName = dialog.folder.name,
            initialColor = dialog.folder.color,
            saving = host.saving,
            serverError = host.error,
            onConfirm = { name, color -> viewModel.editFolder(dialog.folder, name, color) },
            onDismiss = close,
        )

        is BrowserDialog.RenameFile -> NameDialog(
            title = "Yeniden adlandır",
            confirmLabel = "Kaydet",
            label = "Dosya adı",
            initialName = dialog.file.name,
            showColor = false,
            saving = host.saving,
            serverError = host.error,
            onConfirm = { name, _ -> viewModel.renameFile(dialog.file, name) },
            onDismiss = close,
        )

        is BrowserDialog.DeleteFolder -> ConfirmDialog(
            title = "Klasör çöp kutusuna taşınsın mı?",
            message = "“${dialog.folder.name}” klasörü, içindeki alt klasörler ve dosyalarla birlikte çöp kutusuna " +
                "taşınacak. Çöp kutusundan geri yükleyebilirsiniz.",
            confirmLabel = "Çöp kutusuna taşı",
            saving = host.saving,
            error = host.error,
            onConfirm = { viewModel.deleteFolder(dialog.folder) },
            onDismiss = close,
        )

        is BrowserDialog.DeleteFile -> ConfirmDialog(
            title = "Dosya çöp kutusuna taşınsın mı?",
            message = "“${dialog.file.name}” çöp kutusuna taşınacak. Çöp kutusundan geri yükleyebilirsiniz.",
            confirmLabel = "Çöp kutusuna taşı",
            saving = host.saving,
            error = host.error,
            onConfirm = { viewModel.deleteFile(dialog.file) },
            onDismiss = close,
        )

        BrowserDialog.DeleteSelection -> ConfirmDialog(
            title = "${state.selection.count} öğe çöp kutusuna taşınsın mı?",
            message = "Seçilen öğeler çöp kutusuna taşınacak." +
                (if (state.selectedFolders.isNotEmpty()) " Klasörlerin içindekiler de klasörle birlikte taşınır." else "") +
                " Çöp kutusundan geri yükleyebilirsiniz.",
            confirmLabel = "Çöp kutusuna taşı",
            saving = host.saving,
            error = host.error,
            onConfirm = viewModel::confirmDeleteSelection,
            onDismiss = close,
        )

        is BrowserDialog.MoveFile -> MoveFolderDialog(
            summary = "“${dialog.file.name}”",
            currentParentIds = listOf(dialog.file.folderId),
            excludedFolderIds = emptySet(),
            moving = host.saving,
            error = host.error,
            onMove = viewModel::confirmMove,
            onDismiss = close,
        )

        is BrowserDialog.MoveFolder -> MoveFolderDialog(
            summary = "“${dialog.folder.name}”",
            currentParentIds = listOf(dialog.folder.parentId),
            // Klasör kendi içine taşınamaz: listede gösterilmez (alt klasörlerine de ancak içinden girilir).
            excludedFolderIds = setOf(dialog.folder.id),
            moving = host.saving,
            error = host.error,
            onMove = viewModel::confirmMove,
            onDismiss = close,
        )

        BrowserDialog.MoveSelection -> MoveFolderDialog(
            summary = "${state.selection.count} öğe",
            currentParentIds = state.selectedFiles.map { it.folderId } + state.selectedFolders.map { it.parentId },
            // Taşınan klasörler listede gösterilmez: bir klasör kendi içine taşınamaz.
            excludedFolderIds = state.selectedFolders.map { it.id }.toSet(),
            moving = host.saving,
            error = host.error,
            onMove = viewModel::confirmMove,
            onDismiss = close,
        )

        is BrowserDialog.FileInfo -> FileInfoDialog(file = dialog.file, storageLimit = storageLimit, onDismiss = close)
    }
}
