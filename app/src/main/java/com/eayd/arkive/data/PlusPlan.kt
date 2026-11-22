package com.eayd.arkive.data

import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.i18n.Txt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plus tanıtımındaki bilgiler. Asıl sınırlar sunucudadır; değişirse ikisi birlikte güncellenmeli. */
object PlusPlan {
    const val NAME = "Plus"
    /** Seçili dile göre biçimlenmiş fiyat. */
    val PRICE_TEXT: String get() = Txt.plan.price
    const val STORAGE_TEXT = "20 GB"
    const val MAX_FILE_TEXT = "2 GB"
    const val FREE_STORAGE_TEXT = "100 MB"
    const val FREE_MAX_FILE_TEXT = "10 MB"
    const val FREE_SUMMARY_LIMIT = 3
}

/** Planın yapay zeka hakları. [limit] null ise sınırsız; [cloud] bulutta özetleyebilir mi. */
data class SummaryQuota(val planName: String, val count: Int, val limit: Int?, val cloud: Boolean) {
    val isPlus: Boolean get() = planName.equals(PlusPlan.NAME, ignoreCase = true)

    /** Özet sınırı doldu mu. */
    val full: Boolean get() = limit != null && count >= limit
}

/** Plana bağlı özetleme kuralları ve mesajları. */
object PlanPolicy {

    /** Bulut kullanılamıyorsa ayar ne olursa olsun yerel geçerlidir. */
    fun effectiveMode(mode: AiMode, cloudAllowed: Boolean): AiMode = if (cloudAllowed) mode else AiMode.Local

    fun limitMessage(limit: Int): String =
        Txt.plan.freeLimitReached(limit)

    /** Dosya telefonda özetlenemiyor ve bulut Plus'a özel. */
    fun needsCloudMessage(fileName: String, reason: String): String =
        Txt.plan.cantSummarizeOnPhone(fileName, reason)

    /** Sunucunun 402 yanıtı okunamazsa gösterilen mesaj. */
    val CLOUD_PLUS_ONLY_MESSAGE: String
        get() = Txt.plan.cloudPlusOnly

    /** Bu telefon modeli çalıştıramıyor. */
    val LOCAL_AI_UNSUPPORTED: String
        get() = Txt.plan.localAiUnsupported

    /** Ücretsiz planda bulut seçilince gösterilen mesaj. */
    val CLOUD_MODE_MESSAGE: String
        get() = Txt.aiMode.cloudPlusOnly
}

/** Son bilinen plan hakları; internet yokken bu kullanılır. */
object PlanStatus {
    private val _quota = MutableStateFlow<SummaryQuota?>(null)
    val quota: StateFlow<SummaryQuota?> = _quota.asStateFlow()

    fun update(quota: SummaryQuota) {
        _quota.value = quota
    }

    /** Oturum kapanınca hakları siler. */
    fun clear() {
        _quota.value = null
    }
}

/** Plus penceresini açma isteği. [reason] neden açıldığı. */
data class PlusOfferRequest(val reason: String?)

/** Plus tanıtım penceresinin durumu; pencere her yerden aynı şekilde açılır. */
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
