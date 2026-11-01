package com.eayd.arkive.word

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * .docx (Office Open XML) dosyasını [WordDoc]'a çevirir. Desteklenenler: paragraf ve karakter biçimleri (stil
 * zinciri dahil), başlıklar, numaralı/madde işaretli listeler, tablolar (birleşik hücreler, kenarlık, gölge, tablo
 * stilinin ilk satır/şeritleri), görseller, bağlantılar, dipnotlar, metin kutuları, sayfa/bölüm sonları.
 * Üst/alt bilgi, yorum ve değişiklik izleme silinen metni gösterilmez.
 */
internal object DocxReader {
    private const val MAX_XML_BYTES = 8 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    private const val MAX_IMAGES_TOTAL = 24L * 1024 * 1024
    private const val MAX_ENTRIES = 20_000
    const val MAX_CHARS = 2_000_000
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
    private val XML_PARTS = setOf(
        "word/document.xml", "word/styles.xml", "word/numbering.xml", "word/_rels/document.xml.rels",
        "word/footnotes.xml", "word/theme/theme1.xml",
    )

    /** [bytes]: şifresi çözülmüş .docx (zip) dosyası. */
    fun read(bytes: ByteArray): WordDoc {
        try {
            val parts = readParts(bytes, { it in XML_PARTS }, MAX_XML_BYTES, Long.MAX_VALUE, skipOversize = false)
            val documentXml = parts["word/document.xml"]
                ?: throw WordFormatException("Word belgesi okunamadı (.docx değil ya da bozuk).")
            val document = Xml.parse(documentXml)
            fun optional(name: String): XNode? = parts[name]?.let { runCatching { Xml.parse(it) }.getOrNull() }

            val themeXml = optional("word/theme/theme1.xml")
            val converter = DocxConverter(
                styles = Styles.parse(optional("word/styles.xml")),
                numbering = Numbering.parse(optional("word/numbering.xml")),
                theme = WordColors.parseTheme(themeXml),
                themeFonts = WordColors.parseThemeFonts(themeXml),
                rels = parseRels(optional("word/_rels/document.xml.rels")),
                footnotes = optional("word/footnotes.xml"),
            )
            val blocks = converter.convert(document)

            val wanted = converter.imageParts.filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }.toSet()
            val images = if (wanted.isEmpty()) emptyMap() else readParts(bytes, { it in wanted }, MAX_IMAGE_BYTES, MAX_IMAGES_TOTAL, skipOversize = true)
            return WordDoc(blocks, images, converter.truncated, "docx")
        } catch (e: IOException) {
            throw WordFormatException("Word belgesi okunamadı (bozuk olabilir).")
        }
    }

    private fun readParts(bytes: ByteArray, wanted: (String) -> Boolean, maxEach: Int, maxTotal: Long, skipOversize: Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > MAX_ENTRIES) throw WordFormatException("Word belgesi bozuk.")
                if (entry.isDirectory) continue
                val name = entry.name.trimStart('/')
                if (!wanted(name)) continue
                val data = readEntry(zip, maxEach)
                if (data == null || total + data.size > maxTotal) {
                    if (skipOversize) continue
                    throw WordFormatException("Word belgesi çok büyük.")
                }
                total += data.size
                out[name] = data
            }
        }
        return out
    }

    private fun readEntry(zip: ZipInputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (out.size() + n > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun parseRels(root: XNode?): Map<String, Rel> {
        if (root == null) return emptyMap()
        val out = HashMap<String, Rel>()
        for (r in root.children("Relationship")) {
            val id = r.attr("Id") ?: continue
            val target = r.attr("Target") ?: continue
            val external = r.attr("TargetMode").equals("External", ignoreCase = true)
            out[id] = Rel(if (external) target else resolvePart("word", target), external)
        }
        return out
    }

    /** [baseDir] klasörüne göre bağıl [target] yolunu zip içindeki tam yola çevirir. */
    fun resolvePart(baseDir: String, target: String): String {
        if (target.startsWith("/")) return target.trimStart('/')
        val parts = ArrayList(baseDir.split('/').filter { it.isNotEmpty() })
        for (seg in target.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    internal class Rel(val target: String, val external: Boolean)
}

private const val EMU_PER_PT = 12700f
private val LINK_COLOR = 0xFF0563C1.toInt()
private val HEADING_NAME = Regex("^(?:heading|başlık|baslik)\\s*(\\d)")

/** Çalıştırma (karakter) özellikleri; stil zinciri boyunca üst üste uygulanır. */
private class RProps(
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var strike: Boolean = false,
    var color: Int? = null,
    var highlight: Int? = null,
    var sizePt: Float? = null,
    var font: WordFont = WordFont.Sans,
    var script: Int = 0,
    var caps: Boolean = false,
    var smallCaps: Boolean = false,
    var vanish: Boolean = false,
    /** w:position: satırı yükselten/alçaltan yarım punto değeri (LibreOffice üst/alt simgeyi böyle yazar). */
    var position: Int = 0,
) {
    fun copy() = RProps(bold, italic, underline, strike, color, highlight, sizePt, font, script, caps, smallCaps, vanish, position)
}

/** Paragraf özellikleri. */
private class PProps {
    var align = WordAlign.Left
    var left = 0f
    var firstLine = 0f
    var before = 0f
    var after = 0f
    var lineMult: Float? = 1f
    var lineExactPt: Float? = null
    var numId: String? = null
    var ilvl = 0
    var shading: Int? = null
    var borderTop: Int? = null
    var borderBottom: Int? = null
    var pageBreakBefore = false
    var outline: Int? = null
}

private class ParaInfo(val p: PProps, val baseR: RProps, val heading: Int, val label: String?)

private class Ctx(val rBase: List<XNode>, val pBase: List<XNode>, val inCell: Boolean) {
    companion object { val ROOT = Ctx(emptyList(), emptyList(), false) }
}

private class FieldState {
    val code = StringBuilder()
    var separated = false
    var link: String? = null
}

private class Look(val firstRow: Boolean, val lastRow: Boolean, val firstCol: Boolean, val lastCol: Boolean, val noHBand: Boolean)

private class DocxConverter(
    private val styles: Styles,
    private val numbering: Numbering,
    private val theme: Map<String, Int>,
    private val themeFonts: Map<String, String>,
    private val rels: Map<String, DocxReader.Rel>,
    private val footnotes: XNode?,
) {
    val imageParts = LinkedHashSet<String>()
    var truncated = false
        private set

    private var chars = 0
    private val counters = HashMap<String, IntArray>()
    private val fields = ArrayList<FieldState>()
    private val footnoteOrder = ArrayList<String>()

    fun convert(document: XNode): List<WordBlock> {
        val body = document.child("w:body") ?: throw WordFormatException("Word belgesi okunamadı (gövde yok).")
        val out = ArrayList<WordBlock>()
        convertBlocks(body.kids, out, Ctx.ROOT)
        appendFootnotes(out)
        while (out.lastOrNull() === WordPageBreak) out.removeAt(out.size - 1)
        return out
    }

    // --- Bloklar ---------------------------------------------------------------------------------------

    private fun convertBlocks(nodes: List<XNode>, out: MutableList<WordBlock>, ctx: Ctx) {
        for (n in nodes) {
            if (truncated) return
            when (n.name) {
                "w:p" -> convertParagraph(n, out, ctx)
                "w:tbl" -> convertTable(n, ctx)?.let { out.add(it) }
                "w:sdt" -> n.child("w:sdtContent")?.let { convertBlocks(it.kids, out, ctx) }
                "w:customXml", "w:ins", "w:moveTo" -> convertBlocks(n.kids, out, ctx)
                "mc:AlternateContent" -> choice(n)?.let { convertBlocks(it.kids, out, ctx) }
            }
        }
    }

    private fun choice(alt: XNode): XNode? = alt.child("mc:Choice") ?: alt.child("mc:Fallback")

    private fun addPageBreak(out: MutableList<WordBlock>, ctx: Ctx) {
        if (ctx.inCell || out.isEmpty() || out.last() === WordPageBreak) return
        out.add(WordPageBreak)
    }

    // --- Özellik çözümleme -----------------------------------------------------------------------------

    private fun onOff(n: XNode) = n.isOn()

    private fun color(n: XNode, valueKey: String = "val", themeKey: String = "themeColor", tintKey: String = "themeTint", shadeKey: String = "themeShade") =
        WordColors.resolve(theme, n.attr(valueKey), n.attr(themeKey), n.attr(tintKey), n.attr(shadeKey))

    private fun fill(n: XNode): Int? = WordColors.resolve(theme, n.attr("fill"), n.attr("themeFill"), n.attr("themeFillTint"), n.attr("themeFillShade"))

    private fun borderColor(n: XNode?): Int? {
        if (n == null) return null
        val v = n.attr("val")
        if (v == null || v == "nil" || v == "none") return null
        return WordColors.resolve(theme, n.attr("color"), n.attr("themeColor"), n.attr("themeTint"), n.attr("themeShade"))
            ?: 0xFF000000.toInt()
    }

    private fun applyR(r: RProps, rPr: XNode?) {
        if (rPr == null) return
        for (k in rPr.kids) when (k.name) {
            "w:b" -> r.bold = onOff(k)
            "w:i" -> r.italic = onOff(k)
            "w:strike", "w:dstrike" -> r.strike = onOff(k)
            "w:u" -> r.underline = k.attr("val") != "none"
            "w:color" -> r.color = color(k)
            "w:sz" -> k.attr("val")?.toFloatOrNull()?.let { r.sizePt = it / 2f }
            "w:highlight" -> r.highlight = WordColors.highlight(k.attr("val"))
            "w:shd" -> r.highlight = fill(k) ?: r.highlight
            "w:vertAlign" -> r.script = when (k.attr("val")) { "superscript" -> 1; "subscript" -> -1; else -> 0 }
            "w:position" -> r.position = k.attr("val")?.toIntOrNull() ?: 0
            "w:caps" -> r.caps = onOff(k)
            "w:smallCaps" -> r.smallCaps = onOff(k)
            "w:vanish" -> r.vanish = onOff(k)
            "w:rFonts" -> {
                val themed = (k.attr("asciiTheme") ?: k.attr("hAnsiTheme"))?.let { t -> themeFonts[if (t.startsWith("major")) "major" else "minor"] }
                (k.attr("ascii") ?: k.attr("hAnsi") ?: themed)?.let { r.font = fontFamilyOf(it) }
            }
        }
    }

    private fun applyNumPr(p: PProps, k: XNode) {
        k.child("w:numId")?.attr("val")?.let { p.numId = it }
        k.child("w:ilvl")?.attr("val")?.toIntOrNull()?.let { p.ilvl = it.coerceIn(0, 8) }
    }

    private fun applyP(p: PProps, pPr: XNode?) {
        if (pPr == null) return
        for (k in pPr.kids) when (k.name) {
            "w:jc" -> p.align = when (k.attr("val")) {
                "center" -> WordAlign.Center
                "right", "end" -> WordAlign.Right
                "both", "distribute", "justify" -> WordAlign.Justify
                else -> WordAlign.Left
            }
            "w:ind" -> {
                (k.attr("left") ?: k.attr("start"))?.toFloatOrNull()?.let { p.left = it / 20f }
                val hanging = k.attr("hanging")?.toFloatOrNull()
                val first = k.attr("firstLine")?.toFloatOrNull()
                if (hanging != null) p.firstLine = -hanging / 20f else if (first != null) p.firstLine = first / 20f
            }
            "w:spacing" -> {
                k.attr("before")?.toFloatOrNull()?.let { p.before = it / 20f }
                k.attr("after")?.toFloatOrNull()?.let { p.after = it / 20f }
                val line = k.attr("line")?.toFloatOrNull()
                if (line != null) {
                    val rule = k.attr("lineRule")
                    if (rule == null || rule == "auto") {
                        p.lineMult = (line / 240f).coerceIn(0.8f, 3f); p.lineExactPt = null
                    } else {
                        p.lineMult = null; p.lineExactPt = line / 20f
                    }
                }
            }
            "w:numPr" -> applyNumPr(p, k)
            "w:shd" -> p.shading = fill(k)
            "w:pBdr" -> {
                k.child("w:top")?.let { p.borderTop = borderColor(it) }
                k.child("w:bottom")?.let { p.borderBottom = borderColor(it) }
            }
            "w:pageBreakBefore" -> p.pageBreakBefore = onOff(k)
            "w:outlineLvl" -> p.outline = k.attr("val")?.toIntOrNull()
        }
    }

    private fun resolvePara(pPr: XNode?, ctx: Ctx): ParaInfo {
        val styleId = pPr?.child("w:pStyle")?.attr("val") ?: styles.defaultParagraph
        val chain = styles.chain(styleId)

        val p = PProps()
        applyP(p, styles.defaultsP)
        chain.forEach { applyP(p, it.pPr) }
        ctx.pBase.forEach { applyP(p, it) }
        pPr?.child("w:numPr")?.let { applyNumPr(p, it) }

        var label: String? = null
        val numId = p.numId
        if (numId != null && numId != "0") {
            val level = numbering.level(numId, p.ilvl)
            if (level != null) {
                applyP(p, level.pPr)
                label = nextLabel(numId, p.ilvl)
            }
        }
        applyP(p, pPr)

        val r = RProps()
        applyR(r, styles.defaultsR)
        ctx.rBase.forEach { applyR(r, it) }
        chain.forEach { applyR(r, it.rPr) }

        val styleName = chain.lastOrNull()?.name?.lowercase().orEmpty()
        // "Heading 1"e dayanan özel stiller de başlıktır: zincir en türetilmiş stilden temele doğru aranır.
        val headingNumber = chain.asReversed().firstNotNullOfOrNull { HEADING_NAME.find(it.name.lowercase())?.groupValues?.get(1)?.toIntOrNull() }
        val heading = headingNumber?.coerceIn(1, 9) ?: p.outline?.takeIf { it in 0..8 }?.plus(1) ?: 0
        // stiller.xml yoksa başlıklar yine de başlık gibi görünsün
        if (styles.isEmpty && heading > 0) r.bold = true
        if (r.sizePt == null) {
            r.sizePt = when {
                styleName == "title" -> 26f
                heading == 1 -> 16f
                heading == 2 -> 13f
                heading == 3 -> 12f
                else -> null
            }
        }
        return ParaInfo(p, r, heading, label)
    }

    private fun nextLabel(numId: String, ilvl: Int): String? {
        val def = numbering.nums[numId] ?: return null
        val levels = numbering.levels(numId)
        val level = levels[ilvl] ?: return null
        val key = if (def.startOverrides.isNotEmpty()) "n$numId" else "a${def.abstractId}"
        val c = counters.getOrPut(key) { IntArray(9) { -1 } }
        val start = def.startOverrides[ilvl] ?: level.start
        c[ilvl] = if (c[ilvl] < 0) start else c[ilvl] + 1
        for (l in ilvl + 1 until 9) c[l] = -1
        if (level.fmt == "none") return null
        if (level.fmt == "bullet") return ListText.bulletGlyph(level.text)
        var text = level.text
        for (n in 1..9) {
            val token = "%$n"
            if (token !in text) continue
            val li = n - 1
            val lv = levels[li]
            val value = if (c[li] < 0) (def.startOverrides[li] ?: lv?.start ?: 1) else c[li]
            text = text.replace(token, ListText.format(value, lv?.fmt ?: "decimal"))
        }
        return text.ifEmpty { null }
    }

    // --- Paragraf --------------------------------------------------------------------------------------

    private fun convertParagraph(p: XNode, out: MutableList<WordBlock>, ctx: Ctx) {
        val pPr = p.child("w:pPr")
        val info = resolvePara(pPr, ctx)
        val runs = ArrayList<WordRun>()
        val extras = ArrayList<WordBlock>()
        walkInline(p.kids, info, runs, extras, null, ctx)

        val mark = info.baseR.copy().also { applyR(it, pPr?.child("w:rPr")) }
        val emptyPt = mark.sizePt ?: WordRun.DEFAULT_SIZE_PT

        if (info.p.pageBreakBefore) addPageBreak(out, ctx)

        // Sayfa sonu işaretleri ("\u000C") paragrafı parçalara böler.
        val segments = ArrayList<MutableList<WordRun>>()
        var current = ArrayList<WordRun>()
        for (run in mergeRuns(runs)) {
            if (run.text == "\u000C") { segments.add(current); current = ArrayList() } else current.add(run)
        }
        segments.add(current)

        for ((i, seg) in segments.withIndex()) {
            val first = i == 0
            val last = i == segments.lastIndex
            if (seg.isEmpty() && (segments.size > 1 || extras.isNotEmpty())) {
                // sayfa sonundan önce/sonra kalan boş parça ya da yalnızca görsel içeren paragraf: boş satır çizilmez
            } else {
                out.add(makeParagraph(seg, info, emptyPt, first, last))
            }
            if (!last) addPageBreak(out, ctx)
        }
        out.addAll(extras)

        pPr?.child("w:sectPr")?.let { sect ->
            if (sect.child("w:type")?.attr("val") != "continuous") addPageBreak(out, ctx)
        }
    }

    private fun makeParagraph(seg: List<WordRun>, info: ParaInfo, emptyPt: Float, first: Boolean, last: Boolean): WordParagraph {
        val p = info.p
        val maxSize = seg.maxOfOrNull { it.sizePt } ?: emptyPt
        val spacing = p.lineMult ?: p.lineExactPt?.let { (it / (maxSize * 1.2f)).coerceIn(0.8f, 3f) } ?: 1f
        return WordParagraph(
            runs = seg,
            align = p.align,
            heading = info.heading,
            indentPt = p.left,
            firstLinePt = p.firstLine,
            beforePt = if (first) p.before else 0f,
            afterPt = if (last) p.after else 0f,
            lineSpacing = spacing,
            label = if (first) info.label else null,
            background = p.shading,
            borderTop = if (first) p.borderTop else null,
            borderBottom = if (last) p.borderBottom else null,
            emptyPt = emptyPt,
        )
    }

    private fun mergeRuns(runs: List<WordRun>): List<WordRun> {
        val out = ArrayList<WordRun>(runs.size)
        for (r in runs) {
            val prev = out.lastOrNull()
            if (prev != null && r.text != "\u000C" && prev.text != "\u000C" && prev.copy(text = "") == r.copy(text = "")) {
                out[out.size - 1] = prev.copy(text = prev.text + r.text)
            } else {
                out.add(r)
            }
        }
        return out
    }

    // --- Satır içi içerik ------------------------------------------------------------------------------

    private fun currentLink(link: String?): String? = link ?: fields.lastOrNull { it.separated && it.link != null }?.link

    private fun walkInline(nodes: List<XNode>, info: ParaInfo, runs: MutableList<WordRun>, extras: MutableList<WordBlock>, link: String?, ctx: Ctx) {
        for (n in nodes) {
            if (truncated) return
            when (n.name) {
                "w:r" -> convertRun(n, info, runs, extras, link, ctx)
                "w:hyperlink" -> {
                    val url = n.attr("id")?.let { rels[it] }?.takeIf { it.external }?.let { safeUrl(it.target) }
                    walkInline(n.kids, info, runs, extras, url ?: link, ctx)
                }
                "w:fldSimple" -> walkInline(n.kids, info, runs, extras, hyperlinkOf(n.attr("instr")) ?: link, ctx)
                "w:ins", "w:smartTag", "w:customXml", "w:moveTo", "w:sdtContent", "w:bdo", "w:dir" ->
                    walkInline(n.kids, info, runs, extras, link, ctx)
                "w:sdt" -> n.child("w:sdtContent")?.let { walkInline(it.kids, info, runs, extras, link, ctx) }
                "mc:AlternateContent" -> choice(n)?.let { walkInline(it.kids, info, runs, extras, link, ctx) }
                "m:oMath", "m:oMathPara" -> {
                    val sb = StringBuilder()
                    collectMath(n, sb)
                    if (sb.isNotEmpty()) addRun(runs, sb.toString(), info.baseR.copy().also { it.italic = true }, link)
                }
            }
        }
    }

    private fun collectMath(n: XNode, sb: StringBuilder) {
        if (n.name == "m:t") sb.append(n.text)
        for (k in n.kids) collectMath(k, sb)
    }

    private fun hyperlinkOf(instr: String?): String? {
        if (instr == null) return null
        val text = instr.trim()
        if (!text.startsWith("HYPERLINK", ignoreCase = true) || "\\l" in text) return null
        val quoted = Regex("HYPERLINK\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val plain = Regex("HYPERLINK\\s+(\\S+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        return safeUrl(quoted ?: plain)
    }

    private fun convertRun(r: XNode, info: ParaInfo, runs: MutableList<WordRun>, extras: MutableList<WordBlock>, link: String?, ctx: Ctx) {
        val rPr = r.child("w:rPr")
        val props = info.baseR.copy()
        rPr?.child("w:rStyle")?.attr("val")?.let { id -> styles.chain(id).forEach { applyR(props, it.rPr) } }
        applyR(props, rPr)

        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                addRun(runs, sb.toString(), props, currentLink(link))
                sb.setLength(0)
            }
        }
        for (k in r.kids) {
            when (k.name) {
                "w:t" -> sb.append(k.text)
                "w:tab", "w:ptab" -> sb.append('\t')
                "w:br" -> when (k.attr("type")) {
                    "page" -> { flush(); runs.add(WordRun("\u000C")) }
                    else -> sb.append('\n')
                }
                "w:cr" -> sb.append('\n')
                "w:noBreakHyphen" -> sb.append('‑')
                "w:sym" -> k.attr("char")?.toIntOrNull(16)?.let { code ->
                    sb.append(if (code in 0xF000..0xF0FF) ListText.bulletGlyph(code.toChar().toString()) else code.toChar().toString())
                }
                "w:fldChar" -> {
                    flush()
                    when (k.attr("fldCharType")) {
                        "begin" -> fields.add(FieldState())
                        "separate" -> fields.lastOrNull()?.let { it.separated = true; it.link = hyperlinkOf(it.code.toString()) }
                        "end" -> if (fields.isNotEmpty()) fields.removeAt(fields.size - 1)
                    }
                }
                "w:instrText" -> fields.lastOrNull()?.takeIf { !it.separated }?.code?.append(k.text)
                "w:footnoteReference" -> {
                    flush()
                    val id = k.attr("id")
                    if (id != null && footnoteNode(id) != null) {
                        val number = footnoteOrder.indexOf(id).let { if (it >= 0) it + 1 else { footnoteOrder.add(id); footnoteOrder.size } }
                        addRun(runs, number.toString(), props.copy().also { it.script = 1 }, currentLink(link))
                    }
                }
                "w:drawing", "w:pict", "w:object", "mc:AlternateContent" -> {
                    flush()
                    graphic(k, info, extras)
                }
            }
        }
        flush()
    }

    private fun addRun(runs: MutableList<WordRun>, text: String, props: RProps, link: String?) {
        if (props.vanish || text.isEmpty()) return
        chars += text.length
        if (chars > DocxReader.MAX_CHARS) { truncated = true; return }
        val upper = props.caps || props.smallCaps
        var size = (props.sizePt ?: WordRun.DEFAULT_SIZE_PT) * (if (props.smallCaps && !props.caps) 0.85f else 1f)
        var script = props.script
        if (script == 0 && kotlin.math.abs(props.position) >= WordRun.MIN_RAISE_HALF_POINTS) {
            // yazarın seçtiği punto korunur: görüntüleyici simge boyutunu küçülteceği için burada büyütülür
            script = if (props.position > 0) 1 else -1
            size /= WordRun.SCRIPT_SHRINK
        }
        runs.add(
            WordRun(
                text = if (upper) text.uppercase(Locale.getDefault()) else text,
                bold = props.bold,
                italic = props.italic,
                underline = props.underline || link != null,
                strike = props.strike,
                color = props.color ?: if (link != null) LINK_COLOR else null,
                highlight = props.highlight,
                sizePt = size,
                font = props.font,
                script = script,
                link = link,
            ),
        )
    }

    // --- Görseller ve metin kutuları -------------------------------------------------------------------

    private fun graphic(g: XNode, info: ParaInfo, extras: MutableList<WordBlock>) {
        val align = when (info.p.align) { WordAlign.Justify -> WordAlign.Left; else -> info.p.align }
        when (g.name) {
            "w:drawing" -> for (d in g.kids) {
                if (d.name != "wp:inline" && d.name != "wp:anchor") continue
                if (d.name == "wp:anchor" && d.attr("behindDoc") == "1") continue // arka plan / filigran
                val ext = d.child("wp:extent")
                val w = (ext?.attr("cx")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                val h = (ext?.attr("cy")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                scanDrawing(d, w, h, countBlips(d), align, extras)
            }
            "w:pict", "w:object" -> scanVml(g, null, align, extras)
            "mc:AlternateContent" -> choice(g)?.kids?.forEach { graphic(it, info, extras) }
        }
    }

    private fun countBlips(n: XNode): Int = (if (n.name == "a:blip") 1 else 0) + n.kids.sumOf { countBlips(it) }

    private fun scanDrawing(n: XNode, w: Float, h: Float, blips: Int, align: WordAlign, extras: MutableList<WordBlock>) {
        when (n.name) {
            "a:blip" -> {
                addImage(n.attr("embed") ?: n.attr("link"), w, h, align, extras)
                return
            }
            "w:txbxContent" -> {
                convertBlocks(n.kids, extras, Ctx(emptyList(), emptyList(), true))
                return
            }
            "mc:AlternateContent" -> {
                choice(n)?.let { scanDrawing(it, w, h, blips, align, extras) }
                return
            }
            "pic:pic" -> if (blips > 1) {
                // grup içinde birden çok görsel: her görselin kendi ölçüsü
                val ext = n.child("pic:spPr")?.child("a:xfrm")?.child("a:ext")
                val pw = (ext?.attr("cx")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                val ph = (ext?.attr("cy")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                for (k in n.kids) scanDrawing(k, pw, ph, 1, align, extras)
                return
            }
        }
        for (k in n.kids) scanDrawing(k, w, h, blips, align, extras)
    }

    private fun scanVml(n: XNode, size: Pair<Float, Float>?, align: WordAlign, extras: MutableList<WordBlock>) {
        var current = size
        val style = n.attr("style")
        if (style != null && n.name.startsWith("v:")) {
            val w = cssLengthPt(style, "width")
            val h = cssLengthPt(style, "height")
            if (w != null && h != null) current = w to h
        }
        when (n.name) {
            "v:imagedata" -> {
                addImage(n.attr("id") ?: n.attr("relid"), current?.first ?: 0f, current?.second ?: 0f, align, extras)
                return
            }
            "w:txbxContent" -> {
                convertBlocks(n.kids, extras, Ctx(emptyList(), emptyList(), true))
                return
            }
            "mc:AlternateContent" -> {
                choice(n)?.let { scanVml(it, current, align, extras) }
                return
            }
        }
        for (k in n.kids) scanVml(k, current, align, extras)
    }

    private fun cssLengthPt(style: String, property: String): Float? {
        val m = Regex("(?:^|;)\\s*$property\\s*:\\s*([0-9.]+)\\s*(pt|px|in|cm|mm)?", RegexOption.IGNORE_CASE).find(style) ?: return null
        val v = m.groupValues[1].toFloatOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "px" -> v * 0.75f
            "in" -> v * 72f
            "cm" -> v * 28.346f
            "mm" -> v * 2.8346f
            else -> v
        }
    }

    private fun addImage(rid: String?, w: Float, h: Float, align: WordAlign, extras: MutableList<WordBlock>) {
        val rel = rid?.let { rels[it] } ?: return
        if (rel.external) return
        imageParts.add(rel.target)
        extras.add(WordImage(rel.target, w, h, align))
    }

    // --- Tablolar --------------------------------------------------------------------------------------

    private fun lookOf(tblPr: XNode?): Look {
        val n = tblPr?.child("w:tblLook") ?: return Look(firstRow = true, lastRow = false, firstCol = true, lastCol = false, noHBand = false)
        val mask = n.attr("val")?.toIntOrNull(16)
        fun flag(name: String, bit: Int): Boolean =
            if (mask != null) mask and bit != 0 else n.attr(name)?.let { it == "1" || it.equals("true", true) } ?: false
        return Look(flag("firstRow", 0x20), flag("lastRow", 0x40), flag("firstColumn", 0x80), flag("lastColumn", 0x100), flag("noHBand", 0x200))
    }

    private fun tableBorderColor(tblPr: XNode?, chain: List<StyleDef>): Int? {
        fun fromBorders(b: XNode): Int? {
            for (side in listOf("w:top", "w:left", "w:bottom", "w:right", "w:insideH", "w:insideV")) {
                borderColor(b.child(side))?.let { return it }
            }
            return null
        }
        tblPr?.child("w:tblBorders")?.let { return fromBorders(it) } // açıkça belirtilmişse stil dikkate alınmaz
        for (def in chain.reversed()) def.tblPr?.child("w:tblBorders")?.let { return fromBorders(it) }
        return null
    }

    private fun convertTable(tbl: XNode, ctx: Ctx): WordTable? {
        val tblPr = tbl.child("w:tblPr")
        val styleId = tblPr?.child("w:tblStyle")?.attr("val") ?: styles.defaultTable
        val chain = styles.chain(styleId)
        val look = lookOf(tblPr)
        val borderColor = tableBorderColor(tblPr, chain)

        val trs = tbl.children("w:tr")
        if (trs.isEmpty()) return null

        val grid = ArrayList<Float>()
        tbl.child("w:tblGrid")?.children("w:gridCol")?.forEach { grid.add(((it.attr("w")?.toFloatOrNull() ?: 0f) / 20f).coerceAtLeast(1f)) }

        val rows = ArrayList<WordRow>()
        var maxCols = 0
        for ((ri, tr) in trs.withIndex()) {
            if (truncated) break
            val trPr = tr.child("w:trPr")
            val cells = ArrayList<WordCell>()
            var col = 0
            trPr?.child("w:gridBefore")?.attr("val")?.toIntOrNull()?.takeIf { it > 0 }?.let {
                cells.add(WordCell(emptyList(), it)); col += it
            }
            val tcs = ArrayList<XNode>()
            for (k in tr.kids) {
                if (k.name == "w:tc") tcs.add(k)
                else if (k.name == "w:sdt") k.child("w:sdtContent")?.children("w:tc")?.let { tcs.addAll(it) }
            }
            for (tc in tcs) {
                val tcPr = tc.child("w:tcPr")
                val span = tcPr?.child("w:gridSpan")?.attr("val")?.toIntOrNull()?.coerceIn(1, 64) ?: 1
                val vMerge = tcPr?.child("w:vMerge")
                val continues = vMerge != null && vMerge.attr("val") != "restart"

                val conds = conditionalTypes(ri, trs.size, col, look)
                val condNodes = conds.flatMap { type -> chain.mapNotNull { it.conditional[type] } }
                var shading: Int? = tcPr?.child("w:shd")?.let { fill(it) }
                if (shading == null) shading = condNodes.asReversed().firstNotNullOfOrNull { c -> c.child("w:tcPr")?.child("w:shd")?.let { fill(it) } }
                if (shading == null) shading = chain.asReversed().firstNotNullOfOrNull { d -> d.tcPr?.child("w:shd")?.let { fill(it) } }
                val cellBorder = tcPr?.child("w:tcBorders")?.let { b ->
                    listOf("w:top", "w:left", "w:bottom", "w:right").any { borderColor(b.child(it)) != null }
                } ?: false

                val rBase = chain.mapNotNull { it.rPr } + condNodes.mapNotNull { it.child("w:rPr") }
                val pBase = chain.mapNotNull { it.pPr } + condNodes.mapNotNull { it.child("w:pPr") }
                val blocks = ArrayList<WordBlock>()
                if (!continues) convertBlocks(tc.kids, blocks, Ctx(rBase, pBase, true))
                cells.add(WordCell(blocks, span, shading, cellBorder, continues))
                col += span
            }
            trPr?.child("w:gridAfter")?.attr("val")?.toIntOrNull()?.takeIf { it > 0 }?.let {
                cells.add(WordCell(emptyList(), it)); col += it
            }
            maxCols = maxOf(maxCols, col)
            rows.add(WordRow(cells))
        }
        if (rows.isEmpty()) return null

        while (grid.size < maxCols) grid.add(if (grid.isEmpty()) 100f else grid.average().toFloat())
        return WordTable(grid, rows, borderColor)
    }

    /** Hücrenin koşullu stil adları, uygulama sırasıyla (sonraki öncekini ezer). */
    private fun conditionalTypes(row: Int, rowCount: Int, col: Int, look: Look): List<String> {
        val out = ArrayList<String>()
        if (!look.noHBand) {
            val dataIndex = row - (if (look.firstRow) 1 else 0)
            val isLast = look.lastRow && row == rowCount - 1
            if (dataIndex >= 0 && !isLast) out.add(if (dataIndex % 2 == 0) "band1Horz" else "band2Horz")
        }
        if (look.firstCol && col == 0) out.add("firstCol")
        if (look.firstRow && row == 0) out.add("firstRow")
        if (look.lastRow && row == rowCount - 1) out.add("lastRow")
        return out
    }

    // --- Dipnotlar -------------------------------------------------------------------------------------

    private fun footnoteNode(id: String): XNode? {
        val f = footnotes?.children("w:footnote")?.firstOrNull { it.attr("id") == id } ?: return null
        val type = f.attr("type")
        return if (type == "separator" || type == "continuationSeparator" || type == "continuationNotice") null else f
    }

    private fun appendFootnotes(out: MutableList<WordBlock>) {
        if (footnoteOrder.isEmpty()) return
        out.add(WordParagraph(listOf(WordRun("Dipnotlar", bold = true, sizePt = 12f)), beforePt = 18f, afterPt = 6f, borderTop = 0xFF9E9E9E.toInt()))
        var i = 0
        while (i < footnoteOrder.size) { // dipnot metni yeni dipnot içeremez, ama liste büyürse güvenle döner
            val node = footnoteNode(footnoteOrder[i])
            i++
            if (node == null) continue
            val sub = ArrayList<WordBlock>()
            convertBlocks(node.kids, sub, Ctx(emptyList(), emptyList(), true))
            var labelled = false
            for (b in sub) {
                if (b is WordParagraph && !labelled) {
                    labelled = true
                    // dipnot numarası etiket olur; metnin başındaki boşluk/sekme atılır
                    val small = b.runs.map { it.copy(sizePt = minOf(it.sizePt, 10f)) }.toMutableList()
                    if (small.isNotEmpty()) small[0] = small[0].copy(text = small[0].text.trimStart())
                    out.add(b.copy(label = "$i", indentPt = 18f, firstLinePt = -18f, runs = small.filter { it.text.isNotEmpty() }))
                } else {
                    out.add(b)
                }
            }
        }
    }
}
