package com.eayd.arkive.ai

import android.content.Context
import java.io.File

/**
 * Özetlenebilir küçük dosyaların telefondaki kopyası. Kopya telefonda varsa dosya internet olmadan da özetlenebilir
 * ve ön izlenebilir (internetsiz listede yalnızca kopyası olan dosyalar açılır). Kopyalar liste açılınca arka planda
 * alınır ([AiSummarizer.prefetch]); toplam boyut [MAX_TOTAL_BYTES]'ı aşınca en eskiler silinir.
 */
object InputCache {

    /** Tüm kopyaların toplam üst sınırı. */
    const val MAX_TOTAL_BYTES: Long = 40L * 1024 * 1024

    /** Bir pass'te en çok kaç dosya indirilir (liste uzunsa her açılışta biraz ilerler). */
    const val MAX_PER_PASS: Int = 20

    private lateinit var dir: File

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.applicationContext.filesDir, "ai_inputs").also { it.mkdirs() }
    }

    // Dosya adına boyut da yazılır: içerik değişmiş (aynı kimlikle farklı boyut) bir kopya eski sayılır.
    private fun target(fileId: String, size: Long) = File(dir, "${fileId}_$size")

    /** Telefondaki kopya; yoksa null. */
    @Synchronized
    fun get(fileId: String, size: Long): File? = target(fileId, size).takeIf { it.isFile && it.length() == size }

    /** İndirilmiş geçici dosyayı kopya olarak yerine taşır (başarısızsa geçici dosya silinmez; çağıran siler). */
    @Synchronized
    fun adopt(temp: File, fileId: String, size: Long): Boolean {
        if (!temp.isFile || temp.length() != size) return false
        evict(fileId)
        val ok = temp.renameTo(target(fileId, size))
        if (ok) trim()
        return ok
    }

    /** Bu dosyanın tüm kopyalarını siler. */
    @Synchronized
    fun evict(fileId: String) {
        dir.listFiles { f -> f.name.startsWith("${fileId}_") }?.forEach { it.delete() }
    }

    /** Geçici dosyalar için yer. */
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
