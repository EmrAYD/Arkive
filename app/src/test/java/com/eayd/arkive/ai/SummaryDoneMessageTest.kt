package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryDoneMessageTest {

    @Test
    fun `kart dosya adini, ayrintiyi ve dokunma ipucunu soyler`() {
        assertEquals("“ders notu.docx” özetlendi", SummaryDoneMessage.fileLine("ders notu.docx"))
        assertTrue(SummaryDoneMessage.detail(false).contains("AI Sonuçlarım"))
        assertTrue("dokunulunca özete gidileceği yazmalı", SummaryDoneMessage.TAP_HINT.contains("dokunun"))
    }

    @Test
    fun `sunucuya yuklenemeyen ozetin ayrintisi bunu soyler`() {
        val detail = SummaryDoneMessage.detail(true)
        assertTrue(detail.contains("sunucuya ulaşılamadı", ignoreCase = true))
        assertTrue(detail.contains("bağlantı gelince yüklenecek"))
        assertNotEquals(SummaryDoneMessage.detail(false), detail)
    }

    @Test
    fun `sistem bildirimi baslik ve metni dokunmayi soyler`() {
        assertEquals("Özetleme bitti", SummaryDoneMessage.NOTIFICATION_TITLE)
        for (phoneOnly in listOf(false, true)) {
            val text = SummaryDoneMessage.notificationText("not.txt", phoneOnly)
            assertTrue(text.contains("“not.txt” özetlendi"))
            assertTrue(text.contains("dokunun"))
        }
        assertTrue(SummaryDoneMessage.notificationText("not.txt", true).contains("bağlantı gelince yüklenecek"))
    }

    @Test
    fun `uygulama arka plandaysa bildirim verilir`() {
        assertTrue(SummaryDoneDelivery.useNotification(appVisible = false, listeners = 1))
        assertTrue(SummaryDoneDelivery.useNotification(appVisible = false, listeners = 0))
    }

    @Test
    fun `uygulama ekrandaysa mesaj, mesajı gosterecek ekran yoksa bildirim`() {
        assertFalse(SummaryDoneDelivery.useNotification(appVisible = true, listeners = 1))
        assertTrue("ön izlemedeyken mesajı gösterecek ekran yok", SummaryDoneDelivery.useNotification(appVisible = true, listeners = 0))
    }

    @Test
    fun `bildirim istegi kimlik ve adi tasir, kimlik yoksa istek yoktur`() {
        assertEquals(SummaryOpenRequest("f1", "a.pdf"), SummaryOpenRequest.fromExtras("f1", "a.pdf"))
        assertEquals(SummaryOpenRequest("f1", ""), SummaryOpenRequest.fromExtras("f1", null))
        assertNull(SummaryOpenRequest.fromExtras(null, "a.pdf"))
        assertNull(SummaryOpenRequest.fromExtras("  ", "a.pdf"))
    }
}
