package com.eayd.arkive.ai

import com.eayd.arkive.data.PlanPolicy
import com.eayd.arkive.data.SummaryQuota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telefonda özetleme kuralları (ücretsiz plan) ve cihazdaki yapay zeka gelene kadarki davranış. */
class LocalSummaryPolicyTest {

    @Test
    fun `buyuk dosya acilmadan reddedilir`() {
        assertNull(AiPolicy.localPrecheck(AiPolicy.LOCAL_MAX_BYTES))
        assertEquals(LocalRejection.TooBig, AiPolicy.localPrecheck(AiPolicy.LOCAL_MAX_BYTES + 1))
    }

    @Test
    fun `acilan belge sayfa gorsel ve metne gore denetlenir`() {
        assertNull(AiPolicy.localRejection(DocumentStats(words = 800, pages = 3, images = 1)))
        assertEquals(LocalRejection.TooManyPages, AiPolicy.localRejection(DocumentStats(words = 9000, pages = 12, images = 0)))
        assertEquals(LocalRejection.TooManyPages, AiPolicy.localRejection(DocumentStats(words = 6000, pages = null, images = 0)))
        assertEquals(LocalRejection.ImageHeavy, AiPolicy.localRejection(DocumentStats(words = 40, pages = 4, images = 6)))
        assertEquals(LocalRejection.NoText, AiPolicy.localRejection(DocumentStats(words = 0, pages = null, images = 0)))
    }

    @Test
    fun `cihazdaki yapay zeka henuz yok ve ucretsiz planda bu soylenir`() {
        assertFalse(LocalSummarizer.available)
        assertTrue(PlanPolicy.LOCAL_AI_COMING_SOON.contains("Plus"))
    }

    @Test
    fun `ucretsiz plandaki ozet siniri`() {
        assertTrue(SummaryQuota("Free", count = 3, limit = 3, cloud = false).full)
        assertFalse(SummaryQuota("Free", count = 2, limit = 3, cloud = false).full)
        assertFalse(SummaryQuota("Plus", count = 50, limit = null, cloud = true).full)
    }
}
