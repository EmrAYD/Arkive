package com.eayd.arkive.data

import com.eayd.arkive.ai.AiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusPlanTest {

    @Test
    fun `ucretsiz planda ayar ne olursa olsun yalnizca telefonda ozetlenir`() {
        for (mode in AiMode.entries) assertEquals(AiMode.Local, PlanPolicy.effectiveMode(mode, cloudAllowed = false))
    }

    @Test
    fun `plus planda kullanicinin ayari gecerlidir`() {
        for (mode in AiMode.entries) assertEquals(mode, PlanPolicy.effectiveMode(mode, cloudAllowed = true))
    }

    @Test
    fun `ucretsiz planda uc ozetten sonra sinir dolar`() {
        assertFalse(SummaryQuota("Free", 2, 3, false).full)
        assertTrue(SummaryQuota("Free", 3, 3, false).full)
        assertTrue(SummaryQuota("Free", 5, 3, false).full) // Plus'tan düşen kullanıcı
    }

    @Test
    fun `plus planda sinir yoktur`() {
        val plus = SummaryQuota("Plus", 500, null, true)
        assertFalse(plus.full)
        assertTrue(plus.isPlus)
        assertFalse(SummaryQuota("Free", 0, 3, false).isPlus)
    }

    @Test
    fun `mesajlar sinira ve plusa deginir`() {
        assertTrue(PlanPolicy.limitMessage(3).contains("en çok 3 dosya"))
        assertTrue(PlanPolicy.limitMessage(3).contains("Plus"))
        val big = PlanPolicy.needsCloudMessage("rapor.pdf", "Dosya yerelde özetlenemeyecek kadar büyük.")
        assertTrue(big.contains("“rapor.pdf”") && big.contains("Plus"))
        assertEquals("₺49,99 / ay", PlusPlan.PRICE_TEXT)
    }

    @Test
    fun `tanitim penceresi acilir ve kapanir`() {
        PlusOffer.show("sınır doldu")
        assertEquals("sınır doldu", PlusOffer.request.value?.reason)
        PlusOffer.show()
        assertNull(PlusOffer.request.value?.reason)
        PlusOffer.dismiss()
        assertNull(PlusOffer.request.value)
    }

    @Test
    fun `plan durumu guncellenir ve temizlenir`() {
        PlanStatus.update(SummaryQuota("Free", 1, 3, false))
        assertEquals(1, PlanStatus.quota.value?.count)
        PlanStatus.clear()
        assertNull(PlanStatus.quota.value)
    }
}
