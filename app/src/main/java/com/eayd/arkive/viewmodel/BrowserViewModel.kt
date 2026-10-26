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
import com.eayd.arkive.utils.toApiErrorMessage
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

/** Bir yüklemenin hangi aşamada olduğu. */
enum class UploadPhase { Waiting, Uploading, Finishing, Failed }

/** Listede gösterilen devam eden (ya da başarısız olan) yükleme. */
data class UploadItem(
    val id: Long,
    val name: String,
    val progress: Float = 0f,
    val phase: UploadPhase = UploadPhase.Waiting,
    val error: String? = null,
)

/** Liste ekranında açılabilen pencereler. Aynı anda en fazla biri açıktır ([DialogHost]). */
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

/** Klasör/dosya listesi ekranının tüm durumu. Ekran yalnızca bu nesneye bakarak çizilir. */
data class BrowserUiState(
    val isLoading: Boolean = true,
    /** Sunucudan gelen listeler. Ekranda [visibleFolders] ve [visibleFiles] (süzülmüş, sıralanmış) çizilir. */
    val folders: List<FolderDto> = emptyList(),
    val files: List<FileDto> = emptyList(),
    val visibleFolders: List<FolderDto> = emptyList(),
    val visibleFiles: List<FileDto> = emptyList(),
    val sort: SortOption = SortOption.NameAsc,
    val category: CategoryOption = CategoryOption.All,
    val selection: Selection = Selection(),
    val uploads: List<UploadItem> = emptyList(),
    /** İçinde bulunulan klasörün adı (kök listede null). */
    val title: String? = null,
    /** Bu konuma dosya yüklenebilir mi? (Favoriler ve AI sonuçları sayfasına yüklenemez.) */
    val canUpload: Boolean = false,
    /** Liste yüklenemedi: ekranın ortasında "Tekrar dene" ile gösterilir. */
    val errorMessage: String? = null,
    /** Kısa bildirim (Snackbar): işlem hatası ya da "taşındı" gibi sonuç mesajı. */
    val notice: String? = null,
) {
    val selectedFiles: List<FileDto> get() = files.filter { fileKey(it.id) in selection.keys }
    val selectedFolders: List<FolderDto> get() = folders.filter { folderKey(it.id) in selection.keys }
    val selectedItems: SelectedItems get() = SelectedItems(selectedFiles, selectedFolders)
}

/**
 * Bir "seviyedeki" klasörleri VE dosyaları yönetir; dosya yüklemeyi, çoklu seçimi, filtreleri ve pencerelerin
 * işlemlerini de buradan yürütür.
 *  - parentId == null → [location] sayfasının kök klasörleri/dosyaları (ana sayfa ya da AI sonuçları)
 *  - parentId != null → o klasörün içindekiler (konum üst klasörden devralınır, [location] kullanılmaz)
 *  - favoritesOnly    → derinliğine bakılmaksızın TÜM favori klasörler ve dosyalar ("Favorilerim")
 *
 * Ekran ile sunucu arasındaki tek kapı budur: ekran işlemi ViewModel'e bildirir, ViewModel sunucuya
 * gider, sonuç gelince [state] güncellenir ve ekran kendiliğinden yeniden çizilir (tek yönlü veri akışı).
 */
class BrowserViewModel(
    val parentId: String?,
    private val location: String?,
    val favoritesOnly: Boolean = false,
) : ViewModel() {

    // Ana sayfada dosya vardır, AI sonuçları kökünde yoktur (oraya dosya yüklenemez). Klasörlerin içinde
    // ve favorilerde dosyalar listelenir.
    private val showFiles = favoritesOnly || parentId != null || location == FolderLocations.HOME

    private val _state = MutableStateFlow(
        BrowserUiState(canUpload = !favoritesOnly && (parentId != null || location == FolderLocations.HOME))
    )
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    /** Açık pencere (yeni klasör, sil, taşı…) ve kaydediliyor/hata durumu. */
    val dialogs = DialogHost<BrowserDialog>(viewModelScope)

    private val storage = StorageLimitLoader(viewModelScope)

    /** "Bilgi" penceresi için hesabın toplam kotası (yüklenene kadar null). */
    val storageLimit: StateFlow<Long?> = storage.limit

    // Yüklemeler sırayla yapılır (aynı anda tek dosya): telefonun bant genişliğini bölmez, ilerleme
    // çubukları anlaşılır kalır. Mutex bir "kilit"tir; kuyruktaki işler sırayla içeri girer.
    private val uploadLock = Mutex()
    private val uploadJobs = mutableMapOf<Long, Job>()
    private var nextUploadId = 0L

    init {
        fetch(showSpinner = true)
    }

    // Ekran her görününce (ör. klasörden geri dönünce) bir kez yenilenir; ilk gösterim zaten init'te yüklendi.
    private var skipNextShowRefresh = true

    fun onShown() {
        if (skipNextShowRefresh) skipNextShowRefresh = false else fetch(showSpinner = false)
    }

    /** Listeyi baştan yükler ("Tekrar dene" için). */
    fun load() = fetch(showSpinner = true)

    fun clearNotice() = edit { it.copy(notice = null) }

    fun showNotice(message: String) {
        edit { it.copy(notice = message) }
    }

    /**
     * Durumu değiştirir. Sunucu listesi, sıralama ya da kategori değiştiyse ekranda gösterilen süzülmüş listeler
     * de yeniden hesaplanır (yükleme ilerlemesi gibi sık güncellemelerde hesaplanmaz).
     */
    private fun edit(transform: (BrowserUiState) -> BrowserUiState): BrowserUiState = _state.updateAndGet { old ->
        val new = transform(old)
        if (new.folders === old.folders && new.files === old.files &&
            new.sort == old.sort && new.category == old.category
        ) {
            new
        } else {
            new.copy(
                // Klasörlerin türü olmadığı için bir kategori seçiliyken yalnızca o türdeki dosyalar gösterilir.
                visibleFolders = if (new.category == CategoryOption.All) new.folders.sortedFolders(new.sort) else emptyList(),
                visibleFiles = new.files.filter { new.category.matches(it.mimeType) }.sortedFiles(new.sort),
            )
        }
    }

    // --- Yükleme (liste) ---------------------------------------------------------------------------

    private class Loaded(val folder: FolderDto?, val folders: List<FolderDto>, val files: List<FileDto>)

    /** Sunucudan güncel listeyi çeker (klasörler ve dosyalar aynı anda istenir) ve durumu günceller. */
    private suspend fun reload() {
        val loaded = coroutineScope {
            val folderD = async { parentId?.let { ApiClient.api.getFolder(it) } }
            val foldersD = async {
                ApiClient.api.getFolders(
                    parentId = parentId,
                    location = if (parentId == null && !favoritesOnly) location else null,
                    favorite = if (favoritesOnly) true else null,
                )
            }
            val filesD = async {
                if (showFiles) {
                    ApiClient.api.getFiles(
                        folderId = parentId,
                        favorite = if (favoritesOnly) true else null,
                    )
                } else emptyList()
            }
            Loaded(folderD.await(), foldersD.await(), filesD.await())
        }
        edit {
            // Sunucu ada göre sıralı verir; sort kararlıdır. Listeden kalkmış (taşınmış, silinmiş) öğeler seçimden düşer.
            val existing = loaded.files.map { f -> fileKey(f.id) }.toSet() + loaded.folders.map { f -> folderKey(f.id) }.toSet()
            val selection = it.selection.retain(existing)
            it.copy(
                isLoading = false,
                errorMessage = null,
                folders = loaded.folders,
                files = loaded.files,
                selection = if (selection.keys.isEmpty()) Selection() else selection,
                title = loaded.folder?.name ?: it.title,
                // AI sonuçları klasörlerine dosya yüklenemez (sunucu da reddeder).
                canUpload = !favoritesOnly && loaded.folder?.location != FolderLocations.AI_RESULTS &&
                    (parentId != null || location == FolderLocations.HOME),
            )
        }
    }

    /**
     * showSpinner = false: işlemden sonra yenilerken mevcut liste ekranda kalır, yükleme çarkı çıkmaz;
     * yenileme başarısız olursa yalnızca kısa bir bildirim gösterilir.
     */
    private fun fetch(showSpinner: Boolean) {
        if (showSpinner) edit { it.copy(isLoading = true, errorMessage = null) }
        launchCatching(
            onError = { message ->
                edit { if (showSpinner) it.copy(isLoading = false, errorMessage = message) else it.copy(notice = message) }
            },
        ) { reload() }
    }

    /** Bir işlemden sonra listeyi yeniler; yenileme başarısız olursa işlemin kendisi başarısız sayılmaz. */
    private suspend fun refreshQuietly() {
        try {
            reload()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showNotice(e.toApiErrorMessage())
        }
    }

    // --- Sıralama ve kategori ------------------------------------------------------------------------

    fun setSort(sort: SortOption) {
        edit { it.copy(sort = sort) }
    }

    fun setCategory(category: CategoryOption) {
        edit { it.copy(category = category) }
    }

    // --- Dosya yükleme -------------------------------------------------------------------------

    /** Seçilen dosyaları kuyruğa alır; sırayla yüklenir. Her biri için listede bir ilerleme satırı görünür. */
    fun uploadFiles(resolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty() || !_state.value.canUpload) return
        viewModelScope.launch {
            // Dosya adları (içerik sağlayıcıdan sorgulanır) ana iş parçacığını bloklamasın diye IO'da okunur.
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
                        // Bu geri çağrılar ağ iş parçacığından gelir; MutableStateFlow güncellemesi iş parçacığı güvenlidir.
                        onProgress = { p -> updateUpload(id) { it.copy(progress = p) } },
                        onFinishing = { updateUpload(id) { it.copy(phase = UploadPhase.Finishing) } },
                    )
                }
                removeUpload(id)
                fetch(showSpinner = false)   // yeni dosya listede görünsün
            } catch (e: CancellationException) {
                removeUpload(id)             // kullanıcı iptal etti: satır kalkar (sunucu kaydını FileUploader siler)
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

    /** Başarısız yükleme satırını listeden kaldırır. */
    fun dismissUpload(id: Long) = removeUpload(id)

    private fun updateUpload(id: Long, change: (UploadItem) -> UploadItem) {
        edit { s -> s.copy(uploads = s.uploads.map { if (it.id == id) change(it) else it }) }
    }

    private fun removeUpload(id: Long) {
        edit { s -> s.copy(uploads = s.uploads.filterNot { it.id == id }) }
    }

    // --- Çoklu seçim ---------------------------------------------------------------------------------
    // Seçim modu satırlara basılı tutunca açılır, seçili öğe kalmayınca kapanır.

    fun clearSelection() {
        edit { it.copy(selection = Selection()) }
    }

    fun toggleSelection(key: String) {
        edit { s ->
            val next = s.selection.toggle(key)
            s.copy(selection = if (next.keys.isEmpty()) Selection() else next)
        }
    }

    /** "Tümünü seç": ekranda görünen (süzülmüş) öğelerin hepsi. */
    fun selectAllVisible() {
        edit { s ->
            val visible = s.visibleFiles.map { fileKey(it.id) }.toSet() + s.visibleFolders.map { folderKey(it.id) }.toSet()
            s.copy(selection = s.selection.selectAll(visible))
        }
    }

    /**
     * Uzun basış: öğeyi seçime ekler ve seçimin tamamını sürüklenecek olarak döner (tek öğe de olabilir).
     * Önceden seçili öğeler varsa onlar da birlikte taşınır. AI sonuçları klasörleri taşınamaz: o zaman null.
     */
    fun longPress(key: String): SelectedItems? {
        val items = edit { it.copy(selection = it.selection.add(key)) }.selectedItems
        return items.takeIf { selected -> selected.folders.all { it.location == FolderLocations.HOME } }
    }

    // --- Kullanıcı işlemleri -------------------------------------------------------------------------
    // Pencerelerin işlemleri `dialogs.submit` ile çalışır: başarılıysa pencere kapanır, hata verirse pencerede
    // gösterilir. Satırdaki işlemler (favori…) hatayı kısa bildirimle gösterir.

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
        // Yalnızca gerçekten değişen alanlar gönderilir (PATCH = kısmi güncelleme).
        val fields = buildMap<String, Any?> {
            if (newName != folder.name) put("name", newName)
            if (newColor != folder.color) put("color", newColor)
            if (newEmoji != folder.emoji) put("emoji", newEmoji)   // null = emojiyi sil (serializeNulls)
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

    // --- Toplu işlemler ------------------------------------------------------------------------------
    // Öğelere sırayla uygulanır. Biri başarısız olsa da diğerleri denenir; sonunda liste her durumda sunucudan
    // yenilenir (işlenenler ekranda doğru görünsün). Başarısız olan olursa [BatchFailure] fırlatılır.

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
            throw BatchFailure(if (items.count == 1) firstError.orEmpty() else "$failed öğe işlenemedi: $firstError")
        }
    }

    /** Öğeleri bir klasöre taşır ([targetFolderId] null ise ana sayfanın en üstüne) ve sonucu bildirir. */
    private suspend fun moveItems(items: SelectedItems, targetFolderId: String?, targetName: String) {
        runBatch(
            items,
            onFile = { ApiClient.api.updateFile(it.id, mapOf("folder_id" to targetFolderId)) },
            onFolder = { ApiClient.api.updateFolder(it.id, mapOf("parent_id" to targetFolderId)) },
        )
        val what = if (items.count == 1) "“${items.label}”" else items.label
        edit { it.copy(selection = Selection(), notice = "$what “$targetName” klasörüne taşındı") }
    }

    /** "Taşı" penceresinde hedef seçildi: tek dosya, tek klasör ya da seçimin tamamı taşınır. */
    fun confirmMove(targetFolderId: String?, targetName: String) {
        val items = when (val d = dialogs.state.value.current) {
            is BrowserDialog.MoveFile -> SelectedItems(listOf(d.file), emptyList())
            is BrowserDialog.MoveFolder -> SelectedItems(emptyList(), listOf(d.folder))
            BrowserDialog.MoveSelection -> _state.value.selectedItems
            else -> return
        }
        dialogs.submit { moveItems(items, targetFolderId, targetName) }
    }

    /** Sürükle-bırak: seçilen öğeleri [folderId] klasörüne taşır. */
    fun dropItems(items: SelectedItems, folderId: String) {
        val name = _state.value.folders.firstOrNull { it.id == folderId }?.name ?: "klasör"
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
        /**
         * ViewModel'in kurucusu parametre aldığı için onu üreten bir "fabrika" gerekir.
         * viewModel(key = ...) ile birlikte kullanılır: her anahtar için ayrı bir örnek yaşar
         * (ana sayfa listesi, AI sonuçları listesi, favoriler, her klasörün kendi listesi).
         */
        fun factory(
            parentId: String?,
            location: String?,
            favoritesOnly: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { BrowserViewModel(parentId, location, favoritesOnly) }
        }
    }
}
