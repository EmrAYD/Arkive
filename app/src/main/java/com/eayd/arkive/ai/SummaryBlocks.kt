package com.eayd.arkive.ai

enum class BlockKind { Heading, Bullet, Paragraph }

/** Özet metninin bir bloğu. [marker]: madde işareti ("•" ya da "3."); yalnızca [BlockKind.Bullet] için. */
data class SummaryBlock(val kind: BlockKind, val text: String, val marker: String = "")

/** Paragraf içi bir parça; [bold] kalın yazılır. */
data class TextRun(val text: String, val bold: Boolean)

/**
 * Özet metnini PDF için bloklara ayırır. Sunucudaki `parse_blocks` ile aynı kuralları uygular (backend/app/summary_pdf.py).
 * Telefonun ürettiği biçimi ("Özet: ...", "Önemli noktalar:", "- madde") ve Markdown benzeri metni (# başlık, **kalın
 * başlık**, 1. madde) tanır. Android sınıfı kullanmaz; test edilebilir.
 */
object SummaryBlocks {

    private val headingHash = Regex("""^#{1,6}\s*(.+?)\s*#*$""")
    private val headingBold = Regex("""^\*\*(.+?)\*\*:?$""")
    private val summaryLabel = Regex("""^(özet)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val bullet = Regex("""^[-*•]\s+(.+)$""")
    private val numbered = Regex("""^(\d{1,3})[.)]\s+(.+)$""")
    private val boldRuns = Regex("""\*\*(.+?)\*\*|__(.+?)__""")

    fun parse(summary: String): List<SummaryBlock> {
        val blocks = mutableListOf<SummaryBlock>()
        val paragraph = mutableListOf<String>()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += SummaryBlock(BlockKind.Paragraph, paragraph.joinToString(" "))
                paragraph.clear()
            }
        }

        for (raw in summary.replace("\r\n", "\n").split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) {
                flush()
                continue
            }
            val hash = headingHash.matchEntire(line)
            val boldHeading = headingBold.matchEntire(line)
            val label = summaryLabel.matchEntire(line)
            val dot = bullet.matchEntire(line)
            val number = numbered.matchEntire(line)
            when {
                hash != null -> {
                    flush()
                    blocks += SummaryBlock(BlockKind.Heading, hash.groupValues[1])
                }
                boldHeading != null -> {
                    flush()
                    blocks += SummaryBlock(BlockKind.Heading, boldHeading.groupValues[1].trimEnd(':'))
                }
                label != null -> {
                    flush()
                    blocks += SummaryBlock(BlockKind.Heading, "Özet")
                    paragraph += label.groupValues[2]
                }
                dot != null -> {
                    flush()
                    blocks += SummaryBlock(BlockKind.Bullet, dot.groupValues[1], "•")
                }
                number != null -> {
                    flush()
                    blocks += SummaryBlock(BlockKind.Bullet, number.groupValues[2], number.groupValues[1] + ".")
                }
                line.endsWith(":") && line.length <= 60 && '.' !in line.dropLast(1) -> {
                    flush()
                    blocks += SummaryBlock(BlockKind.Heading, line.trimEnd(':'))
                }
                else -> paragraph += line
            }
        }
        flush()
        return blocks
    }

    /** **kalın** ve __kalın__ parçalarını ayırır; kalan * işaretleri atılır. */
    fun runs(text: String): List<TextRun> {
        val out = mutableListOf<TextRun>()
        var pos = 0
        for (m in boldRuns.findAll(text)) {
            if (m.range.first > pos) out += TextRun(text.substring(pos, m.range.first), bold = false)
            out += TextRun(m.groupValues[1].ifEmpty { m.groupValues[2] }, bold = true)
            pos = m.range.last + 1
        }
        if (pos < text.length) out += TextRun(text.substring(pos), bold = false)
        return out.map { it.copy(text = it.text.replace("*", "")) }.filter { it.text.isNotEmpty() }
    }
}
