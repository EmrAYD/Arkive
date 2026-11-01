package com.eayd.arkive.word

import java.nio.charset.Charset
import java.util.Locale

/**
 * Eski (Word 97-2003) .doc dosyasını [WordDoc]'a çevirir. Biçim [MS-DOC] belirtimindedir: metin "parça tablosu"ndan
 * (piece table) okunur; karakter ve paragraf biçimleri 512 baytlık FKP sayfalarındaki "sprm" listelerinden gelir.
 * Desteklenenler: kalın/italik/altı çizili/üstü çizili, renk, boyut, üst/alt simge, yazı tipi ailesi, hizalama,
 * girinti, boşluk, başlık stilleri, numaralı/madde işaretli listeler, tablolar, satır içi JPEG/PNG görseller,
 * köprüler. Biçim katmanı beklenmedik bir yapıyla karşılaşırsa belge düz metin olarak yine de açılır.
 * Kayan (sayfaya bağlı) çizimler, üst/alt bilgi, dipnot ve metin kutuları gösterilmez.
 */
internal object DocReader {
    private val CP1252: Charset = Charset.forName("windows-1252")

    /**
     * [password]: parolalı belge için kullanıcının girdiği parola. Parola gerekiyorsa ve verilmediyse ya da yanlışsa
     * [com.eayd.arkive.ai.PasswordException] atılır.
     */
    fun read(ole: OleFile, password: String? = null): WordDoc {
        var wd = ole.stream("WordDocument") ?: throw WordFormatException("Word belgesi okunamadı.")
        if (wd.size < 0x100 || u16(wd, 0) != 0xA5EC) throw WordFormatException("Word belgesi okunamadı (bozuk).")
        val nFib = u16(wd, 2)
        val flags = u16(wd, 0x0A)
        val encrypted = flags and 0x0100 != 0
        if (encrypted && (nFib < 0xC1 || flags and 0x8000 != 0)) {
            // Word 6/95 ve XOR ile gizlenmiş belgeler: yöntem belgelenmiş ama bu uygulamada desteklenmiyor.
            throw WordFormatException("Bu parolalı .doc şifreleme yöntemi desteklenmiyor.")
        }
        if (!encrypted && nFib < 0xC1) return readWord6(wd)

        var table = ole.stream(if (flags and 0x0200 != 0) "1Table" else "0Table")
            ?: throw WordFormatException("Word belgesi okunamadı (tablo akışı yok).")
        var data: ByteArray? = runCatching { ole.stream("Data") }.getOrNull()
        if (encrypted) {
            val clear = WordCrypto.decrypt(wd, table, data, password)
            wd = clear.wordDocument
            table = clear.table
            data = clear.data
        }
        val fib = Fib.parse(wd)
        val pieces = readPieces(wd, table, fib)
        val text = buildText(wd, pieces, fib.ccpText)
        if (text.isEmpty()) throw WordFormatException("Belgede metin yok.")
        return try {
            Builder(data, wd, table, fib, pieces, text).build()
        } catch (e: WordFormatException) {
            plainDoc(text)
        } catch (e: RuntimeException) {
            plainDoc(text)
        }
    }

    // --- Düşük düzey okuma yardımcıları ---------------------------------------------------------------

    private fun u16(b: ByteArray, o: Int): Int = OleFile.u16(b, o)
    private fun u32(b: ByteArray, o: Int): Long = OleFile.u32(b, o)
    private fun i16(b: ByteArray, o: Int): Int = u16(b, o).toShort().toInt()
    private fun i32(b: ByteArray, o: Int): Int = u32(b, o).toInt()
    private fun u8(b: ByteArray, o: Int): Int = b[o].toInt() and 0xFF

    // --- FIB ------------------------------------------------------------------------------------------

    private class Fib(val ccpText: Int, private val base: Int, private val pairCount: Int, private val wd: ByteArray) {
        /** FibRgFcLcb97 içindeki [index]. (fc, lcb) çifti; yoksa null. */
        fun pair(index: Int): Pair<Int, Int>? {
            if (index >= pairCount) return null
            val o = base + index * 8
            if (o + 8 > wd.size) return null
            val fc = u32(wd, o)
            val lcb = u32(wd, o + 4)
            if (lcb == 0L || fc > Int.MAX_VALUE || lcb > Int.MAX_VALUE) return null
            return fc.toInt() to lcb.toInt()
        }

        companion object {
            const val STSHF = 1
            const val PLCF_BTE_CHPX = 12
            const val PLCF_BTE_PAPX = 13
            const val STTBF_FFN = 15
            const val CLX = 33
            const val PLF_LST = 73
            const val PLF_LFO = 74

            fun parse(wd: ByteArray): Fib {
                val csw = u16(wd, 0x20)
                var p = 0x22 + csw * 2
                val cslw = u16(wd, p)
                p += 2
                val rgLw = p
                val ccpText = i32(wd, rgLw + 12)
                p = rgLw + cslw * 4
                val cb = u16(wd, p)
                if (ccpText <= 0) throw WordFormatException("Belgede metin yok.")
                return Fib(ccpText, p + 2, cb, wd)
            }
        }
    }

    // --- Parça tablosu ve metin ----------------------------------------------------------------------

    private class Piece(val cpStart: Int, val cpEnd: Int, val fcByte: Int, val compressed: Boolean) {
        val byteLen: Int get() = if (compressed) cpEnd - cpStart else (cpEnd - cpStart) * 2
    }

    private fun readPieces(wd: ByteArray, table: ByteArray, fib: Fib): List<Piece> {
        val (fc, lcb) = fib.pair(Fib.CLX) ?: throw WordFormatException("Parça tablosu yok.")
        var pos = fc
        val end = minOf(fc + lcb, table.size)
        while (pos < end) {
            when (u8(table, pos)) {
                1 -> pos += 3 + u16(table, pos + 1)
                2 -> {
                    val size = i32(table, pos + 1)
                    val plc = pos + 5
                    if (size < 4 || plc + size > table.size) throw WordFormatException("Parça tablosu bozuk.")
                    val n = (size - 4) / 12
                    val out = ArrayList<Piece>(n)
                    for (i in 0 until n) {
                        val cpS = i32(table, plc + 4 * i)
                        val cpE = i32(table, plc + 4 * (i + 1))
                        val raw = u32(table, plc + 4 * (n + 1) + 8 * i + 2)
                        val compressed = raw and 0x40000000L != 0L
                        val fcv = raw and 0x3FFFFFFFL
                        val byte = if (compressed) fcv / 2 else fcv
                        if (cpE < cpS || cpS < 0 || byte > Int.MAX_VALUE) continue
                        out.add(Piece(cpS, cpE, byte.toInt(), compressed))
                    }
                    return out
                }
                else -> throw WordFormatException("Parça tablosu bozuk.")
            }
        }
        throw WordFormatException("Parça tablosu bulunamadı.")
    }

    private fun buildText(wd: ByteArray, pieces: List<Piece>, ccpText: Int): String {
        val sb = StringBuilder()
        for (p in pieces) {
            if (p.cpStart >= ccpText) break
            val cps = minOf(p.cpEnd, ccpText) - p.cpStart
            if (cps <= 0) continue
            val byteLen = if (p.compressed) cps else cps * 2
            val avail = minOf(byteLen, wd.size - p.fcByte)
            if (avail <= 0) continue
            if (p.compressed) sb.append(String(wd, p.fcByte, avail, CP1252))
            else sb.append(String(wd, p.fcByte, avail and 1.inv(), Charsets.UTF_16LE))
            if (sb.length > DocxReader.MAX_CHARS) break
        }
        return sb.toString()
    }

    private fun readWord6(wd: ByteArray): WordDoc {
        val fcMin = i32(wd, 0x18)
        val fcMac = i32(wd, 0x1C)
        if (fcMin < 0 || fcMac <= fcMin || fcMac > wd.size) throw WordFormatException("Word belgesi okunamadı (eski sürüm).")
        return plainDoc(String(wd, fcMin, fcMac - fcMin, CP1252))
    }

    /** Biçimsiz, yalnızca paragraflardan oluşan belge. */
    fun plainDoc(text: String): WordDoc {
        val blocks = ArrayList<WordBlock>()
        var total = 0
        var truncated = false
        for (line in text.split('\r', '\u0007', '\n')) {
            val clean = line.filter { it >= ' ' || it == '\t' }
            total += clean.length
            if (total > DocxReader.MAX_CHARS) { truncated = true; break }
            blocks.add(WordParagraph(if (clean.isEmpty()) emptyList() else listOf(WordRun(clean)), afterPt = 6f))
        }
        return WordDoc(blocks, emptyMap(), truncated, "doc")
    }

    // --- Sprm ayrıştırma ------------------------------------------------------------------------------

    /** Bir sprm listesindeki her sprm için [action](sprm, işlenenin başlangıcı, işlenenin uzunluğu) çağırır. */
    private fun forEachSprm(g: ByteArray, action: (Int, Int, Int) -> Unit) {
        var pos = 0
        while (pos + 2 <= g.size) {
            val sprm = u16(g, pos)
            pos += 2
            val len = when (sprm shr 13) {
                0, 1 -> 1
                2, 4, 5 -> 2
                3 -> 4
                7 -> 3
                else -> {
                    if (pos >= g.size) return
                    when {
                        sprm == 0xD608 -> if (pos + 2 > g.size) return else u16(g, pos) + 1
                        sprm == 0xC615 && u8(g, pos) == 255 -> return
                        else -> u8(g, pos) + 1
                    }
                }
            }
            if (pos + len > g.size) return
            action(sprm, pos, len)
            pos += len
        }
    }

    private class Chp {
        var bold = false
        var italic = false
        var strike = false
        var underline = false
        var smallCaps = false
        var caps = false
        var vanish = false
        var color: Int? = null
        var highlight: Int? = null
        var hps = 20
        var script = 0
        var position = 0
        var fSpec = false
        var picLoc = -1
        var ftc = -1

        fun copy(): Chp {
            val c = Chp()
            c.bold = bold; c.italic = italic; c.strike = strike; c.underline = underline
            c.smallCaps = smallCaps; c.caps = caps; c.vanish = vanish; c.color = color; c.highlight = highlight
            c.hps = hps; c.script = script; c.position = position; c.fSpec = fSpec; c.picLoc = picLoc; c.ftc = ftc
            return c
        }
    }

    private class Pap {
        var istd = 0
        var jc = 0
        var left = 0f
        var firstLine = 0f
        var before = 0f
        var after = 0f
        var lineMult: Float? = 1f
        var lineExactPt: Float? = null
        var inTable = false
        var ttp = false
        var itap = 0
        var innerCell = false
        var innerTtp = false
        var ilvl = 0
        var ilfo = 0
        var pageBreakBefore = false
        var centers: IntArray? = null
        var cellFlags: IntArray? = null
        var cellShades: IntArray? = null   // ARGB, 0: gölge yok
        var anyBorder = false
    }

    private val ico = intArrayOf(
        0, 0xFF000000.toInt(), 0xFF0000FF.toInt(), 0xFF00FFFF.toInt(), 0xFF00FF00.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt(),
        0xFFFFFF00.toInt(), 0xFFFFFFFF.toInt(), 0xFF000080.toInt(), 0xFF008080.toInt(), 0xFF008000.toInt(), 0xFF800080.toInt(),
        0xFF800000.toInt(), 0xFF808000.toInt(), 0xFF808080.toInt(), 0xFFC0C0C0.toInt(),
    )

    private fun toggle(current: Boolean, op: Int): Boolean = when (op) {
        0 -> false
        1 -> true
        0x81 -> !current
        else -> current // 0x80: stildeki gibi
    }

    // --- Stil sayfası --------------------------------------------------------------------------------

    private class StyleInfo(val sti: Int, val name: String, val type: Int, val basedOn: Int, val pap: ByteArray, val chp: ByteArray)

    private fun parseStyles(table: ByteArray, fib: Fib): Array<StyleInfo?> {
        val (fc, lcb) = fib.pair(Fib.STSHF) ?: return emptyArray()
        val end = minOf(fc + lcb, table.size)
        val cbStshi = u16(table, fc)
        val cstd = u16(table, fc + 2)
        val cbBase = u16(table, fc + 4)
        var pos = fc + 2 + cbStshi
        val out = arrayOfNulls<StyleInfo>(cstd)
        for (i in 0 until cstd) {
            if (pos + 2 > end) break
            val cb = u16(table, pos)
            pos += 2
            if (cb == 0) continue
            val s = pos
            val e = minOf(pos + cb, end)
            pos += cb
            if (cbBase < 10 || s + cbBase + 4 > e) continue
            val sti = u16(table, s) and 0x0FFF
            val w1 = u16(table, s + 2)
            val sgc = w1 and 0xF
            val basedOn = (w1 shr 4) and 0x0FFF
            val cupx = u16(table, s + 4) and 0xF
            var q = s + cbBase
            val cch = u16(table, q)
            if (q + 2 + cch * 2 > e) continue
            val name = String(table, q + 2, cch * 2, Charsets.UTF_16LE)
            q += 2 + cch * 2 + 2
            var papx = ByteArray(0)
            var chpx = ByteArray(0)
            for (k in 0 until cupx) {
                if (q + 2 > e) break
                val cbUpx = u16(table, q)
                q += 2
                if (q + cbUpx > e) break
                val data = table.copyOfRange(q, q + cbUpx)
                q += cbUpx + (cbUpx and 1)
                when {
                    sgc == 1 && k == 0 -> if (data.size >= 2) papx = data.copyOfRange(2, data.size)
                    sgc == 1 && k == 1 -> chpx = data
                    sgc == 2 && k == 0 -> chpx = data
                }
            }
            out[i] = StyleInfo(sti, name, sgc, basedOn, papx, chpx)
        }
        return out
    }

    // --- FKP aralıkları ------------------------------------------------------------------------------

    /** [start, end) karakter konumu aralığı ve o aralığın sprm listesi. */
    private class Range(val start: Int, val end: Int, val grpprl: ByteArray, val istd: Int) {
        val chpCache = HashMap<Int, Chp>(2)
        var papCache: Pap? = null
    }

    private fun readRanges(wd: ByteArray, table: ByteArray, plc: Pair<Int, Int>?, pieces: List<Piece>, paragraph: Boolean): List<Range> {
        if (plc == null) return emptyList()
        val (fc, lcb) = plc
        if (fc + lcb > table.size || lcb < 8) return emptyList()
        val n = (lcb - 4) / 8
        val sorted = pieces.sortedBy { it.fcByte }
        val out = ArrayList<Range>()

        fun addFcRange(a: Int, b: Int, grpprl: ByteArray, istd: Int) {
            if (b <= a) return
            // a'dan sonra biten ilk parçayı ikili aramayla bul
            var lo = 0
            var hi = sorted.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid].fcByte + sorted[mid].byteLen > a) hi = mid else lo = mid + 1
            }
            var i = lo
            while (i < sorted.size && sorted[i].fcByte < b) {
                val p = sorted[i]
                val from = maxOf(a, p.fcByte)
                val to = minOf(b, p.fcByte + p.byteLen)
                if (to > from) {
                    val cpS = p.cpStart + if (p.compressed) from - p.fcByte else (from - p.fcByte) / 2
                    val cpE = p.cpStart + if (p.compressed) to - p.fcByte else (to - p.fcByte + 1) / 2
                    out.add(Range(cpS, cpE, grpprl, istd))
                }
                i++
            }
        }

        for (i in 0 until n) {
            val pn = (u32(table, fc + 4 * (n + 1) + 4 * i) and 0x3FFFFF).toInt()
            val page = pn * 512
            if (page < 0 || page + 512 > wd.size) continue
            val count = u8(wd, page + 511)
            for (j in 0 until count) {
                val a = i32(wd, page + 4 * j)
                val b = i32(wd, page + 4 * (j + 1))
                if (paragraph) {
                    val bOffset = u8(wd, page + 4 * (count + 1) + 13 * j)
                    if (bOffset == 0) { addFcRange(a, b, ByteArray(0), 0); continue }
                    val po = page + bOffset * 2
                    if (po + 2 > page + 512) continue
                    val cb = u8(wd, po)
                    val start: Int
                    val size: Int
                    if (cb != 0) { start = po + 1; size = 2 * cb - 1 } else { start = po + 2; size = 2 * u8(wd, po + 1) }
                    if (size < 2 || start + size > page + 512) continue
                    addFcRange(a, b, wd.copyOfRange(start + 2, start + size), u16(wd, start))
                } else {
                    val off = u8(wd, page + 4 * (count + 1) + j)
                    if (off == 0) continue
                    val co = page + off * 2
                    if (co + 1 > page + 512) continue
                    val cb = u8(wd, co)
                    if (co + 1 + cb > page + 512) continue
                    addFcRange(a, b, wd.copyOfRange(co + 1, co + 1 + cb), 0)
                }
            }
        }
        out.sortBy { it.start }
        return out
    }

    /** [ranges] içinde [cp]'yi kapsayan aralık (yoksa null). */
    private fun rangeAt(ranges: List<Range>, cp: Int): Range? {
        var lo = 0
        var hi = ranges.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ranges[mid].start <= cp) lo = mid + 1 else hi = mid
        }
        val r = ranges.getOrNull(lo - 1) ?: return null
        return if (cp < r.end) r else null
    }

    private fun nextStart(ranges: List<Range>, cp: Int): Int? {
        var lo = 0
        var hi = ranges.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ranges[mid].start <= cp) lo = mid + 1 else hi = mid
        }
        return ranges.getOrNull(lo)?.start
    }

    // --- Listeler ------------------------------------------------------------------------------------

    private class ListLevel(val nfc: Int, val start: Int, val text: String, val left: Float, val firstLine: Float)

    private fun parseLists(table: ByteArray, fib: Fib): Pair<Map<Long, List<ListLevel>>, List<Long>> {
        val lists = HashMap<Long, List<ListLevel>>()
        fib.pair(Fib.PLF_LST)?.let { (fc, _) ->
            if (fc + 2 > table.size) return@let
            val cLst = u16(table, fc)
            var pos = fc + 2
            val heads = ArrayList<Pair<Long, Boolean>>()
            for (i in 0 until cLst) {
                if (pos + 28 > table.size) return@let
                heads.add(u32(table, pos) to ((u8(table, pos + 26) and 1) != 0))
                pos += 28
            }
            for ((lsid, simple) in heads) {
                val levels = ArrayList<ListLevel>()
                for (l in 0 until if (simple) 1 else 9) {
                    if (pos + 28 > table.size) break
                    val start = i32(table, pos)
                    val nfc = u8(table, pos + 4)
                    val cbChpx = u8(table, pos + 24)
                    val cbPapx = u8(table, pos + 25)
                    var left = 0f
                    var first = 0f
                    val papxStart = pos + 28
                    if (papxStart + cbPapx + cbChpx + 2 > table.size) break
                    forEachSprm(table.copyOfRange(papxStart, papxStart + cbPapx)) { sprm, off, _ ->
                        val o = papxStart + off
                        when (sprm) {
                            0x840F, 0x845E -> left = i16(table, o) / 20f
                            0x8411, 0x8460 -> first = i16(table, o) / 20f
                        }
                    }
                    var q = papxStart + cbPapx + cbChpx
                    val cch = u16(table, q)
                    if (q + 2 + cch * 2 > table.size) break
                    val text = String(table, q + 2, cch * 2, Charsets.UTF_16LE)
                    q += 2 + cch * 2
                    levels.add(ListLevel(nfc, start, text, left, first))
                    pos = q
                }
                lists[lsid] = levels
            }
        }
        val lfo = ArrayList<Long>()
        fib.pair(Fib.PLF_LFO)?.let { (fc, _) ->
            if (fc + 4 > table.size) return@let
            val count = i32(table, fc).coerceIn(0, 10_000)
            for (i in 0 until count) {
                val o = fc + 4 + 16 * i
                if (o + 4 > table.size) break
                lfo.add(u32(table, o))
            }
        }
        return lists to lfo
    }

    private fun parseFonts(table: ByteArray, fib: Fib): List<String> {
        val (fc, lcb) = fib.pair(Fib.STTBF_FFN) ?: return emptyList()
        if (fc + 4 > table.size) return emptyList()
        val end = minOf(fc + lcb, table.size)
        val count = u16(table, fc)
        var pos = fc + 4
        val names = ArrayList<String>()
        for (i in 0 until count) {
            if (pos >= end) break
            val size = u8(table, pos) + 1
            if (pos + size > end) break
            val nameStart = pos + 40
            val sb = StringBuilder()
            var q = nameStart
            while (q + 2 <= pos + size) {
                val ch = u16(table, q)
                if (ch == 0) break
                sb.append(ch.toChar())
                q += 2
            }
            names.add(sb.toString())
            pos += size
        }
        return names
    }

    // --- Belgeyi kurma -------------------------------------------------------------------------------

    private class RawCell(val blocks: List<WordBlock>, val flags: Int, val shade: Int = 0)

    private class RawRow(val cells: List<RawCell>, val bounds: IntArray?)

    private class DocField {
        val code = StringBuilder()
        var separated = false
        var link: String? = null
    }

    private class Builder(
        private val data: ByteArray?,
        private val wd: ByteArray,
        private val table: ByteArray,
        fib: Fib,
        pieces: List<Piece>,
        private val text: String,
    ) {
        private val styles = parseStyles(table, fib)
        private val chpRanges = readRanges(wd, table, fib.pair(Fib.PLCF_BTE_CHPX), pieces, paragraph = false)
        private val papRanges = readRanges(wd, table, fib.pair(Fib.PLCF_BTE_PAPX), pieces, paragraph = true)
        private val listInfo = parseLists(table, fib)
        private val fonts = parseFonts(table, fib)

        private val blocks = ArrayList<WordBlock>()
        private val images = LinkedHashMap<String, ByteArray>()
        private var imageBytes = 0L
        private var chars = 0
        private var truncated = false
        private val fields = ArrayList<DocField>()
        private val counters = HashMap<Long, IntArray>()

        // tablo durumu
        private var rows = ArrayList<RawRow>()
        private var cells = ArrayList<RawCell>()
        private var cellBlocks = ArrayList<WordBlock>()
        private var tableOpen = false
        private var tableBorders = false

        private val defaultChp = HashMap<Int, Chp>()
        private val papCache = HashMap<Int, Pap>()   // PAPX aralığı olmayan paragraflar için varsayılan

        // --- stil çözümleme ---

        private fun chpChain(istd: Int): List<ByteArray> {
            val out = ArrayList<ByteArray>()
            var cur = istd
            var guard = 0
            while (cur in styles.indices && guard++ < 30) {
                val s = styles[cur] ?: break
                out.add(s.chp)
                if (s.basedOn == 0x0FFF || s.basedOn == cur) break
                cur = s.basedOn
            }
            out.reverse()
            return out
        }

        private fun papChain(istd: Int): List<ByteArray> {
            val out = ArrayList<ByteArray>()
            var cur = istd
            var guard = 0
            while (cur in styles.indices && guard++ < 30) {
                val s = styles[cur] ?: break
                out.add(s.pap)
                if (s.basedOn == 0x0FFF || s.basedOn == cur) break
                cur = s.basedOn
            }
            out.reverse()
            return out
        }

        private fun applyChp(c: Chp, g: ByteArray) {
            forEachSprm(g) { sprm, o, _ ->
                val op = u8(g, o)
                when (sprm) {
                    0x0835 -> c.bold = toggle(c.bold, op)
                    0x0836 -> c.italic = toggle(c.italic, op)
                    0x0837 -> c.strike = toggle(c.strike, op)
                    0x083A -> c.smallCaps = toggle(c.smallCaps, op)
                    0x083B -> c.caps = toggle(c.caps, op)
                    0x083C -> c.vanish = toggle(c.vanish, op)
                    0x2A3E -> c.underline = op != 0
                    0x2A42 -> c.color = if (op in 1..16) ico[op] else null
                    0x6870 -> c.color = if (u8(g, o + 3) == 0xFF) null else 0xFF000000.toInt() or (u8(g, o) shl 16) or (u8(g, o + 1) shl 8) or u8(g, o + 2)
                    0x4A43 -> c.hps = u16(g, o)
                    0x2A48 -> c.script = when (op) { 1 -> 1; 2 -> -1; else -> 0 }
                    0x4845 -> c.position = i16(g, o)
                    0x2A0C -> c.highlight = if (op in 1..16) ico[op] else null
                    0x0855 -> c.fSpec = op == 1
                    0x6A03 -> c.picLoc = i32(g, o)
                    0x4A4F -> c.ftc = u16(g, o)
                }
            }
        }

        private fun resolveChp(paraIstd: Int, direct: ByteArray?): Chp {
            val c = Chp()
            chpChain(paraIstd).forEach { applyChp(c, it) }
            if (direct != null) {
                var charStyle = -1
                forEachSprm(direct) { sprm, o, _ -> if (sprm == 0x4A30) charStyle = u16(direct, o) }
                if (charStyle >= 0) chpChain(charStyle).forEach { applyChp(c, it) }
                applyChp(c, direct)
            }
            return c
        }

        private fun chpFor(range: Range?, paraIstd: Int): Chp =
            if (range != null) range.chpCache.getOrPut(paraIstd) { resolveChp(paraIstd, range.grpprl) }
            else defaultChp.getOrPut(paraIstd) { resolveChp(paraIstd, null) }

        private fun applyPap(p: Pap, g: ByteArray) {
            forEachSprm(g) { sprm, o, len ->
                val op = u8(g, o)
                when (sprm) {
                    0x4600 -> p.istd = u16(g, o)
                    0x2403, 0x2461 -> p.jc = op
                    0x2416 -> p.inTable = op != 0
                    0x2417 -> p.ttp = op != 0
                    0x6649 -> p.itap = i32(g, o)
                    0x244B -> p.innerCell = op != 0
                    0x244C -> p.innerTtp = op != 0
                    0x840F, 0x845E -> p.left = i16(g, o) / 20f
                    0x8411, 0x8460 -> p.firstLine = i16(g, o) / 20f
                    0xA413 -> p.before = u16(g, o) / 20f
                    0xA414 -> p.after = u16(g, o) / 20f
                    0x6412 -> {
                        val line = i16(g, o)
                        val mult = u16(g, o + 2)
                        if (mult == 1) { p.lineMult = (line / 240f).coerceIn(0.8f, 3f); p.lineExactPt = null }
                        else if (line != 0) { p.lineMult = null; p.lineExactPt = Math.abs(line) / 20f }
                    }
                    0x260A -> p.ilvl = op.coerceIn(0, 8)
                    0x460B -> p.ilfo = u16(g, o)
                    0x2407 -> p.pageBreakBefore = op != 0
                    0xD608 -> parseTableDef(p, g, o, len)
                    0xD609 -> {
                        // hücre gölgeleri: Shd80 dizisi (her biri 2 bayt: önplan rengi, arka plan rengi, desen)
                        val count = op / 2
                        p.cellShades = IntArray(count) { k ->
                            val v = u16(g, o + 1 + 2 * k)
                            val fore = v and 0x1F
                            val back = (v shr 5) and 0x1F
                            val pattern = v shr 10
                            val idx = if (pattern == 1) fore else back
                            if (idx in 1..16) ico[idx] else 0
                        }
                    }
                    0x6646 -> {
                        // büyük PAPX: sprm listesi Data akışındadır
                        val d = data
                        val loc = i32(g, o)
                        if (d != null && loc >= 0 && loc + 2 <= d.size) {
                            val cb = u16(d, loc)
                            if (loc + 2 + cb <= d.size) applyPap(p, d.copyOfRange(loc + 2, loc + 2 + cb))
                        }
                    }
                }
            }
        }

        private fun parseTableDef(p: Pap, g: ByteArray, o: Int, len: Int) {
            // işlenen: cb(2) itcMac(1) rgdxaCenter[itcMac+1](2 bayt) rgtc80[itcMac](20 bayt)
            if (len < 5) return
            val itc = u8(g, o + 2)
            if (itc <= 0 || itc > 63) return
            val centersAt = o + 3
            if (centersAt + (itc + 1) * 2 > g.size) return
            p.centers = IntArray(itc + 1) { i16(g, centersAt + 2 * it) }
            val tcAt = centersAt + (itc + 1) * 2
            if (tcAt + itc * 20 <= g.size) {
                val flags = IntArray(itc)
                for (k in 0 until itc) {
                    val t = tcAt + k * 20
                    flags[k] = u16(g, t)
                    for (side in 0 until 4) {
                        val type = u8(g, t + 4 + side * 4 + 1)
                        if (type != 0 && type != 0xFF) p.anyBorder = true
                    }
                }
                p.cellFlags = flags
            }
        }

        private fun papAt(cp: Int): Pap {
            val range = rangeAt(papRanges, cp)
            if (range == null) return papCache.getOrPut(0) { Pap() }
            range.papCache?.let { return it }
            val p = Pap()
            papChain(range.istd).forEach { applyPap(p, it) }
            p.istd = range.istd
            applyPap(p, range.grpprl)
            range.papCache = p
            return p
        }

        // --- liste imi ---

        private fun labelFor(p: Pap): Pair<String?, ListLevel?> {
            if (p.ilfo <= 0 || p.ilfo > listInfo.second.size) return null to null
            val lsid = listInfo.second[p.ilfo - 1]
            val levels = listInfo.first[lsid] ?: return null to null
            val ilvl = minOf(p.ilvl, levels.size - 1)
            if (ilvl < 0) return null to null
            val level = levels[ilvl]
            val c = counters.getOrPut(lsid) { IntArray(9) { -1 } }
            c[ilvl] = if (c[ilvl] < 0) level.start else c[ilvl] + 1
            for (l in ilvl + 1 until 9) c[l] = -1
            if (level.nfc == 0xFF) return null to level
            if (level.nfc == 0x17) return ListText.bulletGlyph(level.text) to level
            val sb = StringBuilder()
            for (ch in level.text) {
                if (ch.code in 0..8) {
                    val li = ch.code
                    val lv = levels.getOrNull(li)
                    val value = if (c[li] < 0) (lv?.start ?: 1) else c[li]
                    sb.append(ListText.format(value, nfcName(lv?.nfc ?: 0)))
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString().ifEmpty { null } to level
        }

        private fun nfcName(nfc: Int): String = when (nfc) {
            1 -> "upperRoman"
            2 -> "lowerRoman"
            3 -> "upperLetter"
            4 -> "lowerLetter"
            22 -> "decimalZero"
            else -> "decimal"
        }

        // --- ana döngü ---

        fun build(): WordDoc {
            var cp = 0
            while (cp < text.length && !truncated) {
                var e = cp
                while (e < text.length && text[e] != '\r' && text[e] != '\u0007') e++
                val endChar = if (e < text.length) text[e] else '\r'
                val pap = papAt(cp)

                if (pap.inTable && pap.itap <= 1) {
                    if (endChar == '\u0007' && (pap.ttp || pap.innerTtp) ) {
                        closeRow(pap)
                    } else {
                        val para = paragraph(cp, e, pap)
                        cellBlocks.addAll(para)
                        tableOpen = true
                        if (endChar == '\u0007') {
                            cells.add(RawCell(cellBlocks, 0))
                            cellBlocks = ArrayList()
                        }
                    }
                } else if (pap.inTable) {
                    cellBlocks.addAll(paragraph(cp, e, pap)) // iç içe tablo: düz paragraf olarak
                } else {
                    flushTable()
                    blocks.addAll(paragraph(cp, e, pap))
                }
                cp = e + 1
            }
            flushTable()
            while (blocks.lastOrNull() === WordPageBreak) blocks.removeAt(blocks.size - 1)
            return WordDoc(blocks, images, truncated, "doc", maxOf(pictureCount, images.size))
        }

        private fun closeRow(pap: Pap) {
            // satır sonu: bekleyen hücre içeriği kalmışsa son hücre sayılır
            if (cellBlocks.isNotEmpty()) { cells.add(RawCell(cellBlocks, 0)); cellBlocks = ArrayList() }
            val flags = pap.cellFlags
            val shades = pap.cellShades
            val cs = cells.mapIndexed { i, c -> RawCell(c.blocks, flags?.getOrNull(i) ?: 0, shades?.getOrNull(i) ?: 0) }
            rows.add(RawRow(cs, pap.centers))
            if (pap.anyBorder) tableBorders = true
            cells = ArrayList()
            tableOpen = true
        }

        private fun flushTable() {
            if (cells.isNotEmpty() || cellBlocks.isNotEmpty()) closeRow(Pap())
            if (rows.isEmpty()) { tableOpen = false; return }
            blocks.add(buildTable(rows, tableBorders))
            rows = ArrayList()
            cells = ArrayList()
            cellBlocks = ArrayList()
            tableOpen = false
            tableBorders = false
        }

        private fun buildTable(raw: List<RawRow>, bordered: Boolean): WordTable {
            // Tüm satırların hücre sınırlarının birleşimi ızgarayı verir; böylece düzensiz tablolar da doğru çizilir.
            val grid = java.util.TreeSet<Int>()
            fun snap(v: Int) = Math.round(v / 20f) * 20
            for (r in raw) r.bounds?.forEach { grid.add(snap(it)) }
            val xs = grid.toList()
            val columns = if (xs.size >= 2) (0 until xs.size - 1).map { (xs[it + 1] - xs[it]) / 20f } else emptyList()

            val outRows = ArrayList<WordRow>()
            var maxCols = columns.size
            for (r in raw) {
                val out = ArrayList<WordCell>()
                val b = r.bounds
                var k = 0
                while (k < r.cells.size) {
                    val cell = r.cells[k]
                    var last = k
                    // yatay birleşik hücreler (fFirstMerged, ardından fMerged'ler)
                    if (cell.flags and 1 != 0) {
                        while (last + 1 < r.cells.size && r.cells[last + 1].flags and 2 != 0) last++
                    }
                    val continues = cell.flags and 0x20 != 0 && cell.flags and 0x40 == 0
                    val span = if (b != null && last + 1 < b.size && xs.size >= 2) {
                        val a = xs.indexOf(snap(b[k]))
                        val z = xs.indexOf(snap(b[last + 1]))
                        if (a >= 0 && z > a) z - a else last - k + 1
                    } else last - k + 1
                    out.add(WordCell(if (continues) emptyList() else cell.blocks, span.coerceAtLeast(1), cell.shade.takeIf { it != 0 }, false, continues))
                    k = last + 1
                }
                maxCols = maxOf(maxCols, out.sumOf { it.span })
                outRows.add(WordRow(out))
            }
            val cols = ArrayList(columns)
            while (cols.size < maxCols) cols.add(80f)
            return WordTable(cols, outRows, if (bordered) 0xFF000000.toInt() else 0xFFBDBDBD.toInt())
        }

        // --- paragraf ---

        private fun styleOf(istd: Int): StyleInfo? = styles.getOrNull(istd)

        private fun paragraph(start: Int, end: Int, pap: Pap): List<WordBlock> {
            val runs = ArrayList<WordRun>()
            val extras = ArrayList<WordBlock>()
            buildRuns(start, end, pap, runs, extras)

            val style = styleOf(pap.istd)
            val sti = style?.sti ?: pap.istd
            val name = style?.name?.lowercase().orEmpty()
            val headingFromName = Regex("^(?:heading|başlık|baslik)\\s*(\\d)").find(name)?.groupValues?.get(1)?.toIntOrNull()
            val heading = when {
                sti in 1..9 -> sti
                headingFromName != null -> headingFromName.coerceIn(1, 9)
                else -> 0
            }

            val (label, level) = labelFor(pap)
            var left = pap.left
            var first = pap.firstLine
            if (level != null && left == 0f && level.left > 0f) { left = level.left; first = level.firstLine }

            val markChp = chpFor(null, pap.istd)
            val emptyPt = markChp.hps / 2f
            val out = ArrayList<WordBlock>()
            if (pap.pageBreakBefore && !tableOpen) addBreak(out)

            val segments = ArrayList<MutableList<WordRun>>()
            var current = ArrayList<WordRun>()
            for (r in merge(runs)) {
                if (r.text == "\u000C") { segments.add(current); current = ArrayList() } else current.add(r)
            }
            segments.add(current)

            for ((i, seg) in segments.withIndex()) {
                val firstSeg = i == 0
                val lastSeg = i == segments.lastIndex
                if (seg.isEmpty() && (segments.size > 1 || extras.isNotEmpty())) {
                    // boş parça
                } else {
                    val maxSize = seg.maxOfOrNull { it.sizePt } ?: emptyPt
                    val spacing = pap.lineMult ?: pap.lineExactPt?.let { (it / (maxSize * 1.2f)).coerceIn(0.8f, 3f) } ?: 1f
                    out.add(
                        WordParagraph(
                            runs = seg,
                            align = when (pap.jc) { 1 -> WordAlign.Center; 2 -> WordAlign.Right; 3 -> WordAlign.Justify; else -> WordAlign.Left },
                            heading = heading,
                            indentPt = left,
                            firstLinePt = first,
                            beforePt = if (firstSeg) pap.before else 0f,
                            afterPt = if (lastSeg) pap.after else 0f,
                            lineSpacing = spacing,
                            label = if (firstSeg) label else null,
                            emptyPt = emptyPt,
                        ),
                    )
                }
                if (!lastSeg && !tableOpen) addBreak(out)
            }
            out.addAll(extras)
            return out
        }

        private fun addBreak(out: MutableList<WordBlock>) {
            if (blocks.isEmpty() && out.isEmpty()) return
            if (out.lastOrNull() === WordPageBreak || (out.isEmpty() && blocks.lastOrNull() === WordPageBreak)) return
            out.add(WordPageBreak)
        }

        private fun merge(runs: List<WordRun>): List<WordRun> {
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

        private fun hyperlinkOf(code: String): String? {
            val t = code.trim()
            if (!t.startsWith("HYPERLINK", ignoreCase = true) || "\\l" in t) return null
            val quoted = Regex("HYPERLINK\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
            val plain = Regex("HYPERLINK\\s+(\\S+)", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
            return safeUrl(quoted ?: plain)
        }

        private fun buildRuns(start: Int, end: Int, pap: Pap, runs: MutableList<WordRun>, extras: MutableList<WordBlock>) {
            val sb = StringBuilder()
            var runChp: Chp? = null
            var runLink: String? = null

            fun flush() {
                val c = runChp
                if (sb.isNotEmpty() && c != null) {
                    chars += sb.length
                    if (chars > DocxReader.MAX_CHARS) { truncated = true; sb.setLength(0); return }
                    val upper = c.caps || c.smallCaps
                    val raw = sb.toString()
                    var size = c.hps / 2f * (if (c.smallCaps && !c.caps) 0.85f else 1f)
                    var script = c.script
                    if (script == 0 && kotlin.math.abs(c.position) >= WordRun.MIN_RAISE_HALF_POINTS) {
                        script = if (c.position > 0) 1 else -1
                        size /= WordRun.SCRIPT_SHRINK
                    }
                    val link = runLink
                    runs.add(
                        WordRun(
                            text = if (upper) raw.uppercase(Locale.getDefault()) else raw,
                            bold = c.bold,
                            italic = c.italic,
                            underline = c.underline || link != null,
                            strike = c.strike,
                            color = c.color ?: if (link != null) 0xFF0563C1.toInt() else null,
                            highlight = c.highlight,
                            sizePt = size,
                            font = fontFamilyOf(fonts.getOrNull(c.ftc)),
                            script = script,
                            link = link,
                        ),
                    )
                }
                sb.setLength(0)
            }

            fun put(c: Char, chp: Chp, link: String?) {
                if (chp !== runChp || link != runLink) { flush(); runChp = chp; runLink = link }
                sb.append(c)
            }

            var i = start
            while (i < end && !truncated) {
                val range = rangeAt(chpRanges, i)
                val segEnd = minOf(end, range?.end ?: (nextStart(chpRanges, i) ?: end)).coerceAtLeast(i + 1)
                val chp = chpFor(range, pap.istd)
                for (j in i until segEnd) {
                    val ch = text[j]
                    when (ch) {
                        '\u0013' -> { flush(); fields.add(DocField()) }
                        '\u0014' -> { flush(); fields.lastOrNull()?.let { it.separated = true; it.link = hyperlinkOf(it.code.toString()) } }
                        '\u0015' -> { flush(); if (fields.isNotEmpty()) fields.removeAt(fields.size - 1) }
                        else -> {
                            val top = fields.lastOrNull()
                            if (top != null && !top.separated) {
                                top.code.append(ch)
                            } else if (!chp.vanish) {
                                val link = fields.lastOrNull { it.separated && it.link != null }?.link
                                when {
                                    ch == '\u0001' -> {
                                        flush()
                                        if (chp.fSpec) inlinePicture(chp.picLoc)?.let { extras.add(it) }
                                    }
                                    ch == '\t' -> put('\t', chp, link)
                                    ch == '\u000B' -> put('\n', chp, link)
                                    ch == '\u000C' -> { flush(); runs.add(WordRun("\u000C")) }
                                    ch == '\u001E' -> put('‑', chp, link)
                                    ch >= ' ' -> put(ch, chp, link)
                                }
                            }
                        }
                    }
                }
                i = segEnd
            }
            flush()
        }

        // --- görseller ---

        private var pictureCount = 0

        private fun inlinePicture(loc: Int): WordImage? {
            pictureCount++
            val d = data ?: return null
            if (loc < 0 || loc + 68 > d.size) return null
            val lcb = i32(d, loc)
            val cbHeader = u16(d, loc + 4)
            if (lcb < cbHeader || cbHeader < 68) return null
            val end = minOf(d.size.toLong(), loc.toLong() + lcb).toInt()
            var from = loc + cbHeader
            if (u16(d, loc + 6) == 0x66 && from < end) from += 1 + u8(d, from) // MM_SHAPEFILE: dosya adı
            val blip = findBlip(d, from, end, 0) ?: return null
            if (imageBytes + blip.size > MAX_IMAGES_BYTES) return null
            imageBytes += blip.size
            val key = "doc-image-${images.size + 1}"
            images[key] = blip
            val dxa = i16(d, loc + 0x1C)
            val dya = i16(d, loc + 0x1E)
            val mx = u16(d, loc + 0x20)
            val my = u16(d, loc + 0x22)
            val w = if (dxa > 0 && mx > 0) dxa * mx / 1000f / 20f else 0f
            val h = if (dya > 0 && my > 0) dya * my / 1000f / 20f else 0f
            return WordImage(key, w, h)
        }

        /** OfficeArt kayıtları arasında ilk JPEG/PNG görselin baytlarını bulur. */
        private fun findBlip(d: ByteArray, start: Int, end: Int, depth: Int): ByteArray? {
            var pos = start
            while (pos + 8 <= end) {
                val verInst = u16(d, pos)
                val type = u16(d, pos + 2)
                val len = i32(d, pos + 4)
                val ver = verInst and 0xF
                val inst = verInst shr 4
                val body = pos + 8
                if (len < 0) return null
                val bodyEnd = minOf(end.toLong(), body.toLong() + len).toInt()
                when {
                    type in 0xF018..0xF117 -> {
                        val twoUids = when (type) {
                            0xF01D, 0xF02A -> inst == 0x46B || inst == 0x6E3
                            0xF01E -> inst == 0x6E1
                            else -> false
                        }
                        if (type == 0xF01D || type == 0xF02A || type == 0xF01E) {
                            val dataStart = body + 16 * (if (twoUids) 2 else 1) + 1
                            if (dataStart < bodyEnd) return d.copyOfRange(dataStart, bodyEnd)
                        }
                    }
                    type == 0xF007 -> {
                        if (body + 36 <= bodyEnd) {
                            val inner = body + 36 + u8(d, body + 33)
                            if (inner + 8 <= bodyEnd && depth < 8) findBlip(d, inner, bodyEnd, depth + 1)?.let { return it }
                        }
                    }
                    ver == 0xF && depth < 8 -> findBlip(d, body, bodyEnd, depth + 1)?.let { return it }
                }
                if (bodyEnd <= pos) return null
                pos = bodyEnd
            }
            return null
        }
    }

    private const val MAX_IMAGES_BYTES = 24L * 1024 * 1024
}
