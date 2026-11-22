package com.eayd.arkive.ai

import com.eayd.arkive.data.PlanPolicy
import com.eayd.arkive.data.SummaryQuota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telefonda özetleme kuralları ve özetin nerede çıkarılacağı. */
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
    fun `model calismayan telefonda Plus anlatilir`() {
        assertTrue(PlanPolicy.LOCAL_AI_UNSUPPORTED.contains("Plus"))
    }

    @Test
    fun `ucretsiz planda ayar ne olursa olsun yerel`() {
        for (mode in AiMode.entries) assertEquals(AiMode.Local, PlanPolicy.effectiveMode(mode, cloudAllowed = false))
        for (mode in AiMode.entries) assertEquals(mode, PlanPolicy.effectiveMode(mode, cloudAllowed = true))
        assertTrue(PlanPolicy.CLOUD_MODE_MESSAGE.contains("Plus"))
    }

    @Test
    fun `yerel her zaman telefonda`() {
        assertEquals(SummaryRoute.PhoneOnly, AiPolicy.route(AiMode.Local, modelReady = true, sizeBytes = 1_000))
        assertEquals(SummaryRoute.PhoneOnly, AiPolicy.route(AiMode.Local, modelReady = false, sizeBytes = 1_000))
        assertEquals(
            SummaryRoute.PhoneOnly,
            AiPolicy.route(AiMode.Local, modelReady = true, sizeBytes = AiPolicy.LOCAL_MAX_BYTES + 1),
        )
    }

    @Test
    fun `bulut her zaman sunucuda`() {
        assertEquals(SummaryRoute.Cloud, AiPolicy.route(AiMode.Cloud, modelReady = true, sizeBytes = 1_000))
        assertEquals(SummaryRoute.Cloud, AiPolicy.route(AiMode.Cloud, modelReady = false, sizeBytes = 1_000))
    }

    @Test
    fun `yerel arti bulutta model varsa kucuk dosya once telefonda`() {
        assertEquals(SummaryRoute.PhoneThenCloud, AiPolicy.route(AiMode.Hybrid, modelReady = true, sizeBytes = 1_000))
        assertEquals(
            SummaryRoute.PhoneThenCloud,
            AiPolicy.route(AiMode.Hybrid, modelReady = true, sizeBytes = AiPolicy.LOCAL_MAX_BYTES),
        )
    }

    @Test
    fun `yerel arti bulutta buyuk dosya ya da model hazir degilse bulutta`() {
        assertEquals(
            SummaryRoute.Cloud,
            AiPolicy.route(AiMode.Hybrid, modelReady = true, sizeBytes = AiPolicy.LOCAL_MAX_BYTES + 1),
        )
        assertEquals(SummaryRoute.Cloud, AiPolicy.route(AiMode.Hybrid, modelReady = false, sizeBytes = 1_000))
    }

    @Test
    fun `model yalnizca telefonda ozetlenecek dosya icin istenir`() {
        assertTrue(AiPolicy.wantsModel(AiMode.Local, 1_000))
        assertTrue(AiPolicy.wantsModel(AiMode.Hybrid, 1_000))
        assertFalse(AiPolicy.wantsModel(AiMode.Cloud, 1_000))
        assertFalse(AiPolicy.wantsModel(AiMode.Hybrid, AiPolicy.LOCAL_MAX_BYTES + 1))
        assertFalse(AiPolicy.wantsModel(AiMode.Local, AiPolicy.LOCAL_MAX_BYTES + 1))
    }

    @Test
    fun `ucretsiz plandaki ozet siniri`() {
        assertTrue(SummaryQuota("Free", count = 3, limit = 3, cloud = false).full)
        assertFalse(SummaryQuota("Free", count = 2, limit = 3, cloud = false).full)
        assertFalse(SummaryQuota("Plus", count = 50, limit = null, cloud = true).full)
    }
}
