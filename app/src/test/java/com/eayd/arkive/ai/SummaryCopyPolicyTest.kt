package com.eayd.arkive.ai

import com.eayd.arkive.data.FileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryCopyPolicyTest {

    private fun pdf(size: Long) = FileDto(
        id = "r1", folderId = null, name = "a - özet.pdf", size = size, mimeType = "application/pdf",
        favorite = false, status = "ready", createdAt = "2026-11-03T10:00:00Z", inAiResults = true,
    )

    private fun stored(summary: String = "Özet: kısa", pending: Boolean = false, resultFile: FileDto? = pdf(2_000)) =
        StoredSummary("f1", "a.pdf", summary, "cloud", "m", pending, resultFile)

    @Test
    fun `sunucunun bos metni telefondaki dolu metni ezmez`() {
        assertEquals("eski özet", SummaryCopyPolicy.keptText("", "eski özet"))
        assertEquals("eski özet", SummaryCopyPolicy.keptText("  \n", "eski özet"))
        assertEquals("yeni özet", SummaryCopyPolicy.keptText("yeni özet", "eski özet"))
        assertEquals("", SummaryCopyPolicy.keptText("", null))
    }

    @Test
    fun `telefonda hic kaydi olmayan ozet alinir`() {
        assertTrue(SummaryCopyPolicy.needsFetch(null, hasCopy = false))
    }

    @Test
    fun `metni ve PDF kopyasi olan ozet yeniden alinmaz`() {
        assertFalse(SummaryCopyPolicy.needsFetch(stored(), hasCopy = true))
    }

    @Test
    fun `PDF kopyasi eksikse ya da metin bossa ya da PDF bilgisi yoksa alinir`() {
        assertTrue(SummaryCopyPolicy.needsFetch(stored(), hasCopy = false))
        assertTrue(SummaryCopyPolicy.needsFetch(stored(summary = ""), hasCopy = true))
        assertTrue(SummaryCopyPolicy.needsFetch(stored(resultFile = null), hasCopy = false))
    }

    @Test
    fun `yuklenmeyi bekleyen telefon ozeti sunucudan alinmaz`() {
        assertFalse(SummaryCopyPolicy.needsFetch(stored(pending = true, resultFile = null), hasCopy = false))
    }

    @Test
    fun `cok buyuk PDF'in kopyasi tutulmaz ve bunun icin yeniden alinmaz`() {
        val big = SummaryCopyPolicy.MAX_RESULT_COPY_BYTES + 1
        assertFalse(SummaryCopyPolicy.canKeepCopy(big))
        assertFalse(SummaryCopyPolicy.canKeepCopy(0))
        assertTrue(SummaryCopyPolicy.canKeepCopy(SummaryCopyPolicy.MAX_RESULT_COPY_BYTES))
        assertFalse(SummaryCopyPolicy.needsFetch(stored(resultFile = pdf(big)), hasCopy = false))
    }
}
