package com.eayd.arkive.ai

import android.content.Context
import java.io.File

/** Küçük dosyaların telefondaki kopyaları. Kopyası olan dosya internetsiz de açılır ve özetlenir. */
object InputCache {

    /** Tüm kopyaların toplam üst sınırı. */
    const val MAX_TOTAL_BYTES: Long = 40L * 1024 * 1024

    /** Bir seferde en çok kaç dosya indirilir. */
    const val MAX_PER_PASS: Int = 20

    private lateinit var dir: File

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.applicationContext.filesDir, "ai_inputs").also { it.mkdirs() }
    }

    private fun target(fileId: String, size: Long) = File(dir, "${fileId}_$size")

    /** Telefondaki kopya; yoksa null. */
    @Synchronized
    fun get(fileId: String, size: Long): File? = target(fileId, size).takeIf { it.isFile && it.length() == size }

    /** İndirilmiş geçici dosyayı kopya olarak yerine taşır. */
    @Synchronized
    fun adopt(temp: File, fileId: String, size: Long): Boolean {
        if (!temp.isFile || temp.length() != size) return false
        evict(fileId)
        val ok = temp.renameTo(target(fileId, size))
        if (ok) trim()
        return ok
    }

    /** [source]'un bir kopyasını tutar; [source] yerinde kalır. */
    @Synchronized
    fun keepCopy(source: File, fileId: String, size: Long): Boolean {
        if (!source.isFile || source.length() != size) return false
        if (get(fileId, size) != null) return true
        val temp = File(dir, "$fileId.copy.part")
        return try {
            source.copyTo(temp, overwrite = true)
            adopt(temp, fileId, size)
        } catch (e: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    /** Bu dosyanın kopyalarını siler. */
    @Synchronized
    fun evict(fileId: String) {
        dir.listFiles { f -> f.name.startsWith("${fileId}_") }?.forEach { it.delete() }
    }

    fun tempFile(fileId: String): File = File(dir, "$fileId.part")

    private fun trim() {
        val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".part") }?.sortedBy { it.lastModified() }.orEmpty()
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_TOTAL_BYTES) break
            total -= f.length()
            f.delete()
        }
    }
}
