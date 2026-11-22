package com.eayd.arkive.word

/** Paragraf hizası. */

enum class WordAlign { Left, Center, Right, Justify }

enum class WordFont { Sans, Serif, Mono }

/** Aynı biçimdeki metin parçası. [script] 1 ise üst, -1 ise alt simge. */
data class WordRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val color: Int? = null,
    val highlight: Int? = null,
    val sizePt: Float = DEFAULT_SIZE_PT,
    val font: WordFont = WordFont.Sans,
    val script: Int = 0,
    val link: String? = null,
) {
    companion object {
        const val DEFAULT_SIZE_PT = 11f

        /** Üst ve alt simge metninin küçültme oranı. */
        const val SCRIPT_SHRINK = 0.7f

        /** Satır bundan fazla yükseltilmişse üst ya da alt simge sayılır. */
        const val MIN_RAISE_HALF_POINTS = 2
    }
}

sealed interface WordBlock

/** Bir paragraf. Ölçüler punto cinsindendir; [label] liste imidir. */
data class WordParagraph(
    val runs: List<WordRun>,
    val align: WordAlign = WordAlign.Left,
    val heading: Int = 0,
    val indentPt: Float = 0f,
    val firstLinePt: Float = 0f,
    val beforePt: Float = 0f,
    val afterPt: Float = 0f,
    val lineSpacing: Float = 1f,
    val label: String? = null,
    val background: Int? = null,
    val borderTop: Int? = null,
    val borderBottom: Int? = null,
    val emptyPt: Float = WordRun.DEFAULT_SIZE_PT,
) : WordBlock

/** Görsel. [key] [WordDoc.images] içindeki anahtar. */
data class WordImage(
    val key: String,
    val widthPt: Float,
    val heightPt: Float,
    val align: WordAlign = WordAlign.Left,
) : WordBlock

/** Tablo. [borderColor] null ise çizgisiz. */
class WordTable(
    val columnsPt: List<Float>,
    val rows: List<WordRow>,
    val borderColor: Int?,
) : WordBlock

class WordRow(val cells: List<WordCell>)

/** Tablo hücresi. [continuesAbove] üstteki hücreyle birleşik olduğunu söyler. */
class WordCell(
    val blocks: List<WordBlock>,
    val span: Int = 1,
    val background: Int? = null,
    val bordered: Boolean = false,
    val continuesAbove: Boolean = false,
)

/** Sayfa sonu. */
data object WordPageBreak : WordBlock

/** Okunmuş Word belgesi; .docx ve .doc aynı modele çevrilir. [truncated] belgenin sonunun gösterilmediğini söyler. */
class WordDoc(
    val blocks: List<WordBlock>,
    val images: Map<String, ByteArray>,
    val truncated: Boolean,
    val format: String,
    val imageCount: Int = images.size,
) {
    /** Belgedeki tüm düz metin. */
    fun plainText(): String {
        val out = StringBuilder()
        fun walk(list: List<WordBlock>) {
            for (b in list) when (b) {
                is WordParagraph -> { b.runs.forEach { out.append(it.text) }; out.append('\n') }
                is WordTable -> b.rows.forEach { row ->
                    row.cells.forEach { c -> walk(c.blocks); out.append('\t') }
                }
                else -> Unit
            }
        }
        walk(blocks)
        return out.toString()
    }

    fun isBlank(): Boolean = plainText().isBlank() && images.isEmpty()

    /** Özetleme için düz metin: her paragraf bir satır. */
    fun summaryText(): String {
        val lines = ArrayList<String>()
        fun walk(list: List<WordBlock>) {
            for (b in list) when (b) {
                is WordParagraph -> lines.add(b.runs.joinToString("") { it.text }.replace('\t', ' ').trim())
                is WordTable -> b.rows.forEach { row -> row.cells.forEach { c -> walk(c.blocks) } }
                else -> Unit
            }
        }
        walk(blocks)
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

/** Dosya Word belgesi olarak okunamadı. */
class WordFormatException(message: String) : Exception(message)

