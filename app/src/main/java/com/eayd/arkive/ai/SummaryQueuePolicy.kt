package com.eayd.arkive.ai

/**
 * Sunucuya yüklenemeyen telefon özetinin ne olacağını belirler. Android sınıfı kullanmaz, tek başına test edilir.
 *
 * Yanıt hiç gelmediyse (internet yok, zaman aşımı: [httpCode] null) ya da sunucu sorunu/geçici durum varsa (5xx, 408, 429,
 * oturum yenilenmesi için 401) özet telefonda saklanır ve sonra yeniden denenir. İstemci hatalarında (404 dosya silinmiş,
 * 413/415/422 desteklenmiyor…) yeniden denemek işe yaramaz: kayıt atılır.
 */
object SummaryQueuePolicy {

    fun isRetryable(httpCode: Int?): Boolean = when {
        httpCode == null -> true
        httpCode >= 500 -> true
        httpCode == 401 || httpCode == 408 || httpCode == 429 -> true
        else -> false
    }
}
