package com.eayd.arkive.ai

import android.content.Context
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.utils.writeTextAtomically
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import org.json.JSONObject

/** Telefonda özetlenmeye başlanmış, henüz bitmemiş bir dosya. */
data class LocalJob(val file: FileDto, val startedAtMs: Long, val attempts: Int)

/** Telefonda süren özetlerin kaydı. Özetleme bitince silinir; kayıt kaldıysa uygulama o sırada kapanmıştır. */
object LocalJobs {

    private lateinit var store: File

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = File(context.applicationContext.filesDir, "ai_local_jobs.json")
    }

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /** Dosya telefonda özetlenmeye başlıyor; eski kaydı varsa deneme sayısı artar. */
    @Synchronized
    fun begin(file: FileDto, nowMs: Long = System.currentTimeMillis()) {
        val owner = uid() ?: return
        val root = readRoot()
        val previous = root.optJSONObject(file.id)?.takeIf { it.optString("uid") == owner }?.optInt("attempts") ?: 0
        root.put(
            file.id,
            JSONObject()
                .put("uid", owner)
                .put("startedAt", nowMs)
                .put("attempts", previous + 1)
                .put("folderId", file.folderId ?: JSONObject.NULL)
                .put("name", file.name)
                .put("size", file.size)
                .put("mimeType", file.mimeType ?: JSONObject.NULL)
                .put("favorite", file.favorite)
                .put("status", file.status)
                .put("createdAt", file.createdAt),
        )
        write(root)
    }

    @Synchronized
    fun remove(fileId: String) {
        val root = readRoot()
        if (root.has(fileId)) {
            root.remove(fileId)
            write(root)
        }
    }

    /** Bu kullanıcının yarıda kalmış işleri. */
    @Synchronized
    fun all(): List<LocalJob> {
        val owner = uid() ?: return emptyList()
        val root = readRoot()
        return root.keys().asSequence().mapNotNull { id ->
            val e = root.optJSONObject(id) ?: return@mapNotNull null
            if (e.optString("uid") != owner) return@mapNotNull null
            val file = FileDto(
                id = id,
                folderId = e.optStringOrNull("folderId"),
                name = e.optString("name"),
                size = e.optLong("size"),
                mimeType = e.optStringOrNull("mimeType"),
                favorite = e.optBoolean("favorite"),
                status = e.optString("status", "ready"),
                createdAt = e.optString("createdAt"),
            )
            LocalJob(file, e.optLong("startedAt"), e.optInt("attempts", 1))
        }.toList()
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    private fun readRoot(): JSONObject {
        if (!::store.isInitialized || !store.isFile) return JSONObject()
        return try {
            JSONObject(store.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun write(root: JSONObject) {
        if (!::store.isInitialized) return
        try {
            store.writeTextAtomically(root.toString())
        } catch (e: Exception) {}
    }
}
