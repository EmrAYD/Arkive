package com.eayd.arkive.ai

import android.content.Context
import com.eayd.arkive.utils.writeTextAtomically
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import org.json.JSONObject

/** Sunucuda başlatılmış, sonucu henüz telefona ulaşmamış bir bulut özetlemesi. */
data class CloudJob(val fileId: String, val fileName: String, val startedAtMs: Long)

/**
 * Sunucuda süren bulut özetlemelerinin telefondaki listesi. Uygulama özetleme sürerken kapatılırsa sunucu işi bitirir;
 * uygulama yeniden açılınca bu listedeki işlere yeniden katılınır ([AiSummarizer.start]): satırda yükleme simgesi döner,
 * iş bitince "Özetleme bitti" gösterilir. Kayıtlar oturum açmış kullanıcıya aittir (`files/ai_cloud_jobs.json`).
 */
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

    /** Bu kullanıcının kayıtlı işleri. */
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
            JSONObject() // bozuk dosya yok sayılır
        }
    }

    private fun write(root: JSONObject) {
        if (!::file.isInitialized) return
        try {
            file.writeTextAtomically(root.toString())
        } catch (e: Exception) {
            // Yazılamazsa yalnızca "uygulama açılınca yeniden katıl" eksik kalır; özetleme sunucuda yine biter.
        }
    }
}
