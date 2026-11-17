package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudJobPolicyTest {

    @Test
    fun `baglanti koparsa giderek seyrelen araliklarla yeniden baglanilir`() {
        assertEquals(2_000L, CloudJobPolicy.retryDelayMs(0))
        assertEquals(4_000L, CloudJobPolicy.retryDelayMs(1))
        val delays = generateSequence(0) { it + 1 }.map { CloudJobPolicy.retryDelayMs(it) }.takeWhile { it != null }
            .map { it!! }.toList()
        assertTrue(delays.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(delays.all { it <= 30_000L })
    }

    @Test
    fun `yeniden baglanma denemeleri sonunda biter ve yaklasik on dakika surer`() {
        var attempt = 0
        var total = 0L
        while (true) total += CloudJobPolicy.retryDelayMs(attempt++) ?: break
        assertNull(CloudJobPolicy.retryDelayMs(attempt))
        assertTrue(total in 10L * 60_000..12L * 60_000)
        assertNotNull(CloudJobPolicy.retryDelayMs(0))
    }

    @Test
    fun `uygulama acilinca yalnizca yakinda baslamis islere katilinir`() {
        val start = 1_000_000L
        assertTrue(CloudJobPolicy.shouldResume(start, start))
        assertTrue(CloudJobPolicy.shouldResume(start, start + CloudJobPolicy.RESUME_WINDOW_MS))
        assertFalse(CloudJobPolicy.shouldResume(start, start + CloudJobPolicy.RESUME_WINDOW_MS + 1))
        assertFalse(CloudJobPolicy.shouldResume(start, start - 1)) // saat geri alınmış: güvenilmez kayıt
    }
}
