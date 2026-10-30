package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryBlocksTest {

    private fun h(t: String) = SummaryBlock(BlockKind.Heading, t)
    private fun p(t: String) = SummaryBlock(BlockKind.Paragraph, t)
    private fun b(t: String, m: String) = SummaryBlock(BlockKind.Bullet, t, m)

    @Test
    fun markdownBulletsNumbersAndLabelsAreSplitIntoBlocks() {
        val blocks = SummaryBlocks.parse("# Başlık\nBir satır\nikinci satır\n\n- madde\n3. numaralı\n**Kalın başlık**\nÖzet: kısa metin")
        assertEquals(
            listOf(h("Başlık"), p("Bir satır ikinci satır"), b("madde", "•"), b("numaralı", "3."), h("Kalın başlık"), h("Özet"), p("kısa metin")),
            blocks,
        )
    }

    @Test
    fun phoneSummaryFormatIsRecognised() {
        val blocks = SummaryBlocks.parse("ÖZET: Çalışma ğüşıöç.\n\nÖnemli noktalar:\n- Birinci\n- İkinci")
        assertEquals(listOf(h("Özet"), p("Çalışma ğüşıöç."), h("Önemli noktalar"), b("Birinci", "•"), b("İkinci", "•")), blocks)
    }

    @Test
    fun longLinesEndingWithColonStayParagraphs() {
        val long = "Bu satır iki nokta ile bitiyor ama bir başlık olamayacak kadar uzun ve ayrıca bir cümle içeriyor:"
        assertEquals(listOf(p(long)), SummaryBlocks.parse(long))
    }

    @Test
    fun emptyTextHasNoBlocks() {
        assertEquals(emptyList<SummaryBlock>(), SummaryBlocks.parse("  \n\r\n "))
    }

    @Test
    fun boldRunsAreSeparatedAndStrayStarsDropped() {
        assertEquals(
            listOf(TextRun("a ", false), TextRun("kalın", true), TextRun(" b", false)),
            SummaryBlocks.runs("a **kalın** b"),
        )
        assertEquals(listOf(TextRun("x", true)), SummaryBlocks.runs("__x__"))
        assertEquals(listOf(TextRun("eşleşmeyen yıldız", false)), SummaryBlocks.runs("eşleşmeyen *yıldız"))
    }
}
