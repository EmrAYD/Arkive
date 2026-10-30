package com.eayd.arkive.data

import com.eayd.arkive.utils.writeTextAtomically
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.io.File

/**
 * Klasör/dosya listelerinin telefondaki son görüntüsü. Sunucuya ulaşılamayınca (internet yok) uygulama bu görüntüyü
 * gösterir; böylece uygulama internetsiz açılsa bile liste görünür, telefonda kopyası olan dosyalar özetlenebilir.
 *
 * Her ekran (ana sayfa, AI sonuçları, Favorilerim, bir klasörün içi) için ayrı bir dosya tutulur:
 * `files/list_cache/<kullanıcı>_<ekran>.json`. Kayıtlar kullanıcıya özeldir; başka hesapla girilince görünmez.
 * Yalnızca okuma amaçlıdır: internetsizken sunucuya yazan işlemler (yükleme, silme, taşıma) yapılamaz.
 */
object ListCache {

    /** Bir ekranın listesi: [folder] içinde bulunulan klasör (kök ekranlarda null). */
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
        } catch (e: Exception) {
            // Önbellek yazılamadıysa liste yine de sunucudan gösterilir; yalnızca internetsiz görünüm eksik kalır.
        }
    }

    /** Bu kullanıcının kayıtlı listelerinden birinde geçen dosya (internetsiz ön izleme için dosya bilgisi). */
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
