package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractiveSummarizerTest {

    private val sample = """
        Arkive, kullanıcıların dosyalarını bulutta saklamasını sağlayan bir depolama uygulamasıdır.
        Uygulama dosya yükleme, indirme ve klasör yönetimi sunar.
        Silinen dosyalar çöp kutusunda otuz gün saklanır ve bu süre ayarlardan değiştirilebilir.
        Depolama alanı plana göre değişir ve kullanım ana ekrandaki menüden izlenebilir.
        Dosyalar yüklenirken ilerleme çubuğu gösterilir ve yükleme istenirse iptal edilebilir.
        Yapay zeka özelliği, desteklenen dosyaların kısa bir özetini çıkarır.
        Özetler AI Sonuçlarım sayfasında metin dosyası olarak saklanır.
        Hava durumu bu uygulamanın konusu değildir ama yan bir örnek olarak anılmıştır.
        Depolama alanı dolduğunda yeni dosya yüklenemez ve kullanıcıya bildirim gösterilir.
        Kullanıcılar dosyalarını favorilere ekleyerek hızlıca ulaşabilir.
    """.trimIndent()

    private fun normalized(s: String) = s.replace(Regex("\\s+"), " ")

    private fun sentencesIn(summary: String): List<String> =
        summary.lines().map { it.removePrefix("Özet: ").removePrefix("- ").trim() }
            .filter { it.isNotEmpty() && it != "Önemli noktalar:" }

    @Test
    fun outputHasHeadlineAndPoints() {
        val summary = ExtractiveSummarizer.summarize(sample)
        assertTrue(summary.startsWith("Özet: "))
        assertTrue(summary.contains("\n\nÖnemli noktalar:\n- "))
        val points = summary.lines().count { it.startsWith("- ") }
        assertTrue(points in 1..6)
    }

    @Test
    fun everySentenceComesFromTheDocument() {
        val source = normalized(sample)
        for (sentence in sentencesIn(ExtractiveSummarizer.summarize(sample))) {
            assertTrue("belgede yok: $sentence", source.contains(sentence))
        }
    }

    @Test
    fun pointsKeepDocumentOrder() {
        val summary = ExtractiveSummarizer.summarize(sample)
        val source = normalized(sample)
        val positions = summary.lines().filter { it.startsWith("- ") }.map { source.indexOf(it.removePrefix("- ")) }
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun topicalSentencesWinOverTheOffTopicOne() {
        val summary = ExtractiveSummarizer.summarize(sample)
        assertFalse(summary.contains("Hava durumu"))
        assertTrue(summary.contains("Depolama") || summary.contains("depolama"))
    }

    @Test
    fun shortDocumentIsReturnedAsIs() {
        val summary = ExtractiveSummarizer.summarize("Bu belge yalnızca iki cümle içeriyor. İkincisi de burada ama biraz daha uzun duruyor.")
        assertEquals("Özet: Bu belge yalnızca iki cümle içeriyor. İkincisi de burada ama biraz daha uzun duruyor.", summary)
    }

    @Test
    fun blankOrTinyInputGivesEmptySummary() {
        assertEquals("", ExtractiveSummarizer.summarize(""))
        assertEquals("", ExtractiveSummarizer.summarize("  \n\n 123 \n"))
    }

    @Test
    fun abbreviationsAndInitialsDoNotSplitSentences() {
        val parts = ExtractiveSummarizer.splitSentences("Dr. Ahmet Yılmaz toplantıya geç kaldı. A. B. Kaya da gelmedi. Toplantı iptal edildi!")
        assertEquals(
            listOf("Dr. Ahmet Yılmaz toplantıya geç kaldı.", "A. B. Kaya da gelmedi.", "Toplantı iptal edildi!"),
            parts,
        )
    }

    @Test
    fun lowercaseAfterDotDoesNotSplit() {
        val parts = ExtractiveSummarizer.splitSentences("Toplam tutar 3.5 milyon lira olup bkz. ek tablo ve devamı yazılıdır. Yeni cümle başlar.")
        assertEquals(2, parts.size)
    }

    @Test
    fun hardWrappedLinesAreJoined() {
        val text = "Bu çok uzun bir cümlenin ilk satırıdır ve satır sonu burada kesilmiştir\nama cümle devam ediyor ve burada bitiyor.\n\nYeni paragraf başlıyor."
        assertEquals(
            listOf(
                "Bu çok uzun bir cümlenin ilk satırıdır ve satır sonu burada kesilmiştir ama cümle devam ediyor ve burada bitiyor.",
                "Yeni paragraf başlıyor.",
            ),
            ExtractiveSummarizer.segments(text),
        )
    }

    @Test
    fun hyphenatedWordIsRejoined() {
        val text = "Uzun satırlarda kelimeler bazen tire ile bölünür, örneğin depo-\nlama sözcüğü gibi bir şey."
        assertEquals(listOf("Uzun satırlarda kelimeler bazen tire ile bölünür, örneğin depolama sözcüğü gibi bir şey."), ExtractiveSummarizer.segments(text))
    }

    @Test
    fun listItemsStartNewSegments() {
        val text = "Birinci madde için gerekli olan açıklama burada uzun uzun yazılmıştır ve noktasızdır\n- İkinci madde de benzer şekilde uzun uzun yazılmıştır"
        assertEquals(2, ExtractiveSummarizer.segments(text).size)
    }

    @Test
    fun nearDuplicateSentencesAppearOnce() {
        val repeated = "Sunucu bakım çalışması nedeniyle cumartesi gecesi kısa süreliğine kapalı olacaktır."
        val text = List(6) { repeated }.joinToString(" ") + " " + sample.replace("\n", " ")
        val summary = ExtractiveSummarizer.summarize(text)
        assertTrue(Regex(Regex.escape(repeated)).findAll(summary).count() <= 1)
    }

    @Test
    fun longSentenceIsClipped() {
        val long = "Çok uzun bir cümle " + "kelime ".repeat(200) + "sonunda biter."
        val summary = ExtractiveSummarizer.summarize(long)
        assertTrue(summary.length < 400)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun largeDocumentIsFast() {
        val sentence = "Depolama alanı kullanıcı dosyaları için ayrılmıştır ve her kullanıcıya planına göre tanımlanır "
        val text = buildString {
            repeat(8000) { i -> append(sentence).append("sayı ").append(i).append(". ") }
        }
        val start = System.nanoTime()
        val summary = ExtractiveSummarizer.summarize(text)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue(summary.startsWith("Özet: "))
        assertTrue("çok yavaş: $millis ms", millis < 5000)
    }

    // --- Başlıklı ders notu -------------------------------------------------------------------------------

    private val notes = """
        SİNDİRİM SİSTEMİ
        Alınan besinlerin hücre zarından geçebilecek kadar küçük maddelere parçalanması olayına sindirim denir.
        Sindirim olayı mekanik sindirim ve kimyasal sindirim olmak üzere iki biçimde gerçekleşir.
        NOT: Mekanik sindirimle besinlerin yüzey alanı artırılır.

        Sindirim Sistemi | Biyoloji Notları                          Sayfa 1 / 3

        AĞIZ
        Ağız boşluğunun iç yüzeyi mukoza adı verilen çok katlı epitel hücreler ile kaplıdır.
        Ağızdaki kimyasal sindirim tükürükte bulunan amilaz enzimi ile gerçekleşir.
        Dil ve dişler fiziksel sindirimde görev alır ve besinleri küçük parçalara ayırır.
        Tükürük bezleri dil altı, çene altı ve kulak altı olmak üzere üç çeşittir.
        Dişin görünen kısmına taç, diş etiyle sarılı kısmına boyun denir.

        Sindirim Sistemi | Biyoloji Notları                          Sayfa 2 / 3

        MİDE
        Midede hem kimyasal hem de mekanik sindirim gerçekleşir ve besinler geçici olarak depolanır.
        Mide öz suyunda HCl, su, pepsinojen ve mukus bulunur.
        Mukus midenin iç yüzeyini asit etkisinden korur.
        DİKKAT: Gastrin hormonu mide öz suyunun salgılanmasını arttırır.
        Pepsin midede proteinlerin sindiriminde görev alır.
        Midede bulunup ince bağırsağa aktarılan besin bulamacına kimus denir.

        Sindirim Sistemi | Biyoloji Notları                          Sayfa 3 / 3
    """.trimIndent()

    @Test
    fun headingsGroupThePointsAndEverySectionIsRepresented() {
        val summary = ExtractiveSummarizer.summarize(notes)
        assertTrue(summary.startsWith("Özet: Alınan besinlerin"))
        val lines = summary.lines()
        for (title in listOf("## Sindirim sistemi", "## Ağız", "## Mide")) assertTrue("başlık yok: $title", title in lines)
        // Her başlığın altında en az bir madde var ve sıra belgedeki gibi.
        assertTrue(lines.indexOf("## Ağız") < lines.indexOf("## Mide"))
        assertTrue(lines[lines.indexOf("## Mide") + 1].startsWith("- "))
        assertFalse(summary.contains("Önemli noktalar"))
    }

    @Test
    fun pageFootersAndRepeatedRunningHeadersAreDropped() {
        val summary = ExtractiveSummarizer.summarize(notes)
        assertFalse(summary.contains("Sayfa"))
        assertFalse(summary.contains("Biyoloji Notları"))
    }

    @Test
    fun calloutLabelsAreKeptAndDefinitionsAreFavoured() {
        val summary = ExtractiveSummarizer.summarize(notes)
        assertTrue(summary.contains("- Not: Mekanik sindirimle besinlerin yüzey alanı artırılır."))  // etiket korunur, "NOT:" olarak kalmaz
        assertTrue(summary.lines().first().endsWith("sindirim denir."))                               // özet cümlesi tanım cümlesi
    }

    @Test
    fun headingDetection() {
        assertTrue(ExtractiveSummarizer.isHeading("AĞIZ"))
        assertTrue(ExtractiveSummarizer.isHeading("YUTAK (FARİNKS)"))
        assertTrue(ExtractiveSummarizer.isHeading("2. Öğrenme Hedefleri"))
        assertFalse(ExtractiveSummarizer.isHeading("Küçük Prens Antoine"))      // yalnızca baş harfleri büyük satır başlık değildir
        assertFalse(ExtractiveSummarizer.isHeading("1. Birinci madde burada bir cümledir."))
        assertFalse(ExtractiveSummarizer.isHeading("Mide öz suyunun salgılanması üç farklı şekilde kontrol edilir:"))
        assertFalse(ExtractiveSummarizer.isHeading("NOT: Yemek borusunda sindirim olmaz."))
        assertFalse(ExtractiveSummarizer.isHeading("ISBN 978-975-07-2479-4"))
        assertFalse(ExtractiveSummarizer.isHeading("Ağız boşluğunun iç yüzeyi mukoza ile kaplıdır."))
    }

    @Test
    fun splitCapitalsFromPdfExtractionAreRepaired() {
        assertEquals("Mekanik sindirimle Midenin yüzeyi", ExtractiveSummarizer.repairSplitCapitals("M ekanik sindirimle M idenin yüzeyi"))
        // Anlamlı tek harfler ve vitamin adları korunur.
        assertEquals("K vitamini ve O zaman, A ve B", ExtractiveSummarizer.repairSplitCapitals("K vitamini ve O zaman, A ve B"))
    }

    @Test
    fun documentWithoutHeadingsKeepsTheClassicLayout() {
        val summary = ExtractiveSummarizer.summarize(sample)
        assertTrue(summary.contains("\n\nÖnemli noktalar:\n- "))
        assertFalse(summary.contains("## "))
    }

    @Test
    fun socialHandleAndLinkLinesAreDropped() {
        val cleaned = ExtractiveSummarizer.clean("Orijinal Notlar: Vasfi Gelir (@biyolojinotu)\nDetaylar için https://ornek.com adresine bakın\n@Override\nGerçek cümle burada duruyor.")
        assertEquals("@Override\nGerçek cümle burada duruyor.", cleaned.trim())
    }
}
