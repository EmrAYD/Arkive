package com.eayd.arkive.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.eayd.arkive.i18n.Txt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** İndirilen dosyayı İndirilenler/Arkive klasörüne kaydeder. */
object FileSaver {

    private const val SUBFOLDER = "Arkive"
    private const val FALLBACK_MIME = "application/octet-stream"

    /** Dosyayı kaydeder ve kullanıcıya gösterilecek konumu döner. */
    suspend fun saveToDownloads(url: String, fileName: String, mimeType: String?): String =
        withContext(Dispatchers.IO) {
            val mime = mimeType?.takeIf { it.isNotBlank() } ?: FALLBACK_MIME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(AppSettings.appContext, url, fileName, mime)
            } else {
                saveToPublicFolder(AppSettings.appContext, url, fileName, mime)
            }
            Txt.files.savedToDownloads(SUBFOLDER)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveWithMediaStore(context: Context, url: String, fileName: String, mime: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw StorageException(Txt.errors.saveToDownloadsFailed)
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw StorageException(Txt.errors.writeToDownloadsFailed)
            output.use { FileDownloader.copyTo(url, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveToPublicFolder(context: Context, url: String, fileName: String, mime: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBFOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw StorageException(Txt.errors.writeToDownloadsPermission)
        }
        val target = uniqueFile(dir, fileName)
        try {
            target.outputStream().use { FileDownloader.copyTo(url, it) }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
    }

    /** Aynı ad varsa "ad (1).uzantı" gibi yeni bir ad bulur. */
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
