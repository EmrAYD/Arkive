package com.eayd.arkive.utils

/** Arama ekranının sunucudan bağımsız kuralları. */
object SearchText {

    /** Sunucu bundan kısa aramalara sonuç döndürmez. */
    const val MIN_LETTERS = 2

    /** Metin parçasında ilk eşleşmeden önce bırakılan en çok harf; eşleşme ilk satırda görünsün. */
    const val SNIPPET_LEAD = 24

    private val spaces = Regex("\\s+")

    /** [query] aranabilir mi: kelimelerin (tekrarsız) toplamı en az [MIN_LETTERS] harf. */
    fun canSearch(query: String): Boolean =
        query.trim().split(spaces)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sumOf { it.codePointCount(0, it.length) } >= MIN_LETTERS

    /**
     * Sunucunun verdiği [başlangıç, bitiş) aralıklarını [text] içindeki konumlara çevirir.
     * Sunucu harf sayar; emoji gibi iki birimlik harflerde vurgu kaymasın diye çevrilir. Geçersiz aralıklar atlanır.
     */
    fun highlights(text: String, ranges: List<List<Int>>): List<IntRange> {
        val starts = IntArray(text.codePointCount(0, text.length) + 1)
        var index = 0
        var letter = 0
        while (index < text.length) {
            starts[letter++] = index
            index += Character.charCount(text.codePointAt(index))
        }
        starts[letter] = text.length
        return ranges.mapNotNull { range ->
            val from = range.getOrNull(0)?.coerceIn(0, letter) ?: return@mapNotNull null
            val to = range.getOrNull(1)?.coerceIn(0, letter) ?: return@mapNotNull null
            if (from < to) starts[from] until starts[to] else null
        }
    }

    /** Parçanın başını ilk eşleşmeden en çok [lead] harf önceye kadar kırpar; kelime ortasından kesmez. */
    fun trimLead(snippet: String, marks: List<IntRange>, lead: Int = SNIPPET_LEAD): Pair<String, List<IntRange>> {
        val first = marks.minOfOrNull { it.first } ?: return snippet to marks
        if (first <= lead + 1) return snippet to marks
        var cut = first - lead
        val space = snippet.indexOf(' ', cut)
        if (space in cut until first) cut = space + 1
        if (snippet[cut].isLowSurrogate()) cut++
        val shift = 1 - cut
        return "…" + snippet.substring(cut) to marks.map { (it.first + shift)..(it.last + shift) }
    }

    /** Sonucun bulunduğu yer: "Ana Sayfa / Belgeler / 2026". */
    fun place(root: String, path: List<String>): String = (listOf(root) + path).joinToString(" / ")
}
