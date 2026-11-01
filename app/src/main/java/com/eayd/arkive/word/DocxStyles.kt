package com.eayd.arkive.word

/** word/styles.xml içindeki bir stil. [conditional]: tablo stillerinde "firstRow", "band1Horz"… koşullu biçimleri. */
internal class StyleDef(
    val id: String,
    val type: String,
    val name: String,
    val basedOn: String?,
    val pPr: XNode?,
    val rPr: XNode?,
    val tblPr: XNode?,
    val tcPr: XNode?,
    val conditional: Map<String, XNode>,
)

internal class Styles(
    val defaultsP: XNode?,
    val defaultsR: XNode?,
    private val byId: Map<String, StyleDef>,
    val defaultParagraph: String?,
    val defaultTable: String?,
) {
    private val cache = HashMap<String, List<StyleDef>>()

    val isEmpty: Boolean get() = byId.isEmpty()

    /** [id] stilinin dayandığı stillerle birlikte zinciri; en temel stil başta, [id] sonda. Döngüye karşı korumalı. */
    fun chain(id: String?): List<StyleDef> {
        if (id == null) return emptyList()
        return cache.getOrPut(id) {
            val out = ArrayList<StyleDef>()
            val seen = HashSet<String>()
            var cur: String? = id
            while (cur != null && seen.add(cur)) {
                val def = byId[cur] ?: break
                out.add(def)
                cur = def.basedOn
            }
            out.reverse()
            out
        }
    }

    companion object {
        val EMPTY = Styles(null, null, emptyMap(), null, null)

        fun parse(root: XNode?): Styles {
            if (root == null) return EMPTY
            val defaults = root.child("w:docDefaults")
            val map = LinkedHashMap<String, StyleDef>()
            var defaultParagraph: String? = null
            var defaultTable: String? = null
            for (s in root.children("w:style")) {
                val id = s.attr("styleId") ?: continue
                val type = s.attr("type") ?: "paragraph"
                val conditional = HashMap<String, XNode>()
                for (c in s.children("w:tblStylePr")) c.attr("type")?.let { conditional[it] = c }
                map[id] = StyleDef(
                    id = id,
                    type = type,
                    name = s.child("w:name")?.attr("val").orEmpty(),
                    basedOn = s.child("w:basedOn")?.attr("val"),
                    pPr = s.child("w:pPr"),
                    rPr = s.child("w:rPr"),
                    tblPr = s.child("w:tblPr"),
                    tcPr = s.child("w:tcPr"),
                    conditional = conditional,
                )
                if (s.attr("default") == "1" || s.attr("default") == "true") {
                    if (type == "paragraph" && defaultParagraph == null) defaultParagraph = id
                    if (type == "table" && defaultTable == null) defaultTable = id
                }
            }
            return Styles(
                defaultsP = defaults?.child("w:pPrDefault")?.child("w:pPr"),
                defaultsR = defaults?.child("w:rPrDefault")?.child("w:rPr"),
                byId = map,
                defaultParagraph = defaultParagraph,
                defaultTable = defaultTable,
            )
        }
    }
}

internal class NumLevel(val start: Int, val fmt: String, val text: String, val pPr: XNode?)

internal class NumDef(val abstractId: String, val startOverrides: Map<Int, Int>, val levelOverrides: Map<Int, NumLevel>)

/** word/numbering.xml: liste tanımları. */
internal class Numbering(
    private val abstracts: Map<String, Map<Int, NumLevel>>,
    val nums: Map<String, NumDef>,
) {
    fun level(numId: String, ilvl: Int): NumLevel? {
        val def = nums[numId] ?: return null
        return def.levelOverrides[ilvl] ?: abstracts[def.abstractId]?.get(ilvl)
    }

    fun levels(numId: String): Map<Int, NumLevel> {
        val def = nums[numId] ?: return emptyMap()
        return (abstracts[def.abstractId] ?: emptyMap()) + def.levelOverrides
    }

    companion object {
        val EMPTY = Numbering(emptyMap(), emptyMap())

        private fun level(lvl: XNode): NumLevel = NumLevel(
            start = lvl.child("w:start")?.attr("val")?.toIntOrNull() ?: 1,
            fmt = lvl.child("w:numFmt")?.attr("val") ?: "decimal",
            text = lvl.child("w:lvlText")?.attr("val").orEmpty(),
            pPr = lvl.child("w:pPr"),
        )

        fun parse(root: XNode?): Numbering {
            if (root == null) return EMPTY
            val abstracts = HashMap<String, Map<Int, NumLevel>>()
            for (a in root.children("w:abstractNum")) {
                val id = a.attr("abstractNumId") ?: continue
                val levels = HashMap<Int, NumLevel>()
                for (l in a.children("w:lvl")) {
                    val ilvl = l.attr("ilvl")?.toIntOrNull() ?: continue
                    levels[ilvl] = level(l)
                }
                abstracts[id] = levels
            }
            val nums = HashMap<String, NumDef>()
            for (n in root.children("w:num")) {
                val id = n.attr("numId") ?: continue
                val abstractId = n.child("w:abstractNumId")?.attr("val") ?: continue
                val starts = HashMap<Int, Int>()
                val overrides = HashMap<Int, NumLevel>()
                for (o in n.children("w:lvlOverride")) {
                    val ilvl = o.attr("ilvl")?.toIntOrNull() ?: continue
                    o.child("w:startOverride")?.attr("val")?.toIntOrNull()?.let { starts[ilvl] = it }
                    o.child("w:lvl")?.let { overrides[ilvl] = level(it) }
                }
                nums[id] = NumDef(abstractId, starts, overrides)
            }
            return Numbering(abstracts, nums)
        }
    }
}

/** Liste imleri ve sayı biçimleri (docx ve doc ortak). */
internal object ListText {
    fun bulletGlyph(text: String): String {
        val ch = text.firstOrNull() ?: return "•"
        return when {
            ch == '' || ch == '·' || ch == '•' || ch == '•' -> "•"
            ch == '' || ch == '§' || ch == '▪' -> "▪"
            ch == 'o' || ch == 'O' -> "◦"
            ch == '' -> "➢"
            ch == '' -> "✓"
            ch == '' -> "❖"
            ch == '' -> "→"
            ch in ''..'' -> "•"
            ch.isLetterOrDigit() -> "•"
            ch == ' ' -> "•"
            else -> ch.toString()
        }
    }

    fun format(n: Int, fmt: String): String = when (fmt) {
        "decimalZero" -> if (n in 0..9) "0$n" else "$n"
        "lowerLetter" -> letters(n).lowercase()
        "upperLetter" -> letters(n)
        "lowerRoman" -> roman(n).lowercase()
        "upperRoman" -> roman(n)
        else -> "$n"
    }

    fun letters(n: Int): String {
        if (n <= 0) return "$n"
        val sb = StringBuilder()
        var x = n - 1
        do {
            sb.append('A' + (x % 26))
            x = x / 26 - 1
        } while (x >= 0)
        return sb.reverse().toString()
    }

    fun roman(n: Int): String {
        if (n <= 0 || n > 3999) return "$n"
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var x = n
        val sb = StringBuilder()
        for (i in values.indices) while (x >= values[i]) { sb.append(symbols[i]); x -= values[i] }
        return sb.toString()
    }
}

/** Renk yardımcıları (ARGB Int). */
internal object WordColors {
    private val highlightNames = mapOf(
        "yellow" to 0xFFFFFF00.toInt(), "green" to 0xFF00FF00.toInt(), "cyan" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(), "blue" to 0xFF0000FF.toInt(), "red" to 0xFFFF0000.toInt(),
        "darkBlue" to 0xFF000080.toInt(), "darkCyan" to 0xFF008080.toInt(), "darkGreen" to 0xFF008000.toInt(),
        "darkMagenta" to 0xFF800080.toInt(), "darkRed" to 0xFF800000.toInt(), "darkYellow" to 0xFF808000.toInt(),
        "darkGray" to 0xFF808080.toInt(), "lightGray" to 0xFFC0C0C0.toInt(), "black" to 0xFF000000.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
    )

    private val themeAliases = mapOf(
        "text1" to "dk1", "background1" to "lt1", "text2" to "dk2", "background2" to "lt2",
        "dark1" to "dk1", "light1" to "lt1", "dark2" to "dk2", "light2" to "lt2",
        "hyperlink" to "hlink", "followedHyperlink" to "folHlink",
    )

    fun highlight(name: String?): Int? = if (name == null) null else highlightNames[name]

    fun hex(value: String?): Int? {
        if (value == null || value.length != 6) return null
        val rgb = value.toIntOrNull(16) ?: return null
        return 0xFF000000.toInt() or rgb
    }

    /** theme1.xml'deki renk şeması: ad (dk1, lt1, accent1…) → renk. */
    fun parseTheme(root: XNode?): Map<String, Int> {
        fun find(n: XNode): XNode? {
            if (n.name == "a:clrScheme") return n
            for (k in n.kids) find(k)?.let { return it }
            return null
        }
        val scheme = root?.let { find(it) } ?: return emptyMap()
        val out = HashMap<String, Int>()
        for (c in scheme.kids) {
            val inner = c.kids.firstOrNull() ?: continue
            val value = if (inner.name == "a:sysClr") inner.attr("lastClr") else inner.attr("val")
            hex(value)?.let { out[c.name.removePrefix("a:")] = it }
        }
        return out
    }

    /** theme1.xml'deki yazı tipleri: "major" / "minor" → latin yazı tipi adı. */
    fun parseThemeFonts(root: XNode?): Map<String, String> {
        fun find(n: XNode): XNode? {
            if (n.name == "a:fontScheme") return n
            for (k in n.kids) find(k)?.let { return it }
            return null
        }
        val scheme = root?.let { find(it) } ?: return emptyMap()
        val out = HashMap<String, String>()
        scheme.child("a:majorFont")?.child("a:latin")?.attr("typeface")?.let { out["major"] = it }
        scheme.child("a:minorFont")?.child("a:latin")?.attr("typeface")?.let { out["minor"] = it }
        return out
    }

    /** [themeColor] (varsa) temadan çözülür, [tint]/[shade] (16'lık 00..FF) uygulanır; yoksa [value] altı haneli renktir. */
    fun resolve(theme: Map<String, Int>, value: String?, themeColor: String?, tint: String?, shade: String?): Int? {
        if (themeColor != null) {
            val key = themeAliases[themeColor] ?: themeColor
            val base = theme[key]
            if (base != null) return adjust(base, tint, shade)
        }
        if (value == null || value == "auto") return null
        return hex(value)
    }

    private fun adjust(color: Int, tint: String?, shade: String?): Int {
        var r = (color shr 16) and 0xFF
        var g = (color shr 8) and 0xFF
        var b = color and 0xFF
        val t = tint?.toIntOrNull(16)
        val s = shade?.toIntOrNull(16)
        if (t != null) {
            val f = t / 255f
            r = (255 - (255 - r) * f).toInt().coerceIn(0, 255)
            g = (255 - (255 - g) * f).toInt().coerceIn(0, 255)
            b = (255 - (255 - b) * f).toInt().coerceIn(0, 255)
        }
        if (s != null) {
            val f = s / 255f
            r = (r * f).toInt().coerceIn(0, 255)
            g = (g * f).toInt().coerceIn(0, 255)
            b = (b * f).toInt().coerceIn(0, 255)
        }
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
}

/** Yazı tipi adından kaba aile. */
internal fun fontFamilyOf(name: String?): WordFont {
    val n = name?.lowercase() ?: return WordFont.Sans
    if (listOf("courier", "consolas", "mono", "menlo", "lucida console", "cascadia", "fira code", "source code", "typewriter").any { it in n }) {
        return WordFont.Mono
    }
    if ("sans" in n) return WordFont.Sans
    if (listOf("times", "georgia", "cambria", "garamond", "palatino", "book antiqua", "bookman", "century", "minion", "baskerville", "constantia", "serif").any { it in n }) {
        return WordFont.Serif
    }
    return WordFont.Sans
}

/** Yalnızca güvenli adresler tıklanabilir olur. */
internal fun safeUrl(url: String?): String? {
    val u = url?.trim() ?: return null
    val l = u.lowercase()
    return if (l.startsWith("http://") || l.startsWith("https://") || l.startsWith("mailto:")) u else null
}
