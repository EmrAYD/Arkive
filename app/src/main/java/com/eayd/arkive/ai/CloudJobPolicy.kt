package com.eayd.arkive.ai

/**
 * Sunucudaki bulut özetlemesine yeniden bağlanma kuralları (Android'e bağlı değil; birim testleri bunları sınar).
 *
 * Bulut özetlemesi sunucuda uygulamanın bağlantısından bağımsız sürer. Bağlantı koparsa (uygulama arka planda, ağ
 * değişti) uygulama aynı isteği yeniden gönderir; sunucu yeni iş başlatmaz, süren işe katar ve sonucu döner.
 */
object CloudJobPolicy {

    /** Bağlantı koptuktan sonra yeniden bağlanma beklemeleri (ms); bitince vazgeçilir (toplam 10 dakikadan biraz fazla). */
    private val RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 15_000L) + List(20) { 30_000L }

    /** Uygulama yeniden açılınca, bu kadar eski bir bulut özetlemesine artık katılmaya çalışılmaz. */
    const val RESUME_WINDOW_MS: Long = 2L * 60 * 60 * 1000

    /** [attempt]. yeniden bağlanma denemesinden önce beklenecek süre; denemeler bittiyse null. */
    fun retryDelayMs(attempt: Int): Long? = RETRY_DELAYS_MS.getOrNull(attempt)

    /** [startedAtMs]'de başlamış bir bulut özetlemesine [nowMs] anında yeniden katılmaya çalışılır mı. */
    fun shouldResume(startedAtMs: Long, nowMs: Long): Boolean = nowMs - startedAtMs in 0..RESUME_WINDOW_MS
}
