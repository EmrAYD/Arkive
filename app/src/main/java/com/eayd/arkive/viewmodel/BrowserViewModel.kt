package com.eayd.arkive.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.CreateFolderRequest
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FileUploader
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.data.ListCache
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.toApiErrorMessage
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Yüklemenin aşaması. */
enum class UploadPhase { Waiting, Uploading, Finishing, Failed }

/** Listede gösterilen süren ya da başarısız yükleme. */
data class UploadItem(
    val id: Long,
    val name: String,
    val progress: Float = 0f,
    val phase: UploadPhase = UploadPhase.Waiting,
    val error: String? = null,
)

/** Liste ekranının pencereleri; aynı anda en çok biri açık olur. */
sealed interface BrowserDialog {
    data object NewFolder : BrowserDialog
    data class EditFolder(val folder: FolderDto) : BrowserDialog
    data class DeleteFolder(val folder: FolderDto) : BrowserDialog
    data class RenameFile(val file: FileDto) : BrowserDialog
    data class DeleteFile(val file: FileDto) : BrowserDialog
    data class MoveFile(val file: FileDto) : BrowserDialog
    data class MoveFolder(val folder: FolderDto) : BrowserDialog
    data class FileInfo(val file: FileDto) : BrowserDialog
    data object MoveSelection : BrowserDialog
    data object DeleteSelection : BrowserDialog
}

/** Liste ekranının durumu. */
data class BrowserUiState(
    val isLoading: Boolean = true,
    /** Aşağı çekerek yenileme sürüyor. */
    val refreshing: Boolean = false,
    /** Sunucudan gelen listeler; ekranda süzülmüş halleri çizilir. */
    val folders: List<FolderDto> = emptyList(),
    val files: List<FileDto> = emptyList(),
    val visibleFolders: List<FolderDto> = emptyList(),
    val visibleFiles: List<FileDto> = emptyList(),
    val sort: SortOption = SortOption.NameAsc,
    val category: CategoryOption = CategoryOption.All,
    val selection: Selection = Selection(),
    val uploads: List<UploadItem> = emptyList(),
    /** İçinde bulunulan klasörün adı. */
    val title: String? = null,
    /** Buraya dosya yüklenebilir mi. */
    val canUpload: Boolean = false,
    /** Sunucuya ulaşılamadı; telefondaki son liste gösteriliyor. */
    val offline: Boolean = false,
    /** Liste yüklenemedi. */
    val errorMessage: String? = null,
    /** Kısa bildirim. */
    val notice: String? = null,
) {
    val selectedFiles: List<FileDto> get() = files.filter { fileKey(it.id) in selection.keys }
    val selectedFolders: List<FolderDto> get() = folders.filter { folderKey(it.id) in selection.keys }
    val selectedItems: SelectedItems get() = SelectedItems(selectedFiles, selectedFolders)

    /** Seçimin taşınabileceği sayfa; seçim boşsa null. */
    val moveLocation: String? get() = if (selection.keys.isEmpty()) null else FolderLocations.HOME
}

/** Bir sayfanın ya da klasörün içindeki klasörleri ve dosyaları yönetir: yükleme, seçim, filtreler ve pencereler. */
class BrowserViewModel(
    val parentId: String?,
    private val location: String?,
    val favoritesOnly: Boolean = false,
) : ViewModel() {

    /** Bu ekranın telefondaki liste kaydının adı. */
    private val cacheKey: String = when {
        favoritesOnly -> "favorites"
        parentId != null -> "folder_$parentId"
        else -> "root_$location"
    }

    private val canUploadHere = !favoritesOnly && (parentId != null || location == FolderLocations.HOME)

    private val _state = MutableStateFlow(BrowserUiState(canUpload = canUploadHere))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    /** Açık pencere ve kaydetme durumu. */
    val dialogs = DialogHost<BrowserDialog>(viewModelScope)

    private val storage = StorageLimitLoader(viewModelScope)

    /** Hesabın toplam kotası; yüklenene kadar null. */
    val storageLimit: StateFlow<Long?> = storage.limit

    private val uploadLock = Mutex()
    private val uploadJobs = mutableMapOf<Long, Job>()
    private var nextUploadId = 0L

    init {
        fetch(showSpinner = true)
    }

    private var skipNextShowRefresh = true

    fun onShown() {
        if (skipNextShowRefresh) skipNextShowRefresh = false else fetch(showSpinner = false)
    }

    /** Listeyi yükleme çarkı göstermeden yeniler. */
    fun reloadQuietly() = fetch(showSpinner = false)

    /** Listeyi baştan yükler. */
    fun load() = fetch(showSpinner = true)

    fun clearNotice() = edit { it.copy(notice = null) }

    fun showNotice(message: String) {
        edit { it.copy(notice = message) }
    }

    /** Durumu değiştirir; liste ya da filtre değiştiyse ekrandaki süzülmüş listeleri yeniden hesaplar. */
    private fun edit(transform: (BrowserUiState) -> BrowserUiState): BrowserUiState = _state.updateAndGet { old ->
        val new = transform(old)
        if (new.folders === old.folders && new.files === old.files &&
            new.sort == old.sort && new.category == old.category
        ) {
            new
        } else {
            new.copy(
                visibleFolders = if (new.category == CategoryOption.All) new.folders.sortedFolders(new.sort) else emptyList(),
                visibleFiles = new.files.filter { new.category.matches(it.mimeType) }.sortedFiles(new.sort),
            )
        }
    }

    /** Klasörleri ve dosyaları sunucudan çeker. */
    private suspend fun reload() {
        val loaded = try {
            download()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!showOffline(e)) throw e
            return
        }
        withContext(Dispatchers.IO) { ListCache.save(cacheKey, loaded) }
        apply(loaded, offline = false)
    }

    /** İnternet yoksa ekrandaki ya da telefondaki son listeyi gösterir. Ağ hatası değilse false döner. */
    private suspend fun showOffline(error: Throwable): Boolean {
        if (error !is IOException) return false
        val current = _state.value
        if (!current.isLoading && (current.files.isNotEmpty() || current.folders.isNotEmpty())) {
            edit { it.copy(offline = true, canUpload = false) }
            return true
        }
        val snapshot = withContext(Dispatchers.IO) { ListCache.load(cacheKey) } ?: return false
        apply(snapshot, offline = true)
        return true
    }

    private suspend fun download(): ListCache.Snapshot = coroutineScope {
        val folderD = async { parentId?.let { ApiClient.api.getFolder(it) } }
        val foldersD = async {
            ApiClient.api.getFolders(
                parentId = parentId,
                location = if (parentId == null && !favoritesOnly) location else null,
                favorite = if (favoritesOnly) true else null,
            )
        }
        val filesD = async {
            ApiClient.api.getFiles(
                folderId = parentId,
                favorite = if (favoritesOnly) true else null,
            )
        }
        ListCache.Snapshot(folderD.await(), foldersD.await(), filesD.await())
    }

    private fun apply(loaded: ListCache.Snapshot, offline: Boolean) {
        edit {
            val existing = loaded.files.map { f -> fileKey(f.id) }.toSet() + loaded.folders.map { f -> folderKey(f.id) }.toSet()
            val selection = it.selection.retain(existing)
            it.copy(
                isLoading = false,
                offline = offline,
                errorMessage = null,
                folders = loaded.folders,
                files = loaded.files,
                selection = if (selection.keys.isEmpty()) Selection() else selection,
                title = loaded.folder?.name ?: it.title,
                canUpload = canUploadHere && !offline,
            )
        }
    }

    /** Listeyi yükler. [showSpinner] false ise mevcut liste ekranda kalır. */
    private fun fetch(showSpinner: Boolean) {
        if (showSpinner) edit { it.copy(isLoading = true, errorMessage = null) }
        launchCatching(
            onError = { message ->
                edit { if (showSpinner) it.copy(isLoading = false, errorMessage = message) else it.copy(notice = message) }
            },
        ) { reload() }
    }

    /** Aşağı çekerek yenileme; zaten yenileniyorsa ikinci istek başlatılmaz. */
    fun refresh() {
        if (_state.value.refreshing) return
        edit { it.copy(refreshing = true) }
        launchCatching(::showNotice) {
            try {
                reload()
            } finally {
                edit { it.copy(refreshing = false) }
            }
        }
    }

    /** Bir işlemden sonra listeyi yeniler; yenileme başarısız olsa da işlem başarılı sayılır. */
    private suspend fun refreshQuietly() {
        try {
            reload()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showNotice(e.toApiErrorMessage())
        }
    }

    fun setSort(sort: SortOption) {
        edit { it.copy(sort = sort) }
    }

    fun setCategory(category: CategoryOption) {
        edit { it.copy(category = category) }
    }

    /** Seçilen dosyaları sıraya alır; her biri için listede bir ilerleme satırı çıkar. */
    fun uploadFiles(resolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty() || !_state.value.canUpload) return
        viewModelScope.launch {
            val named = withContext(Dispatchers.IO) { uris.map { it to FileUploader.queryName(resolver, it) } }
            named.forEach { (uri, name) -> enqueueUpload(resolver, uri, name) }
        }
    }

    private fun enqueueUpload(resolver: ContentResolver, uri: Uri, name: String) {
        val id = nextUploadId++
        edit { it.copy(uploads = it.uploads + UploadItem(id, name)) }

        uploadJobs[id] = viewModelScope.launch {
            try {
                uploadLock.withLock {
                    updateUpload(id) { it.copy(phase = UploadPhase.Uploading) }
                    FileUploader.upload(
                        resolver = resolver,
                        uri = uri,
                        folderId = parentId,
                        onProgress = { p -> updateUpload(id) { it.copy(progress = p) } },
                        onFinishing = { updateUpload(id) { it.copy(phase = UploadPhase.Finishing) } },
                    )
                }
                removeUpload(id)
                fetch(showSpinner = false)
            } catch (e: CancellationException) {
                removeUpload(id)
                throw e
            } catch (e: Exception) {
                updateUpload(id) { it.copy(phase = UploadPhase.Failed, error = e.toApiErrorMessage()) }
            } finally {
                uploadJobs.remove(id)
            }
        }
    }

    fun cancelUpload(id: Long) {
        uploadJobs[id]?.cancel()
    }

    /** Başarısız yükleme satırını kaldırır. */
    fun dismissUpload(id: Long) = removeUpload(id)

    private fun updateUpload(id: Long, change: (UploadItem) -> UploadItem) {
        edit { s -> s.copy(uploads = s.uploads.map { if (it.id == id) change(it) else it }) }
    }

    private fun removeUpload(id: Long) {
        edit { s -> s.copy(uploads = s.uploads.filterNot { it.id == id }) }
    }

    fun clearSelection() {
        edit { it.copy(selection = Selection()) }
    }

    fun toggleSelection(key: String) {
        edit { s ->
            val next = s.selection.toggle(key)
            s.copy(selection = if (next.keys.isEmpty()) Selection() else next)
        }
    }

    /** Ekranda görünen öğelerin hepsini seçer. */
    fun selectAllVisible() {
        edit { s ->
            val visible = s.visibleFiles.map { fileKey(it.id) }.toSet() + s.visibleFolders.map { folderKey(it.id) }.toSet()
            s.copy(selection = s.selection.selectAll(visible))
        }
    }

    /** Uzun basılan öğeyi seçime ekler ve sürüklenecek öğeleri döner. */
    fun longPress(key: String): SelectedItems? = edit { it.copy(selection = it.selection.add(key)) }.selectedItems

    fun openDialog(dialog: BrowserDialog) {
        if (dialog is BrowserDialog.FileInfo) storage.ensureLoaded()
        dialogs.open(dialog)
    }

    fun createFolder(name: String, color: String?, emoji: String?) = dialogs.submit {
        ApiClient.api.createFolder(
            CreateFolderRequest(
                name = name,
                parentId = parentId,
                location = if (parentId == null) location else null,
                color = color,
                emoji = emoji,
            )
        )
        reload()
    }

    fun editFolder(folder: FolderDto, newName: String, newColor: String?, newEmoji: String?) = dialogs.submit {
        val fields = buildMap<String, Any?> {
            if (newName != folder.name) put("name", newName)
            if (newColor != folder.color) put("color", newColor)
            if (newEmoji != folder.emoji) put("emoji", newEmoji)
        }
        if (fields.isNotEmpty()) ApiClient.api.updateFolder(folder.id, fields)
        reload()
    }

    fun renameFile(file: FileDto, newName: String) = dialogs.submit {
        if (newName != file.name) ApiClient.api.updateFile(file.id, mapOf("name" to newName))
        reload()
    }

    fun deleteFolder(folder: FolderDto) = dialogs.submit {
        ApiClient.api.deleteFolder(folder.id)
        reload()
    }

    fun deleteFile(file: FileDto) = dialogs.submit {
        ApiClient.api.deleteFile(file.id)
        reload()
    }

    fun toggleFavorite(folder: FolderDto) {
        launchCatching(::showNotice) {
            ApiClient.api.updateFolder(folder.id, mapOf("favorite" to !folder.favorite))
            reload()
        }
    }

    fun toggleFavorite(file: FileDto) {
        launchCatching(::showNotice) {
            ApiClient.api.updateFile(file.id, mapOf("favorite" to !file.favorite))
            reload()
        }
    }

    private suspend fun runBatch(
        items: SelectedItems,
        onFile: suspend (FileDto) -> Unit,
        onFolder: suspend (FolderDto) -> Unit,
    ) {
        var failed = 0
        var firstError: String? = null
        suspend fun attempt(action: suspend () -> Unit) {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                if (firstError == null) firstError = e.toApiErrorMessage()
            }
        }
        items.files.forEach { f -> attempt { onFile(f) } }
        items.folders.forEach { f -> attempt { onFolder(f) } }
        refreshQuietly()
        if (failed > 0) {
            throw BatchFailure(if (items.count == 1) firstError.orEmpty() else Txt.browser.batchFailed(failed, firstError))
        }
    }

    /** Öğeleri bir klasöre taşır; [targetFolderId] null ise ana sayfaya. */
    private suspend fun moveItems(items: SelectedItems, targetFolderId: String?, targetName: String) {
        runBatch(
            items,
            onFile = { ApiClient.api.updateFile(it.id, mapOf("folder_id" to targetFolderId)) },
            onFolder = { ApiClient.api.updateFolder(it.id, mapOf("parent_id" to targetFolderId)) },
        )
        val what = if (items.count == 1) Txt.browser.quoted(items.label) else items.label
        edit { it.copy(selection = Selection(), notice = Txt.browser.movedTo(what, targetName)) }
    }

    /** "Taşı" penceresinde hedef seçildi. */
    fun confirmMove(targetFolderId: String?, targetName: String) {
        val items = when (val d = dialogs.state.value.current) {
            is BrowserDialog.MoveFile -> SelectedItems(listOf(d.file), emptyList())
            is BrowserDialog.MoveFolder -> SelectedItems(emptyList(), listOf(d.folder))
            BrowserDialog.MoveSelection -> _state.value.selectedItems
            else -> return
        }
        dialogs.submit { moveItems(items, targetFolderId, targetName) }
    }

    /** Sürükle-bırak ile öğeleri [folderId] klasörüne taşır. */
    fun dropItems(items: SelectedItems, folderId: String) {
        val name = _state.value.folders.firstOrNull { it.id == folderId }?.name ?: Txt.browser.folderFallback
        launchCatching(::showNotice) { moveItems(items, folderId, name) }
    }

    fun confirmDeleteSelection() {
        val items = _state.value.selectedItems
        dialogs.submit {
            runBatch(
                items,
                onFile = { ApiClient.api.deleteFile(it.id) },
                onFolder = { ApiClient.api.deleteFolder(it.id) },
            )
            clearSelection()
        }
    }

    /** Seçilenlerin hepsi favoriyse favoriden çıkarır, değilse hepsini favoriye ekler. */
    fun favoriteSelection() {
        val items = _state.value.selectedItems
        val favorite = !items.allFavorite
        val changed = SelectedItems(
            files = items.files.filter { it.favorite != favorite },
            folders = items.folders.filter { it.favorite != favorite },
        )
        launchCatching(::showNotice) {
            runBatch(
                changed,
                onFile = { ApiClient.api.updateFile(it.id, mapOf("favorite" to favorite)) },
                onFolder = { ApiClient.api.updateFolder(it.id, mapOf("favorite" to favorite)) },
            )
            clearSelection()
        }
    }

    companion object {
        /** ViewModel'i üreten fabrika; her anahtar için ayrı bir örnek yaşar. */
        fun factory(
            parentId: String?,
            location: String?,
            favoritesOnly: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { BrowserViewModel(parentId, location, favoritesOnly) }
        }
    }
}
