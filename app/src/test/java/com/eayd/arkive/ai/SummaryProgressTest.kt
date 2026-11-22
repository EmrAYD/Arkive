package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Özetleme yüzdesinin tahmini. */
class SummaryProgressTest {

    private fun at(phase: SummaryPhase, current: Int, seconds: Double): Int {
        val run = SummaryProgress.start(phase, current, nowMs = 0)
        return SummaryProgress.percent(run, nowMs = (seconds * 1000).toLong())
    }

    @Test
    fun `asama basinda araligin basindan baslar ve zamanla sonuna yaklasir ama varmaz`() {
        val phase = SummaryPhase.InCloud(sizeBytes = 0)
        assertEquals(SummaryProgress.range(phase).first, at(phase, 0, 0.0))
        val values = listOf(1.0, 5.0, 15.0, 60.0, 600.0).map { at(phase, 0, it) }
        assertEquals(values.sorted(), values)
        assertTrue(values.last() <= SummaryProgress.MAX)
        assertTrue(at(phase, 0, 15.0) in 80..SummaryProgress.MAX)
    }

    @Test
    fun `telefondaki adimlar araligi esit boler`() {
        val first = SummaryProgress.range(SummaryPhase.OnPhone(1, 4))
        val last = SummaryProgress.range(SummaryPhase.OnPhone(4, 4))
        assertEquals(SummaryProgress.range(SummaryPhase.LoadingModel).last, first.first)
        assertEquals(SummaryProgress.MAX, last.last)
        assertEquals(SummaryProgress.range(SummaryPhase.OnPhone(2, 4)).first, first.last)
        assertEquals(SummaryProgress.range(SummaryPhase.OnPhone(1, 1)).last, SummaryProgress.MAX)
    }

    @Test
    fun `yuzde geri gitmez`() {
        assertEquals(60, at(SummaryPhase.InCloud(0), current = 60, seconds = 0.0))
        assertTrue(at(SummaryPhase.InCloud(0), current = 60, seconds = 10.0) > 60)
        assertEquals(40, at(SummaryPhase.LoadingModel, current = 40, seconds = 30.0))
    }

    @Test
    fun `buyuk dosyada bulut daha uzun beklenir`() {
        val small = SummaryProgress.expectedMs(SummaryPhase.InCloud(100_000))
        val big = SummaryProgress.expectedMs(SummaryPhase.InCloud(8L * 1024 * 1024))
        assertTrue(big > small)
        assertTrue(SummaryProgress.expectedMs(SummaryPhase.InCloud(5L * 1024 * 1024 * 1024)) <= 180_000)
    }
}
