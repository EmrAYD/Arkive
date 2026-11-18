package com.eayd.arkive.ai

import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.screens.auth.EulaBlock
import com.eayd.arkive.screens.auth.EulaContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Özet dili: telefonda üretilen özetin başlıkları seçilen dilde, cümleleri belgenin dilinde kalır. */
class SummaryLanguageTest {

    private val text = (1..12).joinToString(" ") { i ->
        "Cloud storage keeps documents safe and lets students reach their notes from any device number $i."
    }

    @Test
    fun `ingilizce ozette basliklar ingilizce`() {
        val summary = ExtractiveSummarizer.summarize(text, AppLanguage.English)
        assertTrue(summary, summary.startsWith("Summary: "))
        assertTrue(summary, "Key points:" in summary || "\n- " !in summary)
        assertTrue("Özet" !in summary)
    }

    @Test
    fun `varsayilan dil turkce`() {
        val summary = ExtractiveSummarizer.summarize(text)
        assertTrue(summary, summary.startsWith("Özet: "))
    }

    @Test
    fun `pdf bloklari iki dildeki ozet etiketini de tanir`() {
        val english = SummaryBlocks.parse("Summary: short\n\nKey points:\n- one\n- two")
        assertEquals(BlockKind.Heading, english.first().kind)
        assertEquals("Summary", english.first().text)
        assertEquals("short", english[1].text)
        val turkish = SummaryBlocks.parse("Özet: kısa")
        assertEquals("Özet", turkish.first().text)
    }

    @Test
    fun `sozlesmenin iki dili ayni yapida`() {
        val tr = EulaContent.forLanguage(AppLanguage.Turkish).blocks
        val en = EulaContent.forLanguage(AppLanguage.English).blocks
        assertEquals(tr.size, en.size)
        tr.zip(en).forEach { (a, b) -> assertEquals(a::class, b::class) }
        assertEquals(7, en.count { it is EulaBlock.Heading })
    }
}
