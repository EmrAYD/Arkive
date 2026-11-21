package com.eayd.arkive.ai

import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "Özetleme tamamlanamadı" bildiriminin metni. */
class SummaryFailedMessageTest {

    private var previous = Lang.app

    @Before
    fun turkish() {
        previous = Lang.app
        Lang.app = AppLanguage.Turkish
    }

    @After
    fun restore() {
        Lang.app = previous
    }

    @Test
    fun `aciklamada dosya adi geciyorsa oldugu gibi kalir`() {
        val message = "“rapor.pdf” özetlenemedi: Dosyadan okunabilir metin çıkarılamadı."
        assertEquals(message, SummaryFailedMessage.text("rapor.pdf", message))
    }

    @Test
    fun `aciklamada dosya adi yoksa basina eklenir`() {
        assertEquals("“rapor.pdf”: Sunucuya ulaşılamadı.", SummaryFailedMessage.text("rapor.pdf", "Sunucuya ulaşılamadı."))
    }

    @Test
    fun `aciklama yoksa yalnizca dosyanin ozetlenemedigi soylenir`() {
        assertEquals("“rapor.pdf” özetlenemedi.", SummaryFailedMessage.text("rapor.pdf", " "))
    }

    @Test
    fun `parola bildirimi dosyayi ve ne yapilacagini soyler`() {
        val text = SummaryFailedMessage.passwordText("gizli.docx")
        assertTrue(text.contains("gizli.docx") && text.contains("parola"))
    }
}
