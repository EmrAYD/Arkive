package com.eayd.arkive.ai

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfficeTextTest {

    private fun zip(vararg parts: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, body) in parts) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val pkg = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun workbook(date1904: Boolean = false) = zip(
        "xl/workbook.xml" to """<workbook xmlns="$main" xmlns:r="$rel">
            <workbookPr date1904="${if (date1904) 1 else 0}"/>
            <sheets><sheet name="Satışlar" sheetId="1" r:id="rId1"/><sheet name="Notlar" sheetId="2" r:id="rId2"/></sheets>
            </workbook>""",
        "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="$pkg">
            <Relationship Id="rId1" Type="$rel/worksheet" Target="worksheets/sheet1.xml"/>
            <Relationship Id="rId2" Type="$rel/worksheet" Target="/xl/worksheets/sheet2.xml"/>
            </Relationships>""",
        "xl/sharedStrings.xml" to """<sst xmlns="$main">
            <si><t>Ürün</t></si><si><t>Adet</t></si><si><t>Tarih</t></si>
            <si><r><t>Kır</t></r><r><t xml:space="preserve">mızı  kalem</t></r><rPh><t>okunuş</t></rPh></si>
            </sst>""",
        "xl/styles.xml" to """<styleSheet xmlns="$main">
            <numFmts count="2"><numFmt numFmtId="164" formatCode="dd\.mm\.yyyy"/><numFmt numFmtId="165" formatCode="#,##0.00 &quot;TL&quot;"/></numFmts>
            <cellXfs count="4"><xf numFmtId="0"/><xf numFmtId="164"/><xf numFmtId="165"/><xf numFmtId="22"/></cellXfs>
            </styleSheet>""",
        "xl/worksheets/sheet1.xml" to """<worksheet xmlns="$main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
            <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2" s="2"><v>3</v></c><c r="C2" s="1"><v>46354</v></c></row>
            <row r="3"><c r="A3" t="inlineStr"><is><t>Defter</t></is></c><c r="B3"><v>0.30000000000000004</v></c><c r="C3" s="3"><v>46354.604166666664</v></c></row>
            <row r="4"/>
            <row r="5"><c r="C5" t="str"><v>Toplam</v></c><c r="D5" t="b"><v>1</v></c><c r="E5" t="e"><v>#DIV/0!</v></c><c r="F5"/></row>
            </sheetData></worksheet>""",
        "xl/worksheets/sheet2.xml" to """<worksheet xmlns="$main"><sheetData>
            <row r="1"><c r="A1" t="inlineStr"><is><t>Kasım sonu</t></is></c></row>
            </sheetData></worksheet>""",
    )

    @Test
    fun excelSheetsAreReadRowByRow() {
        assertEquals(
            listOf(
                "# Satışlar",
                "Ürün | Adet | Tarih",
                "Kırmızı kalem | 3 | 2026-11-28",
                "Defter | 0.3 | 2026-11-28 14:30",
                " |  | Toplam | TRUE | #DIV/0!",
                "# Notlar",
                "Kasım sonu",
            ).joinToString("\n"),
            OfficeText.excel(workbook()),
        )
    }

    @Test
    fun excelDatesFollowTheWorkbookCalendar() {
        val text = OfficeText.excel(workbook(date1904 = true))
        assertTrue(text, text.contains("Kırmızı kalem | 3 | 2030-11-29"))
    }

    private val drawing = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private val presentation = "http://schemas.openxmlformats.org/presentationml/2006/main"

    private fun slide(body: String) = """<p:sld xmlns:p="$presentation" xmlns:a="$drawing"><p:cSld><p:spTree>$body</p:spTree></p:cSld></p:sld>"""

    private fun shape(vararg paragraphs: String) =
        "<p:sp><p:txBody><a:bodyPr/>" + paragraphs.joinToString("") { "<a:p>$it</a:p>" } + "</p:txBody></p:sp>"

    private fun run(text: String) = "<a:r><a:t>$text</a:t></a:r>"

    private fun presentationFile() = zip(
        "ppt/presentation.xml" to """<p:presentation xmlns:p="$presentation" xmlns:r="$rel">
            <p:sldIdLst><p:sldId id="256" r:id="rId3"/><p:sldId id="257" r:id="rId2"/></p:sldIdLst></p:presentation>""",
        "ppt/_rels/presentation.xml.rels" to """<Relationships xmlns="$pkg">
            <Relationship Id="rId1" Type="$rel/slideMaster" Target="slideMasters/slideMaster1.xml"/>
            <Relationship Id="rId2" Type="$rel/slide" Target="slides/slide1.xml"/>
            <Relationship Id="rId3" Type="$rel/slide" Target="slides/slide2.xml"/>
            </Relationships>""",
        "ppt/slides/slide2.xml" to slide(shape(run("Veri Yapıları")) + shape(run("Dördüncü ") + run("hafta"))),
        "ppt/slides/slide1.xml" to slide(
            shape(run("Yığınlar")) +
                shape(run("LIFO") + "<a:br/>" + run("düzeni"), run("push ve pop")) +
                """<p:graphicFrame><a:graphic><a:graphicData><a:tbl>
                <a:tr><a:tc><a:txBody><a:p>${run("İşlem")}</a:p></a:txBody></a:tc><a:tc><a:txBody><a:p>${run("Süre")}</a:p></a:txBody></a:tc></a:tr>
                <a:tr><a:tc><a:txBody><a:p>${run("push")}</a:p><a:p>${run("(yığına ekle)")}</a:p></a:txBody></a:tc><a:tc><a:txBody><a:p>${run("O(1)")}</a:p></a:txBody></a:tc></a:tr>
                </a:tbl></a:graphicData></a:graphic></p:graphicFrame>""" +
                "<p:grpSp>${shape(run("Grup içi yazı"))}</p:grpSp>",
        ),
        "ppt/slides/_rels/slide1.xml.rels" to """<Relationships xmlns="$pkg">
            <Relationship Id="rId1" Type="$rel/slideLayout" Target="../slideLayouts/slideLayout2.xml"/>
            <Relationship Id="rId2" Type="$rel/notesSlide" Target="../notesSlides/notesSlide7.xml"/>
            </Relationships>""",
        "ppt/notesSlides/notesSlide7.xml" to """<p:notes xmlns:p="$presentation" xmlns:a="$drawing"><p:cSld><p:spTree>
            <p:sp><p:nvSpPr><p:cNvPr id="2" name="Slide Image"/><p:cNvSpPr/><p:nvPr><p:ph type="sldImg"/></p:nvPr></p:nvSpPr></p:sp>
            <p:sp><p:nvSpPr><p:cNvPr id="3" name="Notes"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>
            <p:txBody><a:p>${run("Örnek kodu tahtada göster")}</a:p></p:txBody></p:sp>
            <p:sp><p:nvSpPr><p:cNvPr id="4" name="Number"/><p:cNvSpPr/><p:nvPr><p:ph type="sldNum" idx="5"/></p:nvPr></p:nvSpPr>
            <p:txBody><a:p><a:fld type="slidenum"><a:t>2</a:t></a:fld></a:p></p:txBody></p:sp>
            </p:spTree></p:cSld></p:notes>""",
    )

    @Test
    fun powerPointSlidesFollowThePresentationOrder() {
        assertEquals(
            listOf(
                "# Slayt 1",
                "Veri Yapıları",
                "Dördüncü hafta",
                "# Slayt 2",
                "Yığınlar",
                "LIFO\ndüzeni\npush ve pop",
                "İşlem | Süre",
                "push (yığına ekle) | O(1)",
                "Grup içi yazı",
                "Konuşmacı notu: Örnek kodu tahtada göster",
            ).joinToString("\n"),
            OfficeText.powerPoint(presentationFile(), english = false),
        )
    }

    @Test
    fun englishSummariesGetEnglishSlideLabels() {
        val text = OfficeText.powerPoint(presentationFile(), english = true)
        assertTrue(text, text.startsWith("# Slide 1\n") && text.contains("Speaker notes: Örnek kodu"))
    }

    @Test
    fun slidesFallBackToFileOrderWhenThePresentationPartIsMissing() {
        val text = OfficeText.powerPoint(
            zip(
                "ppt/slides/slide10.xml" to slide(shape(run("onuncu"))),
                "ppt/slides/slide2.xml" to slide(shape(run("ikinci"))),
            ),
            english = false,
        )
        assertEquals("# Slayt 1\nikinci\n# Slayt 2\nonuncu", text)
    }

    @Test
    fun notOfficeFilesAreUnreadable() {
        for (bytes in listOf("düz metin".toByteArray(), zip("word/document.xml" to "<w/>"), zip("xl/workbook.xml" to "<bozuk"))) {
            try {
                OfficeText.excel(bytes)
                fail("okunmamalıydı")
            } catch (e: OfficeTextException) {
                assertTrue(!e.tooBig)
            }
        }
    }

    @Test
    fun hugeUnpackedPartsAreRejectedAsTooBig() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val zeros = ByteArray(1024 * 1024)
            repeat(9) { zip.write(zeros) }
            zip.closeEntry()
        }
        try {
            OfficeText.excel(out.toByteArray())
            fail("reddedilmeliydi")
        } catch (e: OfficeTextException) {
            assertTrue(e.tooBig)
        }
    }

    @Test
    fun excelAndPowerPointAreSummarizable() {
        for (name in listOf("tablo.xlsx", "MAKRO.XLSM", "sablon.xltx", "sunu.pptx", "gosteri.ppsx", "sablon.potx")) {
            val expected = if ("xl" in name.lowercase()) SummarizableKind.Xlsx else SummarizableKind.Pptx
            assertEquals(name, expected, AiPolicy.kindOf(null, name))
        }
        assertEquals(
            SummarizableKind.Xlsx,
            AiPolicy.kindOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "indirilen"),
        )
        assertEquals(
            SummarizableKind.Pptx,
            AiPolicy.kindOf("application/vnd.openxmlformats-officedocument.presentationml.presentation", "indirilen"),
        )
        assertEquals(null, AiPolicy.kindOf("application/vnd.ms-excel", "eski.xls"))
        assertEquals(null, AiPolicy.kindOf("application/vnd.ms-powerpoint", "eski.ppt"))
    }
}
