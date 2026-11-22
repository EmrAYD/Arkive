package com.eayd.arkive.ai

import android.content.Context
import com.eayd.arkive.utils.writeTextAtomically
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Telefonda saklanan özet. [pending] henüz sunucuya yüklenmediğini söyler. */
data class StoredSummary(
    val fileId: String,
    val fileName: String,
    val summary: String,
    val pending: Boolean,
)

/**
 * Özetlerin telefondaki kopyası.
 *
 * Sunucuya yüklenemeyen özet burada bekler; yüklenmiş özetler de internetsiz açılabilsin diye burada tutulur.
 */
object SummaryStore {

    private lateinit var dir: File
    private val _pendingIds = MutableStateFlow<Set<String>>(emptySet())

    /** Yüklenmeyi bekleyen özetlerin dosya kimlikleri. */
    val pendingIds: StateFlow<Set<String>> = _pendingIds.asStateFlow()

    fun init(context: Context) {
        if (::dir.isInitialized) return
        val files = context.applicationContext.filesDir
        File(files, "ai_summaries").deleteRecursively()
        dir = File(files, "summaries").also { it.mkdirs() }
        publish()
    }

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun fileOf(fileId: String) = File(dir, "$fileId.json")

    @Synchronized
    fun put(entry: StoredSummary) {
        val owner = uid() ?: return
        val json = JSONObject()
            .put("uid", owner)
            .put("fileId", entry.fileId)
            .put("fileName", entry.fileName)
            .put("summary", entry.summary)
            .put("pending", entry.pending)
        fileOf(entry.fileId).writeTextAtomically(json.toString())
        publish()
    }

    @Synchronized
    fun get(fileId: String): StoredSummary? = read(fileOf(fileId))

    /** Yüklenmeyi bekleyen kayıtlar. */
    @Synchronized
    fun pending(): List<StoredSummary> = all().filter { it.pending }

    @Synchronized
    fun markUploaded(fileId: String) {
        get(fileId)?.let { put(it.copy(pending = false)) }
    }

    @Synchronized
    fun remove(fileId: String) {
        fileOf(fileId).delete()
        publish()
    }

    private fun all(): List<StoredSummary> =
        dir.listFiles { f -> f.name.endsWith(".json") }?.mapNotNull { read(it) }.orEmpty()

    private fun read(file: File): StoredSummary? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            if (json.optString("uid") != uid()) return null
            StoredSummary(
                fileId = json.getString("fileId"),
                fileName = json.optString("fileName"),
                summary = json.getString("summary"),
                pending = json.optBoolean("pending", false),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Bekleyen kümeyi şu anki kullanıcıya göre yeniden hesaplar; hesap değişince de çağrılır. */
    @Synchronized
    fun publish() {
        _pendingIds.value = all().filter { it.pending }.map { it.fileId }.toSet()
    }
}
