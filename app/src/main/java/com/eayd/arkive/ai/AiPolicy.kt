package com.eayd.arkive.ai

import com.eayd.arkive.i18n.Txt

/**
 * Yapay zeka özetlemesinin kuralları: hangi dosyalar özetlenebilir ve hangileri telefonda özetlenebilir. Hiçbir Android
 * sınıfı kullanmaz; bu yüzden tek başına test edilebilir. Eşikleri değiştirmek için yalnızca bu dosyadaki sabitlere bakmak
 * yeter.
 *
 * Özetin nerede çıkarılacağı plana bağlıdır: Plus'ta her zaman sunucuda (bulutta); ücretsiz planda telefonda, cihazdaki
 * yapay zekayla ([LocalSummarizer]). Telefonda özetleme iki aşamada denetlenir:
 *  1. [localPrecheck]: dosyayı hiç açmadan (boyutuna bakarak),
 *  2. [localRejection]: dosya cihazda açılıp sayfa/görsel sayısı çıkarılınca.
 */

/** Özetlenebilen dosya türleri. [Docx]: Word belgeleri; hem .docx hem eski .doc (parolalıları dahil). */
enum class SummarizableKind { Text, Pdf, Docx }

/** Telefonda açılan belgenin ölçüleri. [pages] bilinmiyorsa null (düz metin, Word belgesi). */
data class DocumentStats(val words: Int, val pages: Int?, val images: Int)

/** Dosyanın yerelde özetlenememe nedeni. [message] kullanıcıya gösterilir. */
enum class LocalRejection {
    TooBig,
    TooManyPages,
    ImageHeavy,
    NoText,
    ;

    /** Seçili dilde açıklaması. */
    val message: String
        get() = when (this) {
            TooBig -> Txt.ai.rejectTooBig
            TooManyPages -> Txt.ai.rejectTooManyPages
            ImageHeavy -> Txt.ai.rejectImageHeavy
            NoText -> Txt.ai.rejectNoText
        }
}

object AiPolicy {

    /** Bundan büyük dosyalar yerelde özetlenmez (bayt). */
    const val LOCAL_MAX_BYTES: Long = 1L * 1024 * 1024

    /** Bundan çok sayfalı belgeler yerelde özetlenmez (PDF, DOCX ve düz metin). */
    const val LOCAL_MAX_PAGES: Int = 10

    /** Sayfa sayısı bilinmeyen belgede (düz metin, DOCX) sayfa, kelime sayısından kabaca hesaplanır: sayfa başına bu kadar kelime. */
    const val WORDS_PER_PAGE: Int = 500

    /** Belgenin sayfa sayısı: PDF'te gerçek değer, düz metin ve DOCX'te kelime sayısından tahmin ([WORDS_PER_PAGE]). */
    fun pagesOf(stats: DocumentStats): Int = stats.pages ?: ((stats.words + WORDS_PER_PAGE - 1) / WORDS_PER_PAGE)

    /** Bir PDF'te bu kadar ya da daha fazla görsel varsa ve sayfa başına oranı aşıldıysa "görsel ağırlıklı" sayılır. */
    const val IMAGE_HEAVY_MIN_IMAGES: Int = 4

    /** Sayfa başına en çok bu kadar görsel olan PDF metin ağırlıklı sayılır (0,5 = iki sayfada bir görsel). */
    const val IMAGE_HEAVY_PER_PAGE: Double = 0.5

    /** Sayfa başına bundan az kelime düşen PDF taranmış (resimden ibaret) sayılır. */
    const val SCANNED_MIN_WORDS_PER_PAGE: Int = 20

    /** Telefonda özetleme için dosyayı açmadan verilebilecek karar: çok büyükse nedeni, açılıp bakılabilecekse null. */
    fun localPrecheck(sizeBytes: Long): LocalRejection? = if (sizeBytes <= LOCAL_MAX_BYTES) null else LocalRejection.TooBig

    /**
     * Belge yerelde özetlenemiyorsa nedeni; uygunsa null. Boyut denetimi [localPrecheck]'tedir. Sayfa sınırı ilk bakılandır
     * (çok sayfalı PDF'in metni okunmaz, [stats] boş gelir; düz metin ve DOCX'te sayfa sayısı kelimeden tahmin edilir); görsel denetimi metinsizlikten önce gelir.
     */
    fun localRejection(stats: DocumentStats): LocalRejection? = when {
        pagesOf(stats) > LOCAL_MAX_PAGES -> LocalRejection.TooManyPages
        isImageHeavy(stats) -> LocalRejection.ImageHeavy
        stats.words == 0 -> LocalRejection.NoText
        else -> null
    }

    /**
     * Çok görselli mi? İki ayrı işaret vardır: (1) görsel sayısı hem yeterince çok hem de sayfa başına oranı yüksek;
     * (2) sayfaları metinden çok görselden ibaret (taranmış belge): görsel var ama sayfa başına neredeyse hiç kelime yok.
     */
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

    /**
     * Parola ile korunmuş bir Office dosyası (şifreli .docx) mı? Şifrelenen .docx, zip yerine eski OLE kapsayıcısına
     * sarılır ve içinde "EncryptedPackage" adlı bir akış bulunur (adı UTF-16 olarak saklanır). Eski .doc dosyaları da
     * OLE'dir ama bu akışı taşımaz; onlar bu işlevde şifreli sayılmaz (parolalı .doc'u [isLegacyOffice] yakalar ve
     * parolayı kendi okuyucusu ister).
     */
    fun isEncryptedOffice(bytes: ByteArray): Boolean = isOle(bytes) && indexOf(bytes, encryptedPackageName) >= 0

    /** Eski Office kapsayıcısı (OLE) mı ve şifreli .docx DEĞİL mi? Yani eski .doc (parolalı ya da parolasız). */
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

    // Backend'deki summarize.py ile aynı olmalı. docm/dotx/dotm özetlenmez (ön izleme açar, özet için desteklenmeyen tür).
    private val wordExtensions = setOf("docx", "doc", "dot")

    /**
     * Özetlenebilen dosya türleri: düz metin, PDF ve Word (DOCX ve eski DOC). Birini kapatmak için buradan çıkarın
     * (backend'de karşılığı SUMMARY_FILE_KINDS ayarıdır; "docx" her iki Word biçimini de kapsar).
     */
    val SUPPORTED_KINDS: Set<SummarizableKind> = setOf(SummarizableKind.Text, SummarizableKind.Pdf, SummarizableKind.Docx)

    /** Dosyanın hangi özetlenebilir sınıfa girdiği; desteklenmiyorsa (resim, video, ses…) null. */
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
