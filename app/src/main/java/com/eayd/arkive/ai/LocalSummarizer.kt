package com.eayd.arkive.ai

import com.eayd.arkive.i18n.AppLanguage

/**
 * Telefonda, cihazdaki yapay zeka modeliyle özetleme. Ücretsiz planda özetler burada çıkarılır (plan sınırı: en çok
 * [com.eayd.arkive.data.PlusPlan.FREE_SUMMARY_LIMIT] özet; sunucu da kaydederken denetler).
 *
 * Model henüz eklenmedi: [available] false olduğu sürece ücretsiz planda AI düğmesi "yakında" der ve Plus'ı tanıtır.
 * Model eklenince [available] true yapılır ve [summarize] modeli çalıştırır. Geri kalan akış hazırdır ([AiSummarizer]):
 * dosya telefonda açılır ([DocumentReader]), [AiPolicy] telefona uygun olup olmadığına bakar, çıkan özet önce telefona
 * sonra sunucuya (PUT /files/{id}/summary) yazılır; internet yoksa bağlantı gelince yüklenir.
 */
object LocalSummarizer {

    /** Cihazdaki model kullanılabilir mi. */
    val available: Boolean
        get() = false

    /**
     * Belgenin ne olduğunu ve neleri içerdiğini anlatan tek paragraflık özet ([language] dilinde; bulut özetiyle aynı
     * biçim: "Bu belge … içeriyor."). Özet çıkarılamazsa boş metin döner.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun summarize(text: String, language: AppLanguage): String =
        throw UnsupportedOperationException("Telefonda özetleme henüz yok")
}
