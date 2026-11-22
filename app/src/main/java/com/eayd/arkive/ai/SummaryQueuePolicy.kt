package com.eayd.arkive.ai

/**
 * Sunucuya yüklenemeyen telefon özetinin bekleyip beklemeyeceğine karar verir. İnternet yoksa ya da sunucu geçici sorun
 * yaşıyorsa özet bekler, sonra yeniden denenir.
 */
object SummaryQueuePolicy {

    fun isRetryable(httpCode: Int?): Boolean = when {
        httpCode == null -> true
        httpCode >= 500 -> true
        httpCode == 401 || httpCode == 408 || httpCode == 429 -> true
        else -> false
    }
}
