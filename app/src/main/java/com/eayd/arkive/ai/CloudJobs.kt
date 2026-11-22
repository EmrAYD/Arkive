package com.eayd.arkive.ai

import android.content.Context
import com.eayd.arkive.utils.writeTextAtomically
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import org.json.JSONObject

/** Sonucu henüz gelmemiş bir bulut özeti. */
data class CloudJob(val fileId: String, val fileName: String, val startedAtMs: Long)

/** Süren bulut özetlerinin telefondaki kaydı. Uygulama kapanıp açılınca bu işlere yeniden katılınır. */
object CloudJobs {

    private lateinit var file: File

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "ai_cloud_jobs.json")
    }

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    @Synchronized
    fun add(fileId: String, fileName: String, nowMs: Long = System.currentTimeMillis()) {
        val owner = uid() ?: return
        val root = readRoot()
        root.put(fileId, JSONObject().put("uid", owner).put("fileName", fileName).put("startedAt", nowMs))
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

    @Synchronized
    fun all(): List<CloudJob> {
        val owner = uid() ?: return emptyList()
        val root = readRoot()
        return root.keys().asSequence().mapNotNull { id ->
            val entry = root.optJSONObject(id) ?: return@mapNotNull null
            if (entry.optString("uid") != owner) return@mapNotNull null
            CloudJob(id, entry.optString("fileName"), entry.optLong("startedAt"))
        }.toList()
    }

    private fun readRoot(): JSONObject {
        if (!::file.isInitialized || !file.isFile) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun write(root: JSONObject) {
        if (!::file.isInitialized) return
        try {
            file.writeTextAtomically(root.toString())
        } catch (e: Exception) {}
    }
}
