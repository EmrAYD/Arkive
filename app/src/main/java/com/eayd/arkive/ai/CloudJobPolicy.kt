package com.eayd.arkive.ai

/** Bulut özetine yeniden bağlanma kuralları. */
object CloudJobPolicy {

    /** Bağlantı koptuktan sonraki bekleme süreleri; toplam 10 dakikadan biraz fazla. */
    private val RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 15_000L) + List(20) { 30_000L }

    /** Bundan eski bulut işlerine uygulama açılınca katılınmaz. */
    const val RESUME_WINDOW_MS: Long = 2L * 60 * 60 * 1000

    fun retryDelayMs(attempt: Int): Long? = RETRY_DELAYS_MS.getOrNull(attempt)

    fun shouldResume(startedAtMs: Long, nowMs: Long): Boolean = nowMs - startedAtMs in 0..RESUME_WINDOW_MS
}
