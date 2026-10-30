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
    /** Dosya parola ile korunuyor; parola girilince ön izleme açılır ([PreviewViewModel.unlock]). */
    data object PasswordProtected : PreviewContent
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
enum class PreviewDialog { Rename, Move, Info, Delete, Password }

/**
 * Tek bir dosyanın ön izlemesini hazırlar: dosya bilgisini ve imzalı adresi sunucudan alır, türüne göre
 *  - resim / PDF  → dosyayı geçici olarak telefonun önbelleğine indirir ve çözer,
 *  - Word (DOCX)  → dosyayı indirir, metnini çıkarıp düz metin olarak gösterir (biçim ve görseller yok),
 *  - metin        → ilk 512 KB'ı okur,
 *  - parolalı PDF/DOCX → parola istenir; PDF'in parolasız bir kopyası önbelleğe yazılır (çizici parolalı PDF açamaz),
 *  - video / ses  → yalnızca adresi verir (oynatıcı akıştan okur),
 *  - diğerleri    → "ön izleme yok" der.
 * Ekrandan çıkılınca ([onCleared]) indirilen geçici dosyalar (parolasız kopya dahil) silinir ve PDF belgesi kapatılır.
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
    private val unlockedFile = File(cacheDir, "preview/$fileId.unlocked")
    private var pdf: PdfDoc? = null

    /** Parola bekleyen dosya (indirilmiş geçici kopya ya da telefondaki kopya) ve türü. */
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
                // İnternet yok: dosya telefonda kayıtlıysa (liste kaydı + dosya kopyası) ön izleme oradan açılır.
                val offline = withContext(Dispatchers.IO) {
                    ListCache.findFile(fileId)?.let { f -> InputCache.get(f.id, f.size)?.let { f to it } }
                } ?: throw IOException("Sunucuya ulaşılamadı ve bu dosya telefonda kayıtlı değil.", e)
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
        // Parolalı dosyada parola penceresi kendiliğinden açılır; kapatılırsa ekrandaki düğmeden yeniden açılır.
        if (content is PreviewContent.PasswordProtected) dialogs.open(PreviewDialog.Password)
    }

    /** [local] doluysa içerik internetsiz, telefondaki kopyadan hazırlanır (video/ses gibi akış türleri hariç). */
    private suspend fun buildContent(file: FileDto, local: File? = null): PreviewContent {
        val kind = previewKind(file.mimeType, file.name)
        if (kind == PreviewKind.None) {
            return PreviewContent.NoPreview("Bu dosya türü için ön izleme desteklenmiyor.")
        }

        // Boyut sınırları: ön izleme için büyük dosyaları telefona indirmek yavaş ve belleği zorlar.
        val limit = when (kind) {
            PreviewKind.Image -> MAX_IMAGE_BYTES
            PreviewKind.Pdf -> MAX_PDF_BYTES
            PreviewKind.Docx -> MAX_DOCX_BYTES
            else -> Long.MAX_VALUE
        }
        if (file.size > limit) {
            return PreviewContent.NoPreview("Dosya ön izleme için çok büyük (${formatBytes(file.size)}).")
        }

        if (local != null) return buildLocalContent(kind, local)

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
                decodeImage(tempFile)
            }

            PreviewKind.Pdf -> {
                downloadToTemp(url, limit)
                openPdf(tempFile)
            }

            PreviewKind.Docx -> {
                downloadToTemp(url, limit)
                openDocx(tempFile, password = null)
            }

            PreviewKind.None -> PreviewContent.NoPreview("Bu dosya türü için ön izleme desteklenmiyor.")
        }
    }

    /** İnternetsiz: yalnızca telefonda kopyası tutulan türler (metin, PDF) açılır; kopya yalnızca bunlar için alınır. */
    private suspend fun buildLocalContent(kind: PreviewKind, local: File): PreviewContent = when (kind) {
        PreviewKind.Text -> withContext(Dispatchers.IO) {
            val bytes = local.readBytes()
            PreviewContent.Text(
                String(bytes, 0, minOf(bytes.size, MAX_TEXT_BYTES), Charsets.UTF_8),
                truncated = bytes.size > MAX_TEXT_BYTES,
            )
        }

        PreviewKind.Pdf -> openPdf(local)

        PreviewKind.Docx -> openDocx(local, password = null)

        else -> PreviewContent.NoPreview("Sunucuya ulaşılamadığında bu dosya türü ön izlenemiyor.")
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
        else PreviewContent.NoPreview("Bu görüntü biçimi telefonda açılamıyor.")
    }

    private suspend fun openPdf(file: File): PreviewContent = try {
        val doc = PdfDoc.open(file)
        pdf = doc
        if (doc.pageCount == 0) PreviewContent.NoPreview("PDF'te sayfa yok.") else PreviewContent.Pdf(doc)
    } catch (e: SecurityException) {
        // Android'in PDF çizicisi parolalı dosyayı açamaz: parola istenir, çözülmüş kopya çizilir ([unlock]).
        locked = PreviewKind.Pdf to file
        PreviewContent.PasswordProtected
    } catch (e: IOException) {
        PreviewContent.NoPreview("PDF açılamadı; dosya bozuk olabilir.")
    }

    /**
     * Word belgesini metne çevirip gösterir. Parola gerekiyorsa ve [password] yoksa [PreviewContent.PasswordProtected];
     * [password] yanlışsa hata fırlatılır (parola penceresinde gösterilir).
     */
    private suspend fun openDocx(file: File, password: String?): PreviewContent = try {
        val text = DocumentReader.readDocxFile(file, password).text
        when {
            text.isBlank() -> PreviewContent.NoPreview("Belgede gösterilecek metin yok.")
            text.length > MAX_TEXT_BYTES -> PreviewContent.Text(text.take(MAX_TEXT_BYTES), truncated = true)
            else -> PreviewContent.Text(text, truncated = false)
        }
    } catch (e: PasswordException) {
        if (e.wrong) throw e
        locked = PreviewKind.Docx to file
        PreviewContent.PasswordProtected
    } catch (e: UnreadableDocumentException) {
        PreviewContent.NoPreview("Belge açılamadı: ${e.message}")
    }

    /**
     * Parola penceresinde "Aç": parola doğruysa ön izleme açılır ve pencere kapanır; yanlışsa pencerede
     * "Parola yanlış." görünür. Parola yalnızca bu telefonda, dosyayı açmak için kullanılır; sunucuya gitmez.
     */
    fun unlock(password: String) = dialogs.submit {
        val (kind, source) = locked ?: return@submit
        val file = (_state.value as? PreviewState.Ready)?.file ?: return@submit
        val content = when (kind) {
            PreviewKind.Docx -> openDocx(source, password)
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
        unlockedFile.delete() // parolasız kopya telefonda kalmasın
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
        private const val MAX_PDF_BYTES = 50L * 1024 * 1024
        private const val MAX_DOCX_BYTES = 20L * 1024 * 1024
        private const val MAX_TEXT_BYTES = 512 * 1024

        fun factory(fileId: String, cacheDir: File): ViewModelProvider.Factory = viewModelFactory {
            initializer { PreviewViewModel(fileId, cacheDir) }
        }
    }
}
