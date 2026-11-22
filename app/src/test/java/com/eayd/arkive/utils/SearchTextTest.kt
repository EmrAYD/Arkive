package com.eayd.arkive.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arama ekranının yardımcıları: ne zaman aranır, vurgu nereye düşer. */
class SearchTextTest {

    @Test
    fun `en az iki harf gerekir`() {
        assertFalse(SearchText.canSearch(""))
        assertFalse(SearchText.canSearch("  a  "))
        assertFalse(SearchText.canSearch("a A"))
        assertTrue(SearchText.canSearch("ab"))
        assertTrue(SearchText.canSearch("a b"))
        assertTrue(SearchText.canSearch("%5"))
    }

    @Test
    fun `emoji tek harf sayilir`() {
        assertFalse(SearchText.canSearch("😀"))
        assertTrue(SearchText.canSearch("😀x"))
    }

    @Test
    fun `aralik duz metinde aynen kalir`() {
        assertEquals(listOf(0 until 5, 6 until 11), SearchText.highlights("Küçük Rapor", listOf(listOf(0, 5), listOf(6, 11))))
    }

    @Test
    fun `emojiden sonra vurgu kaymaz`() {
        val name = "😀 rapor.pdf"
        val marks = SearchText.highlights(name, listOf(listOf(2, 7)))
        assertEquals("rapor", name.substring(marks.single().first, marks.single().last + 1))
    }

    @Test
    fun `gecersiz araliklar atlanir ya da kirpilir`() {
        assertEquals(emptyList<IntRange>(), SearchText.highlights("abc", listOf(listOf(2, 2), listOf(3), listOf(5, 9))))
        assertEquals(listOf(1 until 3), SearchText.highlights("abc", listOf(listOf(1, 10))))
    }

    @Test
    fun `parcanin basi eslesmeye yaklasir`() {
        val snippet = "…" + "lorem ipsum dolor sit amet ".repeat(3) + "Ankara toplantısı"
        val start = snippet.indexOf("Ankara")
        val (text, marks) = SearchText.trimLead(snippet, listOf(start until start + 6), lead = 10)
        val mark = marks.single()
        assertEquals("Ankara", text.substring(mark.first, mark.last + 1))
        assertTrue(text.startsWith("…") && mark.first <= 11)
        assertFalse(text[1] == ' ')
    }

    @Test
    fun `eslesme zaten bastaysa parca degismez`() {
        val marks = listOf(3 until 8)
        assertEquals("abc rapor" to marks, SearchText.trimLead("abc rapor", marks))
        assertEquals("abc" to emptyList<IntRange>(), SearchText.trimLead("abc", emptyList()))
    }

    @Test
    fun `yer ana sayfadan baslar`() {
        assertEquals("Ana Sayfa", SearchText.place("Ana Sayfa", emptyList()))
        assertEquals("Ana Sayfa / Belgeler / 2026", SearchText.place("Ana Sayfa", listOf("Belgeler", "2026")))
    }
}
