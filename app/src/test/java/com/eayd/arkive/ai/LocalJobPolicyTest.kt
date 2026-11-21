package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** Telefonda özetlenirken uygulama kapanan dosyanın, uygulama yeniden açılınca ne olacağı. */
class LocalJobPolicyTest {

    private val hour = 60L * 60 * 1000

    @Test
    fun `yerel ayarda telefonda bir kez daha baslatilir`() {
        assertEquals(LocalResume.OnPhone, LocalJobPolicy.decide(SummaryRoute.PhoneOnly, attempts = 1, startedAtMs = 0, nowMs = hour))
    }

    @Test
    fun `telefonda ikinci kez de yarida kaldiysa vazgecilir`() {
        assertEquals(
            LocalResume.GiveUp,
            LocalJobPolicy.decide(SummaryRoute.PhoneOnly, attempts = LocalJobPolicy.MAX_PHONE_ATTEMPTS, startedAtMs = 0, nowMs = hour),
        )
    }

    @Test
    fun `bulut kullanilabiliyorsa bulutta surdurulur`() {
        assertEquals(LocalResume.InCloud, LocalJobPolicy.decide(SummaryRoute.PhoneThenCloud, attempts = 1, startedAtMs = 0, nowMs = hour))
        assertEquals(LocalResume.InCloud, LocalJobPolicy.decide(SummaryRoute.Cloud, attempts = 5, startedAtMs = 0, nowMs = hour))
    }

    @Test
    fun `cok eski ya da gelecekteki kayit birakilir`() {
        val old = LocalJobPolicy.RESUME_WINDOW_MS + 1
        assertEquals(LocalResume.Drop, LocalJobPolicy.decide(SummaryRoute.PhoneOnly, attempts = 1, startedAtMs = 0, nowMs = old))
        assertEquals(LocalResume.Drop, LocalJobPolicy.decide(SummaryRoute.PhoneThenCloud, attempts = 1, startedAtMs = old, nowMs = 0))
    }
}
