package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.TrashFileDto
import com.eayd.arkive.data.TrashFolderDto
import com.eayd.arkive.data.TrashIdsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrashUiState(
    val isLoading: Boolean = true,
    val files: List<TrashFileDto> = emptyList(),
    val folders: List<TrashFolderDto> = emptyList(),
    /** Sunucunun uyguladığı saklama süresi (gün). Bilgi metninde gösterilir. */
    val retentionDays: Int = 30,
    /** Çöpteki bir klasörün içi gösteriliyorsa o klasörün adı ve kalan günü (kök çöp kutusunda null). */
    val folderName: String? = null,
    val folderDaysLeft: Int? = null,
    /** Liste yüklenemedi: ekranın ortasında "Tekrar dene" ile gösterilir. */
    val errorMessage: String? = null,
    /** Kısa bildirim (Snackbar): işlem sonucu ya da işlem hatası. */
    val notice: String? = null,
    /** Seçim modu. Çöp kutusunda hiçbir şey seçili olmasa da mod açık kalabilir ("Seç" düğmesi). */
    val selection: Selection = Selection(),
    /** Geri yükleme / silme isteği sürüyor: düğmeler kilitlenir. */
    val busy: Boolean = false,
    /** "Kalıcı olarak sil" onay penceresi açık mı. */
    val confirmDelete: Boolean = false,
    /** "Tümünü sil" onay penceresi açık mı. */
    val confirmEmpty: Boolean = false,
) {
    val itemCount: Int get() = files.size + folders.size
    val allKeys: Set<String>
        get() = folders.map { folderKey(it.id) }.toSet() + files.map { fileKey(it.id) }.toSet()
    val allSelected: Boolean get() = itemCount > 0 && selection.count == itemCount
}

/**
 * Çöp kutusu ekranının durumu. Silinen öğeler sunucuda [TrashUiState.retentionDays] gün tutulur; süresi dolanları
 * sunucu kendisi kalıcı siler (GET /trash çağrısında da, arka planda da).
 *
 * [folderId] null ise çöp kutusunun kökü, doluysa çöpteki o klasörün içi gösterilir.
 */
class TrashViewModel(private val folderId: String? = null) : ViewModel() {

    /** true: çöp kutusunun kökü değil, çöpteki bir klasörün içi gösteriliyor. */
    val isFolderView: Boolean get() = folderId != null

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    init {
        fetch(showSpinner = true)
    }

    fun load() = fetch(showSpinner = true)

    // Ekran her görününce (ör. başka bölümden dönünce) bir kez sessizce yenilenir; ilk gösterim zaten init'te yüklendi.
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
        // Kökte: GET /trash. Klasör içinde: GET /trash/folders/{id} (klasörle birlikte silinenler).
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
            // Listeden kalkmış (geri yüklenmiş / süresi dolmuş) öğeler seçili kalmasın; liste boşaldıysa seçim modu kapanır.
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

    // --- Seçim ------------------------------------------------------------------------------------

    fun startSelecting() = _state.update { it.copy(selection = Selection().start()) }

    fun stopSelecting() = _state.update { it.copy(selection = Selection()) }

    fun toggle(key: String) = _state.update { it.copy(selection = it.selection.toggle(key)) }

    /** Bir öğeye uzun basınca seçim modu açılır ve o öğe seçilir; mod zaten açıksa öğe seçilir/bırakılır. */
    fun longPress(key: String) = _state.update {
        it.copy(selection = if (it.selection.active) it.selection.toggle(key) else Selection().start(key))
    }

    /** "Hepsi": hepsi seçiliyse seçimi temizler (mod açık kalır), değilse hepsini seçer. */
    fun toggleAll() = _state.update {
        it.copy(selection = it.selection.selectAll(if (it.allSelected) emptySet() else it.allKeys))
    }

    // --- İşlemler ---------------------------------------------------------------------------------

    fun askDeleteForever() = _state.update { if (it.selection.count > 0) it.copy(confirmDelete = true) else it }

    fun dismissDeleteConfirm() = _state.update { it.copy(confirmDelete = false) }

    // Çöp kutusunu boşaltma yalnızca kökte vardır (klasör içinde düğme gösterilmez).
    fun askEmptyTrash() = _state.update { if (!isFolderView && it.itemCount > 0 && !it.busy) it.copy(confirmEmpty = true) else it }

    fun dismissEmptyConfirm() = _state.update { it.copy(confirmEmpty = false) }

    /** Çöp kutusundaki HER ŞEYİ kalıcı siler (DELETE /trash). */
    fun emptyTrash() {
        if (isFolderView || _state.value.busy) return
        // "Kalıcı olarak sil"e basılır basılmaz onay penceresi kapanır; iş arka planda sürer, sonucu bildirim olarak gelir.
        _state.update { it.copy(busy = true, confirmEmpty = false) }
        launchCatching(
            onError = { message -> _state.update { it.copy(busy = false, notice = message) } },
        ) {
            ApiClient.api.emptyTrash()
            _state.update { it.copy(busy = false, selection = Selection(), notice = "Çöp kutusu boşaltıldı") }
            fetch(showSpinner = false)
        }
    }

    /** Seçilenleri özgün yerlerine geri yükler. */
    fun restoreSelected() = perform(
        call = { ApiClient.api.restoreTrash(it) },
        // Klasörün içinden geri yüklenen öğeler, klasör hâlâ çöpte olduğu için Ana Sayfa'nın en üstüne gelir.
        success = { n -> if (isFolderView) "$n öğe Ana Sayfa'ya geri yüklendi" else "$n öğe geri yüklendi" },
    )

    /** Seçilenleri KALICI siler. */
    fun deleteSelectedForever() = perform(
        call = { ApiClient.api.deleteTrash(it) },
        success = { n -> "$n öğe kalıcı olarak silindi" },
    )

    private fun perform(call: suspend (TrashIdsRequest) -> Unit, success: (Int) -> String) {
        val current = _state.value
        if (current.selection.count == 0 || current.busy) return
        val ids = TrashIdsRequest(fileIds = current.selection.fileIds, folderIds = current.selection.folderIds)
        // "Kalıcı olarak sil"e basılır basılmaz onay penceresi kapanır (confirmDelete = false); düğmeler busy ile kilitlenir.
        _state.update { it.copy(busy = true, confirmDelete = false) }
        launchCatching(
            onError = { message -> _state.update { it.copy(busy = false, notice = message) } },
        ) {
            call(ids)
            // Sunucunun döndürdüğü sayı klasörlerin içindekileri de kapsar; kullanıcıya seçtiği kadarını söyleriz.
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
