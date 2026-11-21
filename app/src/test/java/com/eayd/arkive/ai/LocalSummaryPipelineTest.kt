package com.eayd.arkive.ai

import com.eayd.arkive.i18n.AppLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telefondaki yapay zekanın özetleme adımları (model yerine sahte yanıtlarla). */
class LocalSummaryPipelineTest {

    private class Calls {
        val prompts = mutableListOf<Pair<String, Int>>()
    }

    private fun pipeline(
        language: AppLanguage = AppLanguage.Turkish,
        calls: Calls = Calls(),
        steps: MutableList<Pair<Int, Int>> = mutableListOf(),
        reply: (String) -> String,
    ) = LocalSummaryPipeline(
        language = language,
        ask = { prompt, maxTokens -> calls.prompts += prompt to maxTokens; reply(prompt) },
        onStep = { done, total -> steps += done to total },
    )

    @Test
    fun `kisa belge tek istekte ozetlenir`() = runBlocking {
        val calls = Calls()
        val out = pipeline(calls = calls) { "Bu belge bir toplantı notudur.\n\nKararları içerir." }
            .summarize("Toplantı salı günü yapılacak.")
        assertEquals("Bu belge bir toplantı notudur. Kararları içerir.", out)
        val (prompt, tokens) = calls.prompts.single()
        assertEquals(LocalSummaryPipeline.FINAL_MAX_TOKENS, tokens)
        assertTrue(prompt.contains("Toplantı salı günü yapılacak."))
        assertTrue(prompt.contains("\"Bu belge\" diye başla") && prompt.contains("tek paragrafta"))
    }

    @Test
    fun `uzun belge bolumlere ayrilir ve notlar tek paragrafta birlesir`() = runBlocking {
        val calls = Calls()
        val steps = mutableListOf<Pair<Int, Int>>()
        val text = ("Döngüler bir ders konusudur. ".repeat(60) + "\n\n").repeat(12)
        val parts = LocalSummaryPipeline.chunks(text)
        val out = pipeline(calls = calls, steps = steps) { prompt ->
            if (prompt.contains("BÖLÜM:")) "Bu bölümde döngüler anlatılıyor." else "Bu belge döngüleri anlatan bir ders notudur."
        }.summarize(text)
        assertTrue(parts.size > 1)
        assertEquals(parts.size + 1, calls.prompts.size)
        assertTrue(calls.prompts.dropLast(1).all { it.second == LocalSummaryPipeline.PART_MAX_TOKENS })
        val (merge, tokens) = calls.prompts.last()
        assertEquals(LocalSummaryPipeline.FINAL_MAX_TOKENS, tokens)
        assertEquals(parts.size, Regex("Bu bölümde döngüler anlatılıyor.").findAll(merge).count())
        assertEquals("Bu belge döngüleri anlatan bir ders notudur.", out)
        assertEquals((1..parts.size + 1).map { it to parts.size + 1 }, steps)
    }

    @Test
    fun `cok uzun notlar once gruplar halinde kisaltilir`() = runBlocking {
        val calls = Calls()
        val text = ("Kelime ".repeat(1500) + "\n\n").repeat(5)
        val out = pipeline(calls = calls) { prompt ->
            when {
                prompt.contains("BÖLÜM:") -> "Uzun bir not. ".repeat(150) // ~2 bin karakter: birleştirmeye sığmaz
                prompt.startsWith("Aşağıda bir belgenin ardışık") -> "Kısa not."
                else -> "Bu belge uzundur."
            }
        }.summarize(text)
        assertEquals("Bu belge uzundur.", out)
        assertTrue(calls.prompts.any { it.first.startsWith("Aşağıda bir belgenin ardışık") })
        assertTrue(calls.prompts.last().first.length < LocalSummaryPipeline.MERGE_CHARS + 2_000)
    }

    @Test
    fun `bolumler sinira uyar ve metin kaybolmaz`() {
        val text = (1..400).joinToString(" ") { "Cümle numarası $it burada bitiyor." }
        val parts = LocalSummaryPipeline.chunks(text, size = 1_000)
        assertTrue(parts.all { it.length <= 1_000 })
        assertTrue(parts.dropLast(1).all { it.endsWith(".") }) // cümle sonunda kesilir
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
        assertEquals(listOf("kısa"), LocalSummaryPipeline.chunks("  kısa  "))
        assertTrue(LocalSummaryPipeline.chunks("   ").isEmpty())
    }

    @Test
    fun `model yaniti tek paragrafa cevrilir`() {
        val p = LocalSummaryPipeline::paragraph
        assertEquals("Bu belge bir rapordur.", p("ÖZET: Bu belge bir rapordur."))
        assertEquals("Bu belge bir rapordur.", p("**Özet:** Bu belge bir rapordur."))
        assertEquals("Bu belge bir maddedir. İkinci madde.", p("- Bu belge bir maddedir.\n- İkinci madde."))
        assertEquals("Bu belge bir rapordur.", p("Bu belge bir rapordur. Sonuçları da yarı"))
        assertEquals("This document is a report.", p("Summary: This document is a report."))
        assertEquals("", p("**Özet:**\n- "))
    }

    @Test
    fun `ingilizce ozet icin istemler ingilizcedir`() = runBlocking {
        val calls = Calls()
        pipeline(AppLanguage.English, calls) { "This document is short." }.summarize("Kısa bir metin.")
        val prompt = calls.prompts.single().first
        assertTrue(prompt.contains("\"This document\"") && prompt.contains("in English"))
        val system = LocalSummaryPipeline.systemPrompt(AppLanguage.English)
        val instructions = prompt.substringBefore("DOCUMENT:") + system
        assertFalse(instructions.any { it in "çğıöşüÇĞİÖŞÜ" })
        assertTrue(LocalSummaryPipeline.systemPrompt(AppLanguage.Turkish).contains("Türkçe"))
    }

    @Test
    fun `bir bolumun istemi telefondaki modelin baglamina sigar`() {
        // Türkçede bir belirteç ortalama 3 karakterden uzundur; istem + sistem mesajı + yanıt 4096 belirteci aşmamalı.
        val chunk = "a".repeat(LocalSummaryPipeline.CHUNK_CHARS)
        val prompts = LocalSummaryPipeline.prompts(AppLanguage.Turkish)
        val chars = prompts.single(chunk).length + prompts.system.length
        assertTrue(chars / 3 + LocalSummaryPipeline.FINAL_MAX_TOKENS < 4096)
    }
}
