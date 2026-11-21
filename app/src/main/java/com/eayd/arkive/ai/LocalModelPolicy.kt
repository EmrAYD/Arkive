package com.eayd.arkive.ai

/** Telefondaki yapay zeka modelinin durumu ([LocalModel.state]). */
sealed interface LocalModelState {
    /** Bu telefon modeli çalıştıramaz (bellek az ya da işlemci 64 bit değil). */
    data object Unsupported : LocalModelState

    /** Model indirilmedi. */
    data object NotDownloaded : LocalModelState

    /** Model iniyor. [waitingForNetwork]: indirme bir bağlantı (ya da Wi-Fi) bekliyor. */
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val waitingForNetwork: Boolean = false,
    ) : LocalModelState {
        /** 0-100; toplam bilinmiyorsa null. */
        val percent: Int?
            get() = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else null
    }

    /** Model telefonda, özetlemeye hazır. */
    data object Ready : LocalModelState
}

/**
 * Telefondaki modelin kuralları; Android sınıfı kullanmaz, tek başına test edilir. Model ve adresi [LocalModel]'dedir.
 */
object LocalModelPolicy {

    /**
     * Modelin çalışması için gereken en az toplam bellek (RAM). Gemma 4 E2B çalışırken ~1,7 GB bellek kullanır (CPU);
     * 6 GB'lık telefonlar Android'e 5,4-5,8 GB gösterir, 4 GB'lıklar ~3,6 GB. Eşik, 6 GB ve üstünü kabul eder.
     */
    const val MIN_RAM_BYTES: Long = 5_000_000_000L

    /** İndirme için modelin üstüne bırakılan boş alan payı. */
    const val SPACE_MARGIN_BYTES: Long = 300L * 1024 * 1024

    /** Çalıştırıcının (LiteRT-LM) yerel kitaplıklarının bulunduğu işlemci mimarileri. */
    private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

    /** Telefon modeli çalıştırabilir mi: yeterli bellek ve 64 bit işlemci. */
    fun isDeviceSupported(totalRamBytes: Long, abis: List<String>): Boolean =
        totalRamBytes >= MIN_RAM_BYTES && abis.any { it in SUPPORTED_ABIS }

    /** İndirme için yeterli boş alan var mı. */
    fun hasRoom(freeBytes: Long, modelBytes: Long): Boolean = freeBytes >= modelBytes + SPACE_MARGIN_BYTES

    /** İnen dosya eksiksiz mi (boyutu modelinkiyle aynı mı). */
    fun isComplete(fileBytes: Long, modelBytes: Long): Boolean = fileBytes == modelBytes
}
