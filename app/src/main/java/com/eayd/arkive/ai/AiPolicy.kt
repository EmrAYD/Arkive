package com.eayd.arkive.ai

import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.formatBytes

/** Özetlenebilen dosya türleri. Docx, eski .doc dosyalarını da kapsar. */
enum class SummarizableKind { Text, Pdf, Docx }

/** Telefonda açılan belgenin kelime, sayfa ve görsel sayısı. Sayfa sayısı bilinmiyorsa null. */
data class DocumentStats(val words: Int, val pages: Int?, val images: Int)

/** Dosyanın telefonda özetlenememe nedeni. */
enum class LocalRejection {
    TooBig,
    TooManyPages,
    ImageHeavy,
    NoText,
    ;

    val message: String
        get() = when (this) {
            TooBig -> Txt.ai.rejectTooBig
            TooManyPages -> Txt.ai.rejectTooManyPages
            ImageHeavy -> Txt.ai.rejectImageHeavy
            NoText -> Txt.ai.rejectNoText
        }
}

/** Profildeki "Yapay zeka işlem yeri" ayarı. Ücretsiz planda her zaman [Local] geçerlidir. */
enum class AiMode {
    /** Yalnızca telefonda. */
    Local,

    /** Her zaman sunucuda. */
    Cloud,

    /** Küçük dosyalar telefonda, gerisi sunucuda. */
    Hybrid,
    ;

    val allowsLocal: Boolean get() = this != Cloud
    val allowsCloud: Boolean get() = this != Local

    val label: String
        get() = when (this) {
            Local -> Txt.aiMode.local
            Cloud -> Txt.aiMode.cloud
            Hybrid -> Txt.aiMode.hybrid
        }

    val description: String
        get() = when (this) {
            Local -> Txt.aiMode.localDetail(formatBytes(AiPolicy.LOCAL_MAX_BYTES), AiPolicy.LOCAL_MAX_PAGES)
            Cloud -> Txt.aiMode.cloudDetail
            Hybrid -> Txt.aiMode.hybridDetail(formatBytes(AiPolicy.LOCAL_MAX_BYTES), AiPolicy.LOCAL_MAX_PAGES)
        }
}

/** Dosyanın nerede özetleneceği. */
enum class SummaryRoute {
    /** Yalnızca telefonda. */
    PhoneOnly,

    /** Önce telefonda, olmazsa bulutta. */
    PhoneThenCloud,

    /** Doğrudan bulutta. */
    Cloud,
}

/** Yapay zeka özetlemesinin kuralları. Android'e bağlı değil, testlerle denenir. */
object AiPolicy {

    /** Telefonda özetlenebilecek en büyük dosya. */
    const val LOCAL_MAX_BYTES: Long = 1L * 1024 * 1024

    /** Telefonda özetlenebilecek en uzun belge. */
    const val LOCAL_MAX_PAGES: Int = 10

    /** Sayfa sayısı bilinmeyen belgede bir sayfanın kaç kelime sayıldığı. */
    const val WORDS_PER_PAGE: Int = 500

    /** PDF'te gerçek sayfa sayısı, diğerlerinde kelimeden tahmin. */
    fun pagesOf(stats: DocumentStats): Int = stats.pages ?: ((stats.words + WORDS_PER_PAGE - 1) / WORDS_PER_PAGE)

    /** Bundan az görseli olan PDF görsel ağırlıklı sayılmaz. */
    const val IMAGE_HEAVY_MIN_IMAGES: Int = 4

    /** Sayfa başına bundan fazla görsel varsa PDF görsel ağırlıklıdır. */
    const val IMAGE_HEAVY_PER_PAGE: Double = 0.5

    /** Sayfa başına bundan az kelime varsa PDF taranmış sayılır. */
    const val SCANNED_MIN_WORDS_PER_PAGE: Int = 20

    /** Dosyayı açmadan, yalnızca boyutuna bakarak telefona uygun olup olmadığını söyler. */
    fun localPrecheck(sizeBytes: Long): LocalRejection? = if (sizeBytes <= LOCAL_MAX_BYTES) null else LocalRejection.TooBig

    /**
     * Dosyanın telefonda mı bulutta mı özetleneceğini seçer.
     *
     * Model hazır değilse "Yerel + Bulut" da buluta gider.
     */
    fun route(mode: AiMode, modelReady: Boolean, sizeBytes: Long): SummaryRoute = when (mode) {
        AiMode.Local -> SummaryRoute.PhoneOnly
        AiMode.Cloud -> SummaryRoute.Cloud
        AiMode.Hybrid ->
            if (modelReady && localPrecheck(sizeBytes) == null) SummaryRoute.PhoneThenCloud else SummaryRoute.Cloud
    }

    /** Bu dosya için telefonda model gerekiyor mu. */
    fun wantsModel(mode: AiMode, sizeBytes: Long): Boolean = mode.allowsLocal && localPrecheck(sizeBytes) == null

    /** Açılan belge telefonda özetlenemiyorsa nedenini döner, uygunsa null. */
    fun localRejection(stats: DocumentStats): LocalRejection? = when {
        pagesOf(stats) > LOCAL_MAX_PAGES -> LocalRejection.TooManyPages
        isImageHeavy(stats) -> LocalRejection.ImageHeavy
        stats.words == 0 -> LocalRejection.NoText
        else -> null
    }

    /** Belge çok görselli ya da taranmış mı. */
    fun isImageHeavy(stats: DocumentStats): Boolean {
        if (stats.images <= 0) return false
        val pages = stats.pages?.coerceAtLeast(1)
        val manyImages = stats.images >= IMAGE_HEAVY_MIN_IMAGES &&
            (pages == null || stats.images.toDouble() / pages > IMAGE_HEAVY_PER_PAGE)
        val scanned = pages != null && stats.words.toDouble() / pages < SCANNED_MIN_WORDS_PER_PAGE
        return manyImages || scanned
    }

    private val oleMagic = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    private val encryptedPackageName = "EncryptedPackage".toByteArray(Charsets.UTF_16LE)

    private fun isOle(bytes: ByteArray): Boolean =
        bytes.size > oleMagic.size && oleMagic.indices.all { bytes[it] == oleMagic[it] }

    /** Parolalı .docx mi? Şifreli .docx dosyaları eski OLE kapsayıcısının içinde gelir. */
    fun isEncryptedOffice(bytes: ByteArray): Boolean = isOle(bytes) && indexOf(bytes, encryptedPackageName) >= 0

    /** Eski .doc dosyası mı (parolalı ya da parolasız). */
    fun isLegacyOffice(bytes: ByteArray): Boolean = isOle(bytes) && !isEncryptedOffice(bytes)

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (start in 0..haystack.size - needle.size) {
            for (i in needle.indices) if (haystack[start + i] != needle[i]) continue@outer
            return start
        }
        return -1
    }

    private val textExtensions = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "yaml", "yml", "log", "ini", "conf", "html", "htm",
    )

    private val wordMimeTypes = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/vnd.ms-word",
    )

    private val wordExtensions = setOf("docx", "doc", "dot")

    /**
     * Özetlenebilen türler. Bir türü kapatmak için buradan çıkarın; backend'deki SUMMARY_FILE_KINDS ile aynı olmalı.
     */
    val SUPPORTED_KINDS: Set<SummarizableKind> = setOf(SummarizableKind.Text, SummarizableKind.Pdf, SummarizableKind.Docx)

    /** Dosyanın özetlenebilir türü; desteklenmiyorsa null. */
    fun kindOf(mimeType: String?, fileName: String): SummarizableKind? {
        val mime = mimeType?.lowercase().orEmpty()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val kind = when {
            mime == "application/pdf" || ext == "pdf" -> SummarizableKind.Pdf
            mime in wordMimeTypes || ext in wordExtensions -> SummarizableKind.Docx
            mime.startsWith("text/") || ext in textExtensions -> SummarizableKind.Text
            else -> null
        }
        return kind?.takeIf { it in SUPPORTED_KINDS }
    }
}
