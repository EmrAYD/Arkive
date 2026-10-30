package com.eayd.arkive.ai

/**
 * Yapay zeka özetlemesinin NEREDE yapılacağına karar veren kurallar. Hiçbir Android sınıfı kullanmaz; bu yüzden
 * tek başına test edilebilir (bkz. AiPolicyTest). Eşikleri değiştirmek için yalnızca bu dosyadaki sabitlere bakmak yeter.
 *
 * Yerel özetleme [ExtractiveSummarizer] ile yapılır (yapay zeka modeli yoktur): belgenin en önemli cümlelerini seçer.
 *
 * Karar iki aşamadır:
 *  1. [precheck]: dosyayı hiç açmadan bilinenlerle (ayar, dosya boyutu) karar verilir.
 *     Yerel özetleme hala mümkünse sonuç [Precheck.Inspect] olur.
 *  2. [afterInspection]: dosya cihazda açılıp sayfa/görsel sayısı çıkarılınca son karar verilir.
 */

/** Ayarlardaki "yapay zeka işlem yeri". */
enum class AiMode(val label: String, val description: String) {
    Local("Yerel", "Özetleme yalnızca bu telefonda yapılır (telefonda kayıtlı dosyalarda internet gerekmez); özet belgenin en önemli cümlelerinden oluşur. Yalnızca küçük (en çok 1 MB), metin ağırlıklı dosyalar özetlenebilir."),
    Cloud("Bulut", "Özetleme her zaman sunucudaki yapay zeka ile yapılır; özet yeniden yazılır. Büyük ve görsel ağırlıklı dosyalar dahil hepsi özetlenebilir."),
    Hybrid("Yerel + Bulut", "Uygun dosyalar telefonda, uygun olmayanlar (büyük, çok görselli) sunucuda özetlenir.");

    val allowsLocal: Boolean get() = this != Cloud
    val allowsCloud: Boolean get() = this != Local
}

/** Özetlenebilen dosya türleri. */
enum class SummarizableKind { Text, Pdf, Docx }

/** Telefonda açılan belgenin ölçüleri. [pages] bilinmiyorsa null (düz metin, DOCX). */
data class DocumentStats(val words: Int, val pages: Int?, val images: Int)

/** Dosyanın yerelde özetlenememe nedeni. [message] kullanıcıya gösterilir. */
enum class LocalRejection(val message: String) {
    TooBig("Dosya yerelde özetlenemeyecek kadar büyük."),
    ImageHeavy("Dosya çok fazla görsel içeriyor."),
    NoText("Dosyadan okunabilir metin çıkarılamadı."),
}

sealed interface Precheck {
    data object Cloud : Precheck
    data object Inspect : Precheck
    data class Blocked(val reason: LocalRejection) : Precheck
}

sealed interface Route {
    data object Local : Route
    data object Cloud : Route
    data class Blocked(val reason: LocalRejection) : Route
}

object AiPolicy {

    /** Bundan büyük dosyalar yerelde özetlenmez (bayt). */
    const val LOCAL_MAX_BYTES: Long = 1L * 1024 * 1024

    /** Bir PDF'te bu kadar ya da daha fazla görsel varsa ve sayfa başına oranı aşıldıysa "görsel ağırlıklı" sayılır. */
    const val IMAGE_HEAVY_MIN_IMAGES: Int = 4

    /** Sayfa başına en çok bu kadar görsel olan PDF metin ağırlıklı sayılır (0,5 = iki sayfada bir görsel). */
    const val IMAGE_HEAVY_PER_PAGE: Double = 0.5

    /** Sayfa başına bundan az kelime düşen PDF taranmış (resimden ibaret) sayılır. */
    const val SCANNED_MIN_WORDS_PER_PAGE: Int = 20

    /** Dosyayı açmadan verilebilecek karar. */
    fun precheck(mode: AiMode, sizeBytes: Long): Precheck = when {
        !mode.allowsLocal -> Precheck.Cloud
        sizeBytes <= LOCAL_MAX_BYTES -> Precheck.Inspect
        mode.allowsCloud -> Precheck.Cloud
        else -> Precheck.Blocked(LocalRejection.TooBig)
    }

    /** Belge açıldıktan sonra son karar. Yerel uygunsa yerel; değilse (ayara göre) bulut ya da engel. */
    fun afterInspection(mode: AiMode, stats: DocumentStats): Route {
        val reason = localRejection(stats)
        return when {
            reason == null -> Route.Local
            // Metin hiç yoksa sunucu da özetleyemez (sunucu da metinle çalışır): buluta gitmenin anlamı yok.
            reason == LocalRejection.NoText -> Route.Blocked(reason)
            mode.allowsCloud -> Route.Cloud
            else -> Route.Blocked(reason)
        }
    }

    /** Belge yerelde özetlenemiyorsa nedeni; uygunsa null. Boyut denetimi [precheck]'tedir. Görsel denetimi metinsizlikten önce gelir. */
    fun localRejection(stats: DocumentStats): LocalRejection? = when {
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

    /**
     * Parola ile korunmuş bir Office dosyası (şifreli .docx) mı? Şifrelenen .docx, zip yerine eski OLE kapsayıcısına
     * sarılır ve içinde "EncryptedPackage" adlı bir akış bulunur (adı UTF-16 olarak saklanır). Eski .doc dosyaları da
     * OLE'dir ama bu akışı taşımaz; onlar şifreli sayılmaz.
     */
    fun isEncryptedOffice(bytes: ByteArray): Boolean =
        bytes.size > oleMagic.size && oleMagic.indices.all { bytes[it] == oleMagic[it] } && indexOf(bytes, encryptedPackageName) >= 0

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

    /**
     * Özetlenebilen dosya türleri: düz metin, PDF ve DOCX. Birini kapatmak için buradan çıkarın (backend'de karşılığı
     * SUMMARY_FILE_KINDS ayarıdır).
     */
    val SUPPORTED_KINDS: Set<SummarizableKind> = setOf(SummarizableKind.Text, SummarizableKind.Pdf, SummarizableKind.Docx)

    /** Dosyanın hangi özetlenebilir sınıfa girdiği; desteklenmiyorsa (resim, video, ses, eski .doc…) null. */
    fun kindOf(mimeType: String?, fileName: String): SummarizableKind? {
        val mime = mimeType?.lowercase().orEmpty()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val kind = when {
            mime == "application/pdf" || ext == "pdf" -> SummarizableKind.Pdf
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || ext == "docx" ->
                SummarizableKind.Docx
            mime.startsWith("text/") || ext in textExtensions -> SummarizableKind.Text
            else -> null
        }
        return kind?.takeIf { it in SUPPORTED_KINDS }
    }
}
