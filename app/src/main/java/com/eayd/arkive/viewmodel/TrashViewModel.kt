package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.TrashFileDto
import com.eayd.arkive.data.TrashFolderDto
import com.eayd.arkive.data.TrashIdsRequest
import com.eayd.arkive.i18n.Txt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrashUiState(
    val isLoading: Boolean = true,
    val files: List<TrashFileDto> = emptyList(),
    val folders: List<TrashFolderDto> = emptyList(),
    /** Sunucudaki saklama süresi (gün). */
    val retentionDays: Int = 30,
    /** Çöpteki bir klasörün içi gösteriliyorsa adı. */
    val folderName: String? = null,
    val folderDaysLeft: Int? = null,
    /** Liste yüklenemedi. */
    val errorMessage: String? = null,
    /** Kısa bildirim. */
    val notice: String? = null,
    /** Seçim modu; hiçbir şey seçili olmasa da açık kalabilir. */
    val selection: Selection = Selection(),
    /** Geri yükleme ya da silme sürüyor; düğmeler kilitli. */
    val busy: Boolean = false,
    /** "Kalıcı olarak sil" onayı açık mı. */
    val confirmDelete: Boolean = false,
    /** "Tümünü sil" onayı açık mı. */
    val confirmEmpty: Boolean = false,
) {
    val itemCount: Int get() = files.size + folders.size
    val allKeys: Set<String>
        get() = folders.map { folderKey(it.id) }.toSet() + files.map { fileKey(it.id) }.toSet()
    val allSelected: Boolean get() = itemCount > 0 && selection.count == itemCount
}

/** Çöp kutusu ekranının durumu. [folderId] verilirse çöpteki o klasörün içi gösterilir. */
class TrashViewModel(private val folderId: String? = null) : ViewModel() {

    /** Çöpteki bir klasörün içi mi gösteriliyor. */
    val isFolderView: Boolean get() = folderId != null

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    init {
        fetch(showSpinner = true)
    }

    fun load() = fetch(showSpinner = true)

    private var skipNextShowRefresh = true

    fun onShown() {
        if (skipNextShowRefresh) skipNextShowRefresh = false else fetch(showSpinner = false)
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    private class Loaded(
        val folders: List<TrashFolderDto>,
        val files: List<TrashFileDto>,
        val retentionDays: Int,
        val folder: TrashFolderDto?,
    )

    private suspend fun reload() {
        val loaded = folderId?.let { id ->
            ApiClient.api.getTrashFolder(id).let { Loaded(it.folders, it.files, it.retentionDays, it.folder) }
        } ?: ApiClient.api.getTrash().let { Loaded(it.folders, it.files, it.retentionDays, null) }

        _state.update { old ->
            val next = old.copy(
                isLoading = false,
                errorMessage = null,
                folders = loaded.folders.sortedByDescending { it.deletedAt },
                files = loaded.files.sortedByDescending { it.deletedAt },
                retentionDays = loaded.retentionDays,
                folderName = loaded.folder?.name,
                folderDaysLeft = loaded.folder?.daysLeft,
            )
            val kept = old.selection.retain(next.allKeys)
            next.copy(
                selection = if (next.itemCount == 0) Selection() else kept,
                confirmDelete = old.confirmDelete && kept.count > 0,
            )
        }
    }

    private fun fetch(showSpinner: Boolean) {
        if (showSpinner) _state.update { it.copy(isLoading = true, errorMessage = null) }
        launchCatching(
            onError = { message ->
                _state.update {
                    if (showSpinner || it.itemCount == 0) it.copy(isLoading = false, errorMessage = message)
                    else it.copy(notice = message)
                }
            },
        ) { reload() }
    }

    fun startSelecting() = _state.update { it.copy(selection = Selection().start()) }

    fun stopSelecting() = _state.update { it.copy(selection = Selection()) }

    fun toggle(key: String) = _state.update { it.copy(selection = it.selection.toggle(key)) }

    /** Uzun basınca seçim modu açılır ve öğe seçilir. */
    fun longPress(key: String) = _state.update {
        it.copy(selection = if (it.selection.active) it.selection.toggle(key) else Selection().start(key))
    }

    /** Hepsi seçiliyse seçimi temizler, değilse hepsini seçer. */
    fun toggleAll() = _state.update {
        it.copy(selection = it.selection.selectAll(if (it.allSelected) emptySet() else it.allKeys))
    }

    fun askDeleteForever() = _state.update { if (it.selection.count > 0) it.copy(confirmDelete = true) else it }

    fun dismissDeleteConfirm() = _state.update { it.copy(confirmDelete = false) }

    fun askEmptyTrash() = _state.update { if (!isFolderView && it.itemCount > 0 && !it.busy) it.copy(confirmEmpty = true) else it }

    fun dismissEmptyConfirm() = _state.update { it.copy(confirmEmpty = false) }

    /** Çöp kutusunu kalıcı olarak boşaltır. */
    fun emptyTrash() {
        if (isFolderView || _state.value.busy) return
        _state.update { it.copy(busy = true, confirmEmpty = false) }
        launchCatching(
            onError = { message -> _state.update { it.copy(busy = false, notice = message) } },
        ) {
            ApiClient.api.emptyTrash()
            _state.update { it.copy(busy = false, selection = Selection(), notice = Txt.trash.emptied) }
            fetch(showSpinner = false)
        }
    }

    /** Seçilenleri eski yerlerine geri yükler. */
    fun restoreSelected() = perform(
        call = { ApiClient.api.restoreTrash(it) },
        success = { n -> if (isFolderView) Txt.trash.restoredToHome(n) else Txt.trash.restored(n) },
    )

    /** Seçilenleri kalıcı olarak siler. */
    fun deleteSelectedForever() = perform(
        call = { ApiClient.api.deleteTrash(it) },
        success = { n -> Txt.trash.deletedPermanently(n) },
    )

    private fun perform(call: suspend (TrashIdsRequest) -> Unit, success: (Int) -> String) {
        val current = _state.value
        if (current.selection.count == 0 || current.busy) return
        val ids = TrashIdsRequest(fileIds = current.selection.fileIds, folderIds = current.selection.folderIds)
        _state.update { it.copy(busy = true, confirmDelete = false) }
        launchCatching(
            onError = { message -> _state.update { it.copy(busy = false, notice = message) } },
        ) {
            call(ids)
            val chosen = ids.fileIds.size + ids.folderIds.size
            _state.update {
                it.copy(busy = false, selection = Selection(), notice = success(chosen))
            }
            fetch(showSpinner = false)
        }
    }

    companion object {
        fun factory(folderId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { TrashViewModel(folderId) }
        }
    }
}
