package com.eayd.arkive.data

import com.eayd.arkive.utils.writeTextAtomically
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.io.File

/** Listelerin telefondaki son hali; internet yokken bu gösterilir. */
object ListCache {

    /** Bir ekranın listesi. */
    class Snapshot(
        val folder: FolderDto?,
        val folders: List<FolderDto>,
        val files: List<FileDto>,
    )

    private val gson = Gson()

    private fun dir(): File = File(AppSettings.appContext.filesDir, "list_cache").also { it.mkdirs() }

    private fun fileOf(screenKey: String): File? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val safe = "${uid}_$screenKey".replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir(), "$safe.json")
    }

    @Synchronized
    fun save(screenKey: String, snapshot: Snapshot) {
        val target = fileOf(screenKey) ?: return
        try {
            target.writeTextAtomically(gson.toJson(snapshot))
        } catch (e: Exception) {}
    }

    /** Kayıtlı listelerde geçen dosya; internetsiz ön izleme için. */
    @Synchronized
    fun findFile(fileId: String): FileDto? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val prefix = "${uid}_".replace(Regex("[^A-Za-z0-9_-]"), "_")
        val candidates = dir().listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".json") }.orEmpty()
        for (candidate in candidates) {
            val snapshot = try {
                gson.fromJson(candidate.readText(), Snapshot::class.java)
            } catch (e: Exception) {
                null
            } ?: continue
            snapshot.files.firstOrNull { it.id == fileId }?.let { return it }
        }
        return null
    }

    @Synchronized
    fun load(screenKey: String): Snapshot? {
        val source = fileOf(screenKey)?.takeIf { it.isFile } ?: return null
        return try {
            gson.fromJson(source.readText(), Snapshot::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
