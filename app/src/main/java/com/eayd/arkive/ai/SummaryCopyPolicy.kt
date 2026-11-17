package com.eayd.arkive.ai

/**
 * Özetin telefondaki kopyasıyla ilgili kurallar (Android'e bağlı değil; birim testleri bunları sınar). Bir dosyanın
 * özeti varsa telefonda ikisi birlikte tutulur: özetin metni ([SummaryStore]) ve "AI Sonuçlarım"daki PDF'in kopyası
 * ([InputCache]). Böylece sunucuya ulaşılamayınca da özet, PDF olarak açılır.
 */
object SummaryCopyPolicy {

    /** Özet PDF'inin telefonda kopyası en çok bu boyutta tutulur (sunucunun kabul ettiği telefon PDF'i sınırı kadar). */
    const val MAX_RESULT_COPY_BYTES: Long = 5L * 1024 * 1024

    /** Bu boyuttaki özet PDF'inin kopyası tutulabilir mi. */
    fun canKeepCopy(size: Long): Boolean = size in 1..MAX_RESULT_COPY_BYTES

    /**
     * Saklanacak metin: sunucunun verdiği metin boşsa (okunamadıysa) telefondaki önceki metin korunur. Böylece dolu bir
     * kopya boş bir yanıtla ezilmez.
     */
    fun keptText(server: String, previous: String?): String = server.takeIf { it.isNotBlank() } ?: previous.orEmpty()

    /**
     * Liste açılınca bu özetin sunucudan alınıp telefona yazılması gerekiyor mu. [stored]: telefondaki kayıt (yoksa
     * null), [hasCopy]: özet PDF'inin kopyası telefonda var mı. Yüklenmeyi bekleyen (telefonda üretilmiş) özet alınmaz.
     */
    fun needsFetch(stored: StoredSummary?, hasCopy: Boolean): Boolean {
        if (stored == null) return true
        if (stored.pending) return false
        if (stored.summary.isBlank()) return true
        val result = stored.resultFile ?: return true
        return !hasCopy && canKeepCopy(result.size)
    }
}
