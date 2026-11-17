package com.eayd.arkive.data

import com.eayd.arkive.ai.AiMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plus planının tanıtımda gösterilen bilgileri. Sınırların kendisi sunucudadır (app/plans.py ve subscription_plans
 * tablosu); bunlar yalnızca tanıtım metinleridir, değişirse ikisi birlikte güncellenmelidir.
 */
object PlusPlan {
    const val NAME = "Plus"
    const val PRICE_TEXT = "₺49,99 / ay"
    const val STORAGE_TEXT = "20 GB"
    const val MAX_FILE_TEXT = "2 GB"
    const val FREE_STORAGE_TEXT = "100 MB"
    const val FREE_MAX_FILE_TEXT = "10 MB"
    const val FREE_SUMMARY_LIMIT = 3
}

/**
 * Kullanıcının planına göre yapay zeka hakları (sunucudaki GET /me yanıtından). [limit]: en çok kaç dosyanın özeti
 * olabilir (null: sınırsız); [count]: şu anki özet sayısı; [cloud]: bulutta özetleyebilir mi.
 */
data class SummaryQuota(val planName: String, val count: Int, val limit: Int?, val cloud: Boolean) {
    val isPlus: Boolean get() = planName.equals(PlusPlan.NAME, ignoreCase = true)

    /** Yeni bir dosya özetlenemez: sınır doldu. */
    val full: Boolean get() = limit != null && count >= limit
}

/** Plana bağlı özetleme kuralları ve mesajları. Hiçbir Android sınıfı kullanmaz (bkz. PlusPlanTest). */
object PlanPolicy {

    /** Bulut özeti yoksa (ücretsiz plan ya da plan bilinmiyor) ayar ne olursa olsun yalnızca telefonda özetlenir. */
    fun effectiveMode(mode: AiMode, cloudAllowed: Boolean): AiMode = if (cloudAllowed) mode else AiMode.Local

    fun limitMessage(limit: Int): String =
        "Ücretsiz planda en çok $limit dosya özetlenebilir. Sınırsız özet için Plus'a geçin."

    /** Dosya telefonda özetlenemiyor (büyük, görselli…) ve bulut özeti Plus'a özel. */
    fun needsCloudMessage(fileName: String, reason: String): String =
        "“$fileName” telefonda özetlenemiyor: $reason Büyük, uzun ve görselli dosyalar Plus planında bulutta özetlenir."

    const val CLOUD_MODE_MESSAGE =
        "Bulut ve Yerel + Bulut seçenekleri Plus planına özeldir. Ücretsiz planda dosyalar yalnızca telefonda özetlenir."
}

/**
 * Son bilinen plan hakları. Profil ve yan menü kullanım bilgisini her aldığında, AI düğmesine basıldığında da tazelenir.
 * İnternet yokken son bilinen değer kullanılır; hiç bilinmiyorsa (null) yalnızca telefonda özetlenir.
 */
object PlanStatus {
    private val _quota = MutableStateFlow<SummaryQuota?>(null)
    val quota: StateFlow<SummaryQuota?> = _quota.asStateFlow()

    fun update(quota: SummaryQuota) {
        _quota.value = quota
    }

    /** Oturum kapanınca: başka hesabın hakları kullanılmasın. */
    fun clear() {
        _quota.value = null
    }
}

/** Plus tanıtım penceresi açılsın. [reason]: neden açıldığı (ör. özet sınırı doldu); yalnızca tanıtım için null. */
data class PlusOfferRequest(val reason: String?)

/**
 * Plus tanıtımını (tam ekran pencere) açıp kapatan genel durum. Pencereyi ArkiveNavHost gösterir; böylece AI düğmesi, yan
 * menü ve profil gibi farklı yerlerden aynı pencere açılır.
 */
object PlusOffer {
    private val _request = MutableStateFlow<PlusOfferRequest?>(null)
    val request: StateFlow<PlusOfferRequest?> = _request.asStateFlow()

    fun show(reason: String? = null) {
        _request.value = PlusOfferRequest(reason)
    }

    fun dismiss() {
        _request.value = null
    }
}
