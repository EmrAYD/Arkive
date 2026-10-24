package com.eayd.arkive.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * İndirilen dosyayı cihaza kaydeder; tarayıcı ya da başka bir uygulama devreye girmez. Her sürümde aynı yere,
 * genel "İndirilenler" klasöründeki `Arkive` alt klasörüne yazılır:
 *  - Android 10+: MediaStore ile (izin gerekmez). Aynı adlı dosya varsa sistem adı kendisi "ad (1).uzantı" yapar.
 *  - Android 9 ve eski: doğrudan klasöre yazılır; bunun için WRITE_EXTERNAL_STORAGE izni gerekir (izni
 *    istemek çağıran ekranın işidir, bkz. rememberFileDownloader) ve ardından dosya galeriye/dosya yöneticisine
 *    tanıtılır.
 */
object FileSaver {

    private const val SUBFOLDER = "Arkive"
    private const val FALLBACK_MIME = "application/octet-stream"

    /** [url]'deki dosyayı [fileName] adıyla kaydeder. Döner: kullanıcıya gösterilecek konum açıklaması. */
    suspend fun saveToDownloads(url: String, fileName: String, mimeType: String?): String =
        withContext(Dispatchers.IO) {
            val mime = mimeType?.takeIf { it.isNotBlank() } ?: FALLBACK_MIME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(AppSettings.appContext, url, fileName, mime)
            } else {
                saveToPublicFolder(AppSettings.appContext, url, fileName, mime)
            }
            "İndirilenler/$SUBFOLDER klasörüne kaydedildi"
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveWithMediaStore(context: Context, url: String, fileName: String, mime: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
            // Yazma bitene kadar dosya başka uygulamalara görünmez (yarım dosya listelenmesin).
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw StorageException("Dosya İndirilenler klasörüne kaydedilemedi.")
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw StorageException("Dosya İndirilenler klasörüne yazılamadı.")
            output.use { FileDownloader.copyTo(url, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            // Hata ya da iptalde yarım kalan kaydı sil.
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION") // Android 9 ve eskide genel klasöre erişmenin tek yolu bu.
    private suspend fun saveToPublicFolder(context: Context, url: String, fileName: String, mime: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBFOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw StorageException("İndirilenler klasörüne yazılamadı. Depolama izni verildi mi?")
        }
        val target = uniqueFile(dir, fileName)
        try {
            target.outputStream().use { FileDownloader.copyTo(url, it) }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        // Dosya yöneticisi / İndirilenler uygulaması dosyayı hemen görsün.
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
    }

    /** Klasörde aynı ad varsa "ad (1).uzantı", "ad (2).uzantı"… */
    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$ext")
            n++
        }
        return candidate
    }
}
