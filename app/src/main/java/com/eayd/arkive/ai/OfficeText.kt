package com.eayd.arkive.ai

import com.eayd.arkive.word.WordFormatException
import com.eayd.arkive.word.XNode
import com.eayd.arkive.word.Xml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.MathContext
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Excel ya da PowerPoint dosyası okunamadı. [tooBig] dosyanın açılınca sınırı aştığını söyler. */
internal class OfficeTextException(val tooBig: Boolean = false, cause: Throwable? = null) : Exception(cause)

/**
 * Excel ve PowerPoint dosyalarından özetlenecek düz metni çıkarır; sunucudaki openpyxl ve python-pptx okumasıyla aynı
 * biçimi verir. Android'e bağlı değil, testlerle denenir.
 */
internal object OfficeText {

    /** Zip'teki bir parçanın açılmış en büyük boyutu. */
    private const val MAX_PART_BYTES = 8 * 1024 * 1024

    /** Okunan bütün parçaların açılmış toplamı. */
    private const val MAX_TOTAL_BYTES = 32 * 1024 * 1024

    /** Excel'in en fazla sütun sayısı (XFD). */
    private const val MAX_COLUMNS = 16_384

    /** Excel'in hazır tarih ve saat biçimleri (numFmtId). */
    private val BUILTIN_DATE_FORMATS = (14..22).toSet() + (45..47)

    /** Biçimde tarih harfi aranmadan önce silinenler: tırnaklı yazılar, kaçışlı karakterler, renk ve yerel ayar. */
    private val FORMAT_NOISE = Regex("\\[(?!h{1,2}\\]|m{1,2}\\]|s{1,2}\\])[^\\]]*\\]|\\\\.|\"[^\"]*\"")
    private val DATE_LETTERS = Regex("[dmyhs]", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")

    /** Excel sayfaları: her sayfa "# Sayfa adı" başlığıyla, her satır hücreleri " | " ile ayrılmış tek satır. */
    fun excel(bytes: ByteArray): String = readable {
        val parts = unzip(bytes) { it.startsWith("xl/") && (it.endsWith(".xml") || it.endsWith(".rels")) }
        val workbook = parts["xl/workbook.xml"]?.let(Xml::parse) ?: throw OfficeTextException()
        val targets = relationships(parts["xl/_rels/workbook.xml.rels"], "xl/")
        val shared = parts["xl/sharedStrings.xml"]?.let { Xml.parse(it).children("si").map(::textOf) }.orEmpty()
        val dates = parts["xl/styles.xml"]?.let { dateStyles(Xml.parse(it)) }.orEmpty()
        val date1904 = workbook.child("workbookPr")?.attr("date1904")?.let { it == "1" || it == "true" } == true
        val lines = ArrayList<String>()
        for (sheet in workbook.child("sheets")?.children("sheet").orEmpty()) {
            val xml = targets[sheet.attr("id")]?.let(parts::get) ?: continue
            lines += "# ${sheet.attr("name").orEmpty()}"
            val rows = Xml.parse(xml).child("sheetData")?.children("row").orEmpty()
            for (row in rows) {
                val cells = ArrayList<String>()
                for (cell in row.children("c")) {
                    val column = cell.attr("r")?.let(::columnOf) ?: cells.size
                    if (column !in 0 until MAX_COLUMNS) continue
                    while (cells.size <= column) cells += ""
                    cells[column] = cellText(cell, shared, dates, date1904)
                }
                while (cells.isNotEmpty() && cells.last().isEmpty()) cells.removeAt(cells.lastIndex)
                if (cells.isNotEmpty()) lines += cells.joinToString(" | ")
            }
        }
        lines.joinToString("\n")
    }

    /**
     * Sunu: slayt sırasıyla "# Slayt N" başlığı, metin kutuları, tablolar (hücreler " | " ile) ve konuşmacı notları.
     *
     * [english] özet İngilizce yazılacaksa başlıklar da İngilizce olur ("# Slide N", "Speaker notes").
     */
    fun powerPoint(bytes: ByteArray, english: Boolean): String = readable {
        val parts = unzip(bytes) { it.startsWith("ppt/") && (it.endsWith(".xml") || it.endsWith(".rels")) }
        val slideWord = if (english) "Slide" else "Slayt"
        val notesWord = if (english) "Speaker notes" else "Konuşmacı notu"
        val lines = ArrayList<String>()
        slideOrder(parts).forEachIndexed { index, path ->
            lines += "# $slideWord ${index + 1}"
            val slide = parts[path] ?: return@forEachIndexed
            collectSlideLines(Xml.parse(slide), lines)
            val notesPath = relationships(parts[relsPathOf(path)], path.substringBeforeLast('/') + "/", "/notesSlide")
                .values.firstOrNull()
            val notes = notesPath?.let(parts::get)?.let { notesText(Xml.parse(it)) }.orEmpty()
            if (notes.isNotEmpty()) lines += "$notesWord: $notes"
        }
        if (lines.isEmpty()) throw OfficeTextException()
        lines.joinToString("\n")
    }

    /** Ayrıştırma hatalarını [OfficeTextException]'a çevirir. */
    private inline fun readable(block: () -> String): String = try {
        block()
    } catch (e: WordFormatException) {
        throw OfficeTextException(cause = e)
    } catch (e: ZipException) {
        throw OfficeTextException(cause = e)
    } catch (e: IllegalArgumentException) {
        throw OfficeTextException(cause = e)
    }

    /** Zip'ten istenen parçaları açar; sınırı aşan dosya zip bombası sayılır. */
    private fun unzip(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val parts = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("/")
                if (entry.isDirectory || !wanted(name)) continue
                val out = ByteArrayOutputStream()
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    if (out.size() + n > MAX_PART_BYTES || total > MAX_TOTAL_BYTES) throw OfficeTextException(tooBig = true)
                    out.write(buffer, 0, n)
                }
                parts[name] = out.toByteArray()
            }
        }
        if (parts.isEmpty()) throw OfficeTextException()
        return parts
    }

    /** İlişki dosyasındaki hedefler: kimlik -> zip içindeki yol. [typeSuffix] verilirse yalnızca o türdekiler. */
    private fun relationships(xml: ByteArray?, base: String, typeSuffix: String? = null): Map<String, String> {
        val root = xml?.let(Xml::parse) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (rel in root.children("Relationship")) {
            if (rel.attr("TargetMode") == "External") continue
            if (typeSuffix != null && rel.attr("Type")?.endsWith(typeSuffix) != true) continue
            val id = rel.attr("Id") ?: continue
            val target = rel.attr("Target") ?: continue
            out[id] = resolve(base, target)
        }
        return out
    }

    /** "ppt/slides/" ve "../notesSlides/notesSlide1.xml" -> "ppt/notesSlides/notesSlide1.xml". */
    private fun resolve(base: String, target: String): String {
        val path = if (target.startsWith("/")) target.removePrefix("/") else base + target
        val out = ArrayList<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                else -> out += part
            }
        }
        return out.joinToString("/")
    }

    private fun relsPathOf(path: String): String =
        path.substringBeforeLast('/') + "/_rels/" + path.substringAfterLast('/') + ".rels"

    /** "AB12" -> 27. */
    private fun columnOf(reference: String): Int? {
        var column = 0
        var letters = 0
        for (ch in reference) {
            if (ch !in 'A'..'Z' && ch !in 'a'..'z') break
            column = column * 26 + (ch.uppercaseChar() - 'A' + 1)
            letters++
            if (letters > 3) return null
        }
        return if (letters == 0) null else column - 1
    }

    /** Yazının kendisi; Japonca okunuş ipuçları (rPh) atlanır. */
    private fun textOf(node: XNode): String {
        val sb = StringBuilder()
        fun walk(n: XNode) {
            if (n.name == "rPh") return
            if (n.name == "t") sb.append(n.text)
            for (kid in n.kids) walk(kid)
        }
        walk(node)
        return sb.toString()
    }

    private fun collapse(text: String): String = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

    private fun cellText(cell: XNode, shared: List<String>, dates: Set<Int>, date1904: Boolean): String {
        val value = cell.child("v")?.text?.trim()
        val text = when (cell.attr("t")) {
            "s" -> value?.toIntOrNull()?.let(shared::getOrNull).orEmpty()
            "inlineStr" -> cell.child("is")?.let(::textOf).orEmpty()
            "str", "e" -> value.orEmpty()
            "b" -> if (value == "1") "TRUE" else "FALSE"
            else -> value?.let { number(it, cell.attr("s")?.toIntOrNull() in dates, date1904) }.orEmpty()
        }
        return collapse(text)
    }

    private fun number(raw: String, isDate: Boolean, date1904: Boolean): String {
        val value = raw.toDoubleOrNull() ?: return raw
        if (value.isNaN() || value.isInfinite()) return raw
        if (isDate && value >= 0) return excelDate(value, date1904)
        if (value == floor(value) && abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal(value).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    /** Excel'in gün sayısını "2026-11-28" ya da "2026-11-28 14:30" yapar; 1 günden kısası yalnızca saattir. */
    private fun excelDate(serial: Double, date1904: Boolean): String {
        var days = floor(serial).toLong()
        var seconds = ((serial - days) * 86_400).roundToLong()
        if (seconds >= 86_400) {
            days++
            seconds -= 86_400
        }
        val time = String.format(Locale.ROOT, "%02d:%02d", seconds / 3600, seconds % 3600 / 60)
        if (days == 0L && !date1904) return time
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        when {
            date1904 -> calendar.set(1904, Calendar.JANUARY, 1)
            days < 61 -> calendar.set(1899, Calendar.DECEMBER, 31)
            else -> calendar.set(1899, Calendar.DECEMBER, 30)
        }
        calendar.add(Calendar.DAY_OF_MONTH, days.coerceAtMost(3_000_000).toInt())
        val date = String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        return if (seconds == 0L) date else "$date $time"
    }

    /** Tarih ya da saat biçimli hücre stillerinin sıraları (cellXfs içindeki yerleri). */
    private fun dateStyles(styles: XNode): Set<Int> {
        val custom = styles.child("numFmts")?.children("numFmt").orEmpty()
            .mapNotNull { f -> f.attr("numFmtId")?.toIntOrNull()?.let { it to f.attr("formatCode").orEmpty() } }
            .toMap()
        val out = HashSet<Int>()
        styles.child("cellXfs")?.children("xf").orEmpty().forEachIndexed { index, xf ->
            val id = xf.attr("numFmtId")?.toIntOrNull() ?: return@forEachIndexed
            val code = custom[id]
            val isDate = if (code != null) isDateFormat(code) else id in BUILTIN_DATE_FORMATS
            if (isDate) out += index
        }
        return out
    }

    private fun isDateFormat(code: String): Boolean {
        if (code.equals("General", ignoreCase = true)) return false
        return DATE_LETTERS.containsMatchIn(FORMAT_NOISE.replace(code, ""))
    }

    /** Slaytların sunudaki sırası; okunamazsa dosya numarasına göre. */
    private fun slideOrder(parts: Map<String, ByteArray>): List<String> {
        val numbered = parts.keys.mapNotNull { name ->
            SLIDE_FILE.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to name }
        }.sortedBy { it.first }.map { it.second }
        val presentation = parts["ppt/presentation.xml"]?.let(Xml::parse) ?: return numbered
        val targets = relationships(parts["ppt/_rels/presentation.xml.rels"], "ppt/", "/slide")
        val ordered = presentation.child("sldIdLst")?.children("sldId").orEmpty().map { targets[it.attr("id")] }
        return if (ordered.isNotEmpty() && ordered.all { it != null && it in parts }) ordered.filterNotNull() else numbered
    }

    private val SLIDE_FILE = Regex("ppt/slides/slide(\\d+)\\.xml")

    /** Slayttaki paragraflar ve tablolar, belgedeki sırayla. */
    private fun collectSlideLines(node: XNode, out: MutableList<String>) {
        when (node.name) {
            "a:tbl" -> {
                for (row in node.children("a:tr")) {
                    val cells = row.children("a:tc").map { cell -> collapse(paragraphs(cell).joinToString(" ")) }
                    if (cells.any { it.isNotEmpty() }) out += cells.joinToString(" | ")
                }
                return
            }
            "txBody" -> {
                val text = paragraphs(node).joinToString("\n").trim()
                if (text.isNotEmpty()) out += text
                return
            }
        }
        for (kid in node.kids) collectSlideLines(kid, out)
    }

    /** Metin gövdesindeki paragrafların yazıları; satır sonları korunur. */
    private fun paragraphs(body: XNode): List<String> {
        val out = ArrayList<String>()
        fun walk(n: XNode) {
            if (n.name == "a:p") {
                val sb = StringBuilder()
                for (kid in n.kids) {
                    when (kid.name) {
                        "a:r", "a:fld" -> sb.append(kid.child("a:t")?.text.orEmpty())
                        "a:br" -> sb.append('\n')
                    }
                }
                out += sb.toString()
                return
            }
            for (kid in n.kids) walk(kid)
        }
        walk(body)
        return out
    }

    /** Not sayfasındaki asıl not alanının yazısı; slayt resmi ve sayfa numarası alanları atlanır. */
    private fun notesText(notes: XNode): String {
        val out = ArrayList<String>()
        fun walk(n: XNode) {
            if (n.name == "sp") {
                val placeholder = n.child("nvSpPr")?.child("nvPr")?.child("ph")
                if (placeholder?.attr("type") == "body") {
                    n.child("txBody")?.let { out += paragraphs(it).joinToString("\n").trim() }
                }
                return
            }
            for (kid in n.kids) walk(kid)
        }
        walk(notes)
        return out.filter { it.isNotEmpty() }.joinToString("\n")
    }
}
