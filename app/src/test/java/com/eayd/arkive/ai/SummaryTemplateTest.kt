package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTemplateTest {

    @Test
    fun idsAreUniqueAndMatchTheServerTemplates() {
        assertEquals(listOf("klasik", "akademik", "modern", "kompakt"), SummaryTemplate.entries.map { it.id })
    }

    @Test
    fun unknownOrMissingIdFallsBackToTheDefault() {
        assertEquals(SummaryTemplate.Classic, SummaryTemplate.fromId(null))
        assertEquals(SummaryTemplate.Classic, SummaryTemplate.fromId("yok"))
        assertEquals(SummaryTemplate.Modern, SummaryTemplate.fromId("modern"))
    }

    @Test
    fun everyTemplateHasALabelAndDescription() {
        assertTrue(SummaryTemplate.entries.all { it.label.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun classicTemplateUsesTheRequestedSizes() {
        val t = SummaryTemplate.Default
        assertEquals(14f, t.headingSize, 0f)
        assertEquals(12f, t.bodySize, 0f)
        assertEquals(PdfFamily.Serif, t.family)
        assertEquals(4, t.marginsCm.size)
    }
}
