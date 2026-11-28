package com.eayd.arkive.ai

import com.eayd.arkive.i18n.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Özetin seslendirilmesi: cümlelere bölme, dil ve hız. */
class SpeechTextTest {

    private fun pieces(text: String) = SpeechText.sentences(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `cumlelere bolunur`() {
        assertEquals(
            listOf("Bu belge bir rapor.", "Gelirler arttı!", "Peki giderler?", "Onlar da azaldı…"),
            pieces("Bu belge bir rapor. Gelirler arttı! Peki giderler? Onlar da azaldı…"),
        )
    }

    @Test
    fun `kisaltma ve ondalik sayidan bolunmez`() {
        assertEquals(
            listOf("Prof. Dr. Ahmet Yılmaz 3.5 puan aldı, örn. ilk sınavda.", "Sonra çıktı."),
            pieces("Prof. Dr. Ahmet Yılmaz 3.5 puan aldı, örn. ilk sınavda. Sonra çıktı."),
        )
    }

    @Test
    fun `kucuk harfle devam eden nokta bolmez, satir sonu boler`() {
        assertEquals(listOf("Sayfa 5. bölümde anlatılır", "Yeni satır"), pieces("Sayfa 5. bölümde anlatılır\n\nYeni satır"))
    }

    @Test
    fun `tirnakla biten cumle tirnagi da alir`() {
        assertEquals(listOf("He said \"Stop.\"", "Then left."), pieces("He said \"Stop.\" Then left."))
    }

    @Test
    fun `cok uzun cumle virgulden bolunur ve metin kaybolmaz`() {
        val text = (1..200).joinToString(", ") { "öğe $it" } + "."
        val ranges = SpeechText.sentences(text)
        assertTrue(ranges.size > 1)
        assertTrue(ranges.all { it.last - it.first + 1 <= SpeechText.MAX_PIECE })
        val joined = ranges.joinToString("") { text.substring(it.first, it.last + 1) }
        assertEquals(text.replace(" ", ""), joined.replace(" ", ""))
    }

    @Test
    fun `bos metin parca vermez`() {
        assertEquals(emptyList<IntRange>(), SpeechText.sentences("  \n "))
    }

    @Test
    fun `dil metinden anlasilir`() {
        assertEquals(AppLanguage.Turkish, SpeechText.languageOf("Bu belge bir şirketin yıllık raporunu içeriyor.", AppLanguage.English))
        assertEquals(AppLanguage.English, SpeechText.languageOf("This document contains the annual report of a company.", AppLanguage.Turkish))
        assertEquals(AppLanguage.English, SpeechText.languageOf("12345", AppLanguage.English))
    }

    @Test
    fun `hiz sirayla degisir ve basa doner`() {
        assertEquals(1.25f, SpeechText.nextSpeed(1f))
        assertEquals(0.75f, SpeechText.nextSpeed(2f))
        assertEquals(1f, SpeechText.nextSpeed(0.8f))
    }

    @Test
    fun `hiz yazisi dile gore`() {
        assertEquals("1,25×", SpeechText.speedText(1.25f, Locale.forLanguageTag("tr")))
        assertEquals("1.25×", SpeechText.speedText(1.25f, Locale.ENGLISH))
        assertEquals("1×", SpeechText.speedText(1f, Locale.forLanguageTag("tr")))
    }
}
