package com.eayd.arkive.ai

import com.eayd.arkive.i18n.AppLanguage
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** Özetin seslendirilmesi için metin kuralları. Android'e bağlı değil, testlerle denenir. */
object SpeechText {

    /** Seçilebilen okuma hızları. */
    val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    /** Bundan uzun cümleler virgülden ya da boşluktan bölünür; ses motoru çok uzun metni tek seferde okumaz. */
    const val MAX_PIECE = 1_000

    private val ABBREVIATIONS = setOf(
        "vb", "vs", "örn", "bkz", "dr", "prof", "doç", "av", "sn", "no", "s", "st", "mr", "mrs", "ms", "e.g", "i.e",
        "etc", "fig", "vol", "yy", "yrd", "öğr", "gör", "jr", "sr",
    )

    private const val ENDS = ".!?…"
    private val TURKISH_LETTERS = Regex("[ğĞışŞİ]")
    private val TURKISH_WORDS = setOf("bu", "ve", "bir", "ile", "için", "olarak", "belge", "da", "de", "içeriyor")
    private val ENGLISH_WORDS = setOf("the", "and", "of", "to", "is", "this", "document", "with", "for", "contains")

    /**
     * Metni okunacak parçalara (çoğunlukla cümlelere) böler. Her aralık metindeki yeridir: [başlangıç, bitiş].
     *
     * Kısaltmalardan ("Prof. Dr.") ve ondalık sayılardan ("3.5") bölmez; satır sonları her zaman yeni parça başlatır.
     */
    fun sentences(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val lineBreak = c == '\n'
            var end = -1
            if (lineBreak) {
                end = i
            } else if (c in ENDS) {
                var j = i
                while (j + 1 < text.length && (text[j + 1] in ENDS || text[j + 1] in "\"'”’)")) j++
                val atEnd = j + 1 >= text.length
                if (atEnd || (text[j + 1].isWhitespace() && startsSentence(text, j + 1) && !isAbbreviation(text, start, i))) {
                    end = j + 1
                    i = j
                }
            }
            if (end >= 0) {
                addPiece(text, start, end, out)
                start = if (lineBreak) i + 1 else end
            }
            i++
        }
        addPiece(text, start, text.length, out)
        return out
    }

    /** Metnin dilini tahmin eder; ipucu yoksa [fallback]. Özetler yalnızca Türkçe ya da İngilizce yazılır. */
    fun languageOf(text: String, fallback: AppLanguage): AppLanguage {
        val words = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val turkish = TURKISH_LETTERS.findAll(text).count() * 2 + words.count { it in TURKISH_WORDS }
        val english = words.count { it in ENGLISH_WORDS }
        return when {
            turkish > english -> AppLanguage.Turkish
            english > turkish -> AppLanguage.English
            else -> fallback
        }
    }

    /** Sıradaki okuma hızı; sonuncudan sonra başa döner. */
    fun nextSpeed(current: Float): Float {
        val index = SPEEDS.indices.minByOrNull { abs(SPEEDS[it] - current) } ?: 0
        return SPEEDS[(index + 1) % SPEEDS.size]
    }

    /** Hızın yazısı: Türkçede "1,25×", İngilizcede "1.25×". */
    fun speedText(speed: Float, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return format.format(speed.toDouble()) + "×"
    }

    private fun startsSentence(text: String, from: Int): Boolean {
        var k = from
        while (k < text.length && text[k].isWhitespace()) k++
        if (k >= text.length) return true
        val c = text[k]
        return c.isUpperCase() || c.isDigit() || c in "\"'“‘(-–—"
    }

    private fun isAbbreviation(text: String, sentenceStart: Int, dot: Int): Boolean {
        if (text[dot] != '.') return false
        var k = dot - 1
        while (k >= sentenceStart && (text[k].isLetter() || text[k] == '.')) k--
        val word = text.substring(k + 1, dot).lowercase(Locale.ROOT)
        return word.length == 1 || word in ABBREVIATIONS
    }

    private fun addPiece(text: String, from: Int, to: Int, out: MutableList<IntRange>) {
        var a = from
        var b = to
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        while (b - a > MAX_PIECE) {
            val cut = splitPoint(text, a, a + MAX_PIECE)
            out += a until cut
            a = cut
            while (a < b && text[a].isWhitespace()) a++
        }
        if (b > a) out += a until b
    }

    private fun splitPoint(text: String, from: Int, limit: Int): Int {
        for (marks in listOf(";:", ",", " ")) {
            for (k in limit - 1 downTo from + MAX_PIECE / 3) {
                if (text[k] in marks) return k + 1
            }
        }
        return limit
    }
}
