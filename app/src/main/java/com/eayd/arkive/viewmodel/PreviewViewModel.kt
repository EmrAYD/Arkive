package com.eayd.arkive.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.FileDownloader
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.NetworkPolicy
import com.eayd.arkive.data.PdfDoc
import com.eayd.arkive.utils.PreviewKind
import com.eayd.arkive.utils.decodeScaledBitmap
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.utils.previewKind
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Ön izleme ekranında gösterilecek içerik. Her tür kendi görüntüleyicisiyle çizilir. */
sealed interface PreviewContent {
    class Image(val bitmap: Bitmap) : PreviewContent
    class Pdf(val doc: PdfDoc) : PreviewContent
    class Text(val text: String, val truncated: Boolean) : PreviewContent
    /** Video ve ses, indirilmeden doğrudan imzalı adresten akış olarak oynatılır. */
    class Media(val url: String, val isAudio: Boolean) : PreviewContent
    /** Ön izleme yapılamıyor (tür desteklenmiyor, dosya çok büyük…); ekranda neden ve "indir" düğmesi gösterilir. */
    class NoPreview(val reason: String) : PreviewContent
}

sealed interface PreviewState {
    /** [progress] null ise belirsiz (dönen) gösterge, değilse 0f..1f. */
    data class Loading(val progress: Float? = null) : PreviewState
    data class Error(val message: String) : PreviewState
    class Ready(val file: FileDto, val content: PreviewContent) : PreviewState
}

/** Ön izleme ekranının 3 nokta menüsünden açılan pencereler. Aynı anda en fazla biri açıktır ([DialogHost]). */
enum class PreviewDialog { Rename, Move, Info, Delete }

/**
 * Tek bir dosyanın ön izlemesini hazırlar: dosya bilgisini ve imzalı adresi sunucudan alır, türüne göre
 *  - resim / PDF  → dosyayı geçici olarak telefonun önbelleğine indirir ve çözer,
 *  - metin        → ilk 512 KB'ı okur,
 *  - video / ses  → yalnızca adresi verir (oynatıcı akıştan okur),
 *  - diğerleri    → "ön izleme yok" der.
 * Ekrandan çıkılınca ([onCleared]) indirilen geçici dosya silinir ve PDF belgesi kapatılır.
 */
class PreviewViewModel(
    private val fileId: String,
    cacheDir: File,
) : ViewModel() {

    private val _state = MutableStateFlow<PreviewState>(PreviewState.Loading())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    /** Üst çubukta gösterilen dosya adı; dosya bilgisi gelir gelmez (içerik hazır olmadan) dolar. */
    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    /** Kısa bildirim (Snackbar): işlem hatası ya da "taşındı" gibi sonuç mesajı. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Açık pencere (yeniden adlandır, taşı, bilgi, sil) ve kaydediliyor/hata durumu. */
    val dialogs = DialogHost<PreviewDialog>(viewModelScope)

    private val storage = StorageLimitLoader(viewModelScope)

    /** "Bilgi" penceresi için hesabın toplam kotası (yüklenene kadar null). */
    val storageLimit: StateFlow<Long?> = storage.limit

    /** Dosya silindi: ekran listeye dönmelidir. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val tempFile = File(cacheDir, "preview/$fileId")
    private var pdf: PdfDoc? = null
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = PreviewState.Loading()
        job = launchCatching(onError = { _state.value = PreviewState.Error(it) }) {
            val file = ApiClient.api.getFile(fileId)
            _title.value = file.name
            _state.value = PreviewState.Ready(file, buildContent(file))
        }
    }

    private suspend fun buildContent(file: FileDto): PreviewContent {
        val kind = previewKind(file.mimeType, file.name)
        if (kind == PreviewKind.None) {
            return PreviewContent.NoPreview("Bu dosya türü için ön izleme desteklenmiyor.")
        }

        // Boyut sınırları: ön izleme için büyük dosyaları telefona indirmek yavaş ve belleği zorlar.
        val limit = when (kind) {
            PreviewKind.Image -> MAX_IMAGE_BYTES
            PreviewKind.Pdf -> MAX_PDF_BYTES
            else -> Long.MAX_VALUE
        }
        if (file.size > limit) {
            return PreviewContent.NoPreview("Dosya ön izleme için çok büyük (${formatBytes(file.size)}).")
        }

        NetworkPolicy.requireAllowed() // "Yalnızca Wi-Fi" açıksa dosya içeriği indirilmez
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
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        decodeScaledBitmap(tempFile)
                    } catch (e: OutOfMemoryError) {
                        null
                    }
                }
                if (bitmap != null) PreviewContent.Image(bitmap)
                else PreviewContent.NoPreview("Bu görüntü biçimi telefonda açılamıyor.")
            }

            PreviewKind.Pdf -> {
                downloadToTemp(url, limit)
                try {
                    val doc = PdfDoc.open(tempFile)
                    pdf = doc
                    if (doc.pageCount == 0) PreviewContent.NoPreview("PDF'te sayfa yok.")
                    else PreviewContent.Pdf(doc)
                } catch (e: SecurityException) {
                    PreviewContent.NoPreview("Bu PDF şifreli, ön izlenemiyor.")
                } catch (e: IOException) {
                    PreviewContent.NoPreview("PDF açılamadı; dosya bozuk olabilir.")
                }
            }

            PreviewKind.None -> PreviewContent.NoPreview("Bu dosya türü için ön izleme desteklenmiyor.")
        }
    }

    private suspend fun downloadToTemp(url: String, maxBytes: Long) {
        tempFile.delete()
        try {
            FileDownloader.downloadTo(url, tempFile, maxBytes) { progress ->
                _state.value = PreviewState.Loading(progress)
            }
        } catch (e: Exception) {
            tempFile.delete()   // yarım inen dosya kalmasın
            throw e
        }
    }

    // --- 3 nokta menüsü işlemleri (listedekiyle aynı) --------------------------------------------------

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

    /** Açık olan dosyanın bilgisini (ad, klasör…) içerik yeniden indirilmeden günceller. */
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

    /** [targetFolderId] null ise en üst seviyeye taşır (JSON'da `null`: bkz. ApiClient serializeNulls). */
    fun moveFile(targetFolderId: String?, targetName: String) = dialogs.submit {
        val name = (_state.value as? PreviewState.Ready)?.file?.name
        replaceFile(ApiClient.api.updateFile(fileId, mapOf("folder_id" to targetFolderId)))
        if (name != null) showNotice("“$name” “$targetName” klasörüne taşındı")
    }

    fun deleteFile() = dialogs.submit {
        ApiClient.api.deleteFile(fileId)
        _deleted.value = true
    }

    override fun onCleared() {
        pdf?.closeAsync()
        tempFile.delete()
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        private const val MAX_PDF_BYTES = 50L * 1024 * 1024
        private const val MAX_TEXT_BYTES = 512 * 1024

        fun factory(fileId: String, cacheDir: File): ViewModelProvider.Factory = viewModelFactory {
            initializer { PreviewViewModel(fileId, cacheDir) }
        }
    }
}
