package com.eayd.arkive.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.FileDownloader
import com.eayd.arkive.ai.DocumentReader
import com.eayd.arkive.ai.InputCache
import com.eayd.arkive.ai.PasswordException
import com.eayd.arkive.ai.UnreadableDocumentException
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.ListCache
import com.eayd.arkive.data.NetworkPolicy
import com.eayd.arkive.data.PdfDoc
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.PreviewKind
import com.eayd.arkive.utils.decodeScaledBitmap
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.utils.previewKind
import com.eayd.arkive.word.WordDoc
import com.eayd.arkive.word.WordLoader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Ön izleme ekranında gösterilecek içerik. */
sealed interface PreviewContent {
    class Image(val bitmap: Bitmap) : PreviewContent
    class Pdf(val doc: PdfDoc) : PreviewContent
    class Text(val text: String, val truncated: Boolean) : PreviewContent
    /** Word belgesi. */
    class Word(val doc: WordDoc) : PreviewContent
    /** Dosya parolalı; parola girilince açılır. */
    data object PasswordProtected : PreviewContent
    /** Video ya da ses; indirilmeden akıştan oynatılır. */
    class Media(val url: String, val isAudio: Boolean) : PreviewContent
    /** Ön izleme yapılamıyor; neden ve "İndir" düğmesi gösterilir. */
    class NoPreview(val reason: String) : PreviewContent
}

sealed interface PreviewState {
    /** [progress] null ise belirsiz gösterge, değilse 0f..1f. */
    data class Loading(val progress: Float? = null) : PreviewState
    data class Error(val message: String) : PreviewState
    class Ready(val file: FileDto, val content: PreviewContent) : PreviewState
}

/** Ön izlemenin 3 nokta menüsünden açılan pencereler. */
enum class PreviewDialog { Rename, Move, Info, Delete, Password }

/** Tek bir dosyanın ön izlemesini hazırlar: dosyayı indirir ya da akışını hazırlar ve türüne göre açar. */
class PreviewViewModel(
    private val fileId: String,
    cacheDir: File,
) : ViewModel() {

    private val _state = MutableStateFlow<PreviewState>(PreviewState.Loading())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    /** Üst çubuktaki dosya adı; içerik hazır olmadan dolar. */
    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    /** Kısa bildirim. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Açık pencere ve kaydetme durumu. */
    val dialogs = DialogHost<PreviewDialog>(viewModelScope)

    private val storage = StorageLimitLoader(viewModelScope)

    /** Hesabın toplam kotası; yüklenene kadar null. */
    val storageLimit: StateFlow<Long?> = storage.limit

    /** Dosya silindi; ekran listeye dönmeli. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val tempFile = File(cacheDir, "preview/$fileId")
    private val unlockedFile = File(cacheDir, "preview/$fileId.unlocked")
    private var pdf: PdfDoc? = null

    /** Parola bekleyen dosya ve türü. */
    private var locked: Pair<PreviewKind, File>? = null
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = PreviewState.Loading()
        job = launchCatching(onError = { _state.value = PreviewState.Error(it) }) {
            val file = try {
                ApiClient.api.getFile(fileId)
            } catch (e: IOException) {
                val offline = withContext(Dispatchers.IO) {
                    ListCache.findFile(fileId)
                        ?.let { f -> InputCache.get(f.id, f.size)?.let { f to it } }
                } ?: throw IOException(Txt.preview.offlineNotSaved, e)
                _title.value = offline.first.name
                show(offline.first, buildContent(offline.first, offline.second))
                return@launchCatching
            }
            _title.value = file.name
            show(file, buildContent(file))
        }
    }

    private fun show(file: FileDto, content: PreviewContent) {
        _state.value = PreviewState.Ready(file, content)
        if (content is PreviewContent.PasswordProtected) dialogs.open(PreviewDialog.Password)
    }

    /** İçeriği hazırlar; [local] verilirse telefondaki kopyadan, internetsiz. */
    private suspend fun buildContent(file: FileDto, local: File? = null): PreviewContent {
        val kind = previewKind(file.mimeType, file.name)
        if (kind == PreviewKind.None) {
            return PreviewContent.NoPreview(Txt.preview.unsupportedType)
        }

        val limit = when (kind) {
            PreviewKind.Image -> MAX_IMAGE_BYTES
            PreviewKind.Pdf -> MAX_PDF_BYTES
            PreviewKind.Word -> MAX_WORD_BYTES
            else -> Long.MAX_VALUE
        }
        if (file.size > limit) {
            return PreviewContent.NoPreview(Txt.preview.tooBig(formatBytes(file.size)))
        }

        if (local != null) return buildLocalContent(kind, local)

        NetworkPolicy.requireAllowed()
        val url = ApiClient.api.getDownloadUrl(fileId).url

        return when (kind) {
            PreviewKind.Video -> PreviewContent.Media(url, isAudio = false)
            PreviewKind.Audio -> PreviewContent.Media(url, isAudio = true)

            PreviewKind.Text -> {
                val (text, truncated) = FileDownloader.readText(url, MAX_TEXT_BYTES)
                PreviewContent.Text(text, truncated)
            }

            PreviewKind.Image -> {
                downloadToTemp(url, limit)
                decodeImage(tempFile)
            }

            PreviewKind.Pdf -> {
                downloadToTemp(url, limit)
                openPdf(tempFile)
            }

            PreviewKind.Word -> {
                downloadToTemp(url, limit)
                openWord(tempFile, password = null)
            }

            PreviewKind.None -> PreviewContent.NoPreview(Txt.preview.unsupportedType)
        }
    }

    /** İnternetsiz yalnızca telefonda kopyası olan metin ve PDF açılır. */
    private suspend fun buildLocalContent(kind: PreviewKind, local: File): PreviewContent = when (kind) {
        PreviewKind.Text -> withContext(Dispatchers.IO) {
            val bytes = local.readBytes()
            PreviewContent.Text(
                String(bytes, 0, minOf(bytes.size, MAX_TEXT_BYTES), Charsets.UTF_8),
                truncated = bytes.size > MAX_TEXT_BYTES,
            )
        }

        PreviewKind.Pdf -> openPdf(local)

        PreviewKind.Word -> openWord(local, password = null)

        else -> PreviewContent.NoPreview(Txt.preview.offlineTypeUnsupported)
    }

    private suspend fun decodeImage(file: File): PreviewContent {
        val bitmap = withContext(Dispatchers.IO) {
            try {
                decodeScaledBitmap(file)
            } catch (e: OutOfMemoryError) {
                null
            }
        }
        return if (bitmap != null) PreviewContent.Image(bitmap)
        else PreviewContent.NoPreview(Txt.preview.imageFormatUnsupported)
    }

    private suspend fun openPdf(file: File): PreviewContent = try {
        val doc = PdfDoc.open(file)
        pdf = doc
        if (doc.pageCount == 0) PreviewContent.NoPreview(Txt.preview.pdfNoPages) else PreviewContent.Pdf(doc)
    } catch (e: SecurityException) {
        locked = PreviewKind.Pdf to file
        PreviewContent.PasswordProtected
    } catch (e: IOException) {
        PreviewContent.NoPreview(Txt.preview.pdfOpenFailed)
    }

    /** Word belgesini açar; parola gerekiyorsa [PreviewContent.PasswordProtected] döner. */
    private suspend fun openWord(file: File, password: String?): PreviewContent = try {
        val doc = withContext(Dispatchers.IO) { WordLoader.load(file.readBytes(), password) }
        if (doc.isBlank()) PreviewContent.NoPreview(Txt.preview.noTextToShow) else PreviewContent.Word(doc)
    } catch (e: PasswordException) {
        if (e.wrong) throw e
        locked = PreviewKind.Word to file
        PreviewContent.PasswordProtected
    } catch (e: UnreadableDocumentException) {
        PreviewContent.NoPreview(Txt.preview.documentOpenFailed(e.message))
    } catch (e: IOException) {
        PreviewContent.NoPreview(Txt.preview.documentUnreadable)
    }

    /** Parolayla dosyayı açar. Parola yalnızca telefonda kullanılır, sunucuya gitmez. */
    fun unlock(password: String) = dialogs.submit {
        val (kind, source) = locked ?: return@submit
        val file = (_state.value as? PreviewState.Ready)?.file ?: return@submit
        val content = when (kind) {
            PreviewKind.Word -> openWord(source, password)
            else -> {
                unlockedFile.parentFile?.mkdirs()
                DocumentReader.unlockPdf(AppSettings.appContext, source, password, unlockedFile)
                openPdf(unlockedFile)
            }
        }
        locked = null
        _state.value = PreviewState.Ready(file, content)
    }

    private suspend fun downloadToTemp(url: String, maxBytes: Long) {
        tempFile.delete()
        try {
            FileDownloader.downloadTo(url, tempFile, maxBytes) { progress ->
                _state.value = PreviewState.Loading(progress)
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun showNotice(message: String) {
        _notice.value = message
    }

    fun openDialog(dialog: PreviewDialog) {
        if (dialog == PreviewDialog.Info) storage.ensureLoaded()
        dialogs.open(dialog)
    }

    /** Açık dosyanın bilgisini içeriği yeniden indirmeden günceller. */
    private fun replaceFile(updated: FileDto) {
        _title.value = updated.name
        _state.update { current ->
            if (current is PreviewState.Ready) PreviewState.Ready(updated, current.content) else current
        }
    }

    fun renameFile(newName: String) = dialogs.submit {
        val current = (_state.value as? PreviewState.Ready)?.file
        if (current == null || newName != current.name) {
            replaceFile(ApiClient.api.updateFile(fileId, mapOf("name" to newName)))
        }
    }

    /** Dosyayı taşır; [targetFolderId] null ise en üste. */
    fun moveFile(targetFolderId: String?, targetName: String) = dialogs.submit {
        val name = (_state.value as? PreviewState.Ready)?.file?.name
        replaceFile(ApiClient.api.updateFile(fileId, mapOf("folder_id" to targetFolderId)))
        if (name != null) showNotice(Txt.preview.movedTo(name, targetName))
    }

    fun deleteFile() = dialogs.submit {
        ApiClient.api.deleteFile(fileId)
        _deleted.value = true
    }

    override fun onCleared() {
        pdf?.closeAsync()
        tempFile.delete()
        unlockedFile.delete()
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        private const val MAX_PDF_BYTES = 50L * 1024 * 1024
        private const val MAX_WORD_BYTES = 20L * 1024 * 1024
        private const val MAX_TEXT_BYTES = 512 * 1024

        fun factory(fileId: String, cacheDir: File): ViewModelProvider.Factory = viewModelFactory {
            initializer { PreviewViewModel(fileId, cacheDir) }
        }
    }
}
