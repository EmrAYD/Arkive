package com.eayd.arkive.ai

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.formatBytes
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Telefondaki yapay zeka modeli (Gemma 4 E2B, LiteRT-LM).
 *
 * Uygulamayla gelmez; kullanıcı bir kez indirir. İndirmeyi Android'in indirme yöneticisi yapar, uygulama kapansa da
 * sürer.
 */
object LocalModel {

    /** Kullanıcıya gösterilen ad. */
    const val DISPLAY_NAME = "Gemma 4 E2B"

    private const val FILE_NAME = "gemma-4-E2B-it.litertlm"

    /**
     * Modelin adresi; hep aynı dosya insin diye belirli bir sürüme bağlı.
     *
     * SHA-256: 181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
     */
    private const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "2e12bfd869b2962984f97a1f2a26807316d534cc/gemma-4-E2B-it.litertlm"

    /** Dosyanın boyutu; indirmenin tamamlandığı buna bakılarak anlaşılır. */
    const val SIZE_BYTES: Long = 2_588_147_712L

    private const val DIR = "models"
    private const val PREFS = "local_model"
    private const val KEY_DOWNLOAD_ID = "download_id"

    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcher: Job? = null

    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.NotDownloaded)

    /** Modelin durumu. */
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** "Model indirildi" gibi kısa mesajlar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Uygulama açılırken bir kez çağrılır; süren bir indirme varsa izlemeye devam eder. */
    fun init(context: Context) {
        if (::context.isInitialized) return
        this.context = context.applicationContext
        refresh()
    }

    /** Model dosyası; telefonda eksiksiz yoksa null. */
    fun file(): File? = modelFile()?.takeIf { LocalModelPolicy.isComplete(it.length(), SIZE_BYTES) }

    @Synchronized
    fun refresh() {
        _state.value = when {
            !isDeviceSupported() -> LocalModelState.Unsupported
            file() != null -> LocalModelState.Ready
            else -> {
                val id = downloadId()
                if (id != null && poll(id)) {
                    watch(id)
                    _state.value
                } else {
                    if (file() != null) LocalModelState.Ready else LocalModelState.NotDownloaded
                }
            }
        }
    }

    /** İndirmeyi başlatır. Başlayamazsa nedenini, başladıysa null döner. */
    @Synchronized
    fun startDownload(): String? {
        when (_state.value) {
            LocalModelState.Ready, is LocalModelState.Downloading -> return null
            LocalModelState.Unsupported -> return Txt.localAi.unsupportedBody
            LocalModelState.NotDownloaded -> Unit
        }
        val dir = dir() ?: return Txt.localAi.noStorage
        val free = try {
            StatFs(dir.path).availableBytes
        } catch (e: Exception) {
            0L
        }
        if (!LocalModelPolicy.hasRoom(free, SIZE_BYTES)) return Txt.localAi.noSpace(sizeText)
        partFile()?.delete()
        val request = DownloadManager.Request(Uri.parse(URL))
            .setTitle(Txt.localAi.downloadTitle)
            .setDescription(DISPLAY_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, DIR, "$FILE_NAME.part")
            .setAllowedOverMetered(!AppSettings.wifiOnly.value)
            .setAllowedOverRoaming(false)
        val id = try {
            manager().enqueue(request)
        } catch (e: Exception) {
            return Txt.localAi.downloadFailed
        }
        prefs().edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        _state.value = LocalModelState.Downloading(0, SIZE_BYTES)
        watch(id)
        return null
    }

    /** İndirmeyi durdurur ve yarım dosyayı siler. */
    @Synchronized
    fun cancelDownload() {
        watcher?.cancel()
        downloadId()?.let { id -> runCatching { manager().remove(id) } }
        forgetDownload()
        partFile()?.delete()
        refresh()
    }

    /** Modeli telefondan siler. */
    @Synchronized
    fun delete() {
        LocalSummarizer.release()
        modelFile()?.delete()
        partFile()?.delete()
        refresh()
    }

    /** Modelin boyutu, okunur halde. */
    val sizeText: String
        get() = formatBytes(SIZE_BYTES)

    private fun isDeviceSupported(): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return LocalModelPolicy.isDeviceSupported(memory.totalMem, Build.SUPPORTED_ABIS.toList())
    }

    private fun dir(): File? = context.getExternalFilesDir(DIR)?.also { it.mkdirs() }

    private fun modelFile(): File? = dir()?.let { File(it, FILE_NAME) }

    private fun partFile(): File? = dir()?.let { File(it, "$FILE_NAME.part") }

    private fun manager(): DownloadManager = requireNotNull(context.getSystemService(DownloadManager::class.java))

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun downloadId(): Long? = prefs().getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it >= 0 }

    private fun forgetDownload() = prefs().edit().remove(KEY_DOWNLOAD_ID).apply()

    private fun watch(id: Long) {
        watcher?.cancel()
        watcher = scope.launch {
            while (isActive) {
                delay(1_000)
                val running = synchronized(this@LocalModel) { poll(id) }
                if (!running) break
            }
        }
    }

    /** İndirmenin durumunu sorar; indirme sürüyorsa true. */
    private fun poll(id: Long): Boolean {
        val cursor = try {
            manager().query(DownloadManager.Query().setFilterById(id))
        } catch (e: Exception) {
            null
        } ?: return false
        cursor.use { c ->
            if (!c.moveToFirst()) {
                forgetDownload()
                partFile()?.delete()
                _state.value = LocalModelState.NotDownloaded
                return false
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                .takeIf { it > 0 } ?: SIZE_BYTES
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    finish(id)
                    return false
                }
                DownloadManager.STATUS_FAILED -> {
                    fail(id, reason)
                    return false
                }
                DownloadManager.STATUS_PAUSED -> {
                    val waiting = reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK ||
                        reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI
                    _state.value = LocalModelState.Downloading(done, total, waitingForNetwork = waiting)
                }
                else -> _state.value = LocalModelState.Downloading(done, total)
            }
            return true
        }
    }

    /** İndirme bitti: boyut doğruysa dosya asıl adını alır. */
    private fun finish(id: Long) {
        forgetDownload()
        val part = partFile()
        val target = modelFile()
        val ok = part != null && target != null && LocalModelPolicy.isComplete(part.length(), SIZE_BYTES) &&
            part.renameTo(target)
        runCatching { manager().remove(id) }
        if (ok) {
            _state.value = LocalModelState.Ready
            _messages.tryEmit(Txt.localAi.downloaded)
        } else {
            part?.delete()
            _state.value = LocalModelState.NotDownloaded
            _messages.tryEmit(Txt.localAi.downloadFailed)
        }
    }

    private fun fail(id: Long, reason: Int) {
        forgetDownload()
        runCatching { manager().remove(id) }
        partFile()?.delete()
        _state.value = LocalModelState.NotDownloaded
        _messages.tryEmit(
            if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) Txt.localAi.noSpace(sizeText) else Txt.localAi.downloadFailed,
        )
    }
}
