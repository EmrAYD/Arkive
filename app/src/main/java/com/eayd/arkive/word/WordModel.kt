package com.eayd.arkive.word

/**
 * Word belgesinin ekranda çizilecek sade modeli. .docx ([DocxReader]) ve eski .doc ([DocReader]) aynı modele
 * çevrilir; görüntüleyici ([com.eayd.arkive.screens.preview.WordViewer]) dosya biçimini bilmez.
 * Bu paket Android'e bağlı değildir (yalnızca Java/Kotlin standart kütüphanesi), bu yüzden bilgisayarda test edilir.
 */

enum class WordAlign { Left, Center, Right, Justify }

enum class WordFont { Sans, Serif, Mono }

/**
 * Aynı biçime sahip bir metin parçası. [color] ve [highlight] ARGB'dir (null: varsayılan renk / vurgu yok).
 * [script]: 1 üst simge, -1 alt simge. [link]: tıklanınca açılacak adres (yalnızca http/https/mailto).
 * Metinde '\n' satır sonu, '\t' sekme demektir.
 */
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

        /** Üst/alt simge metninin görüntüleyicide küçültüldüğü oran. */
        const val SCRIPT_SHRINK = 0.7f

        /** Satır yükseltme (w:position / sprmCHpsPos) bu yarım punto değerinden büyükse simge sayılır. */
        const val MIN_RAISE_HALF_POINTS = 2
    }
}

sealed interface WordBlock

/**
 * Bir paragraf. Girinti ve boşluklar punto (pt) cinsindendir. [firstLinePt] ilk satırın [indentPt]'e göre kaymasıdır
 * (negatif: asılı girinti). [label] liste imi ("•", "1.", "a)"); doluysa görüntüleyici imi asılı girintide çizer.
 * [heading] 1..9: başlık düzeyi, 0: gövde metni. [emptyPt]: paragraf boşsa bıraktığı satırın puntosu.
 */
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

/** Satır içi ya da kayan görsel. [key] [WordDoc.images] içindeki anahtardır; boyutlar punto, 0: görselin kendi boyutu. */
data class WordImage(
    val key: String,
    val widthPt: Float,
    val heightPt: Float,
    val align: WordAlign = WordAlign.Left,
) : WordBlock

/** [columnsPt]: ızgara sütun genişlikleri. [borderColor]: null ise çizgisiz tablo. */
class WordTable(
    val columnsPt: List<Float>,
    val rows: List<WordRow>,
    val borderColor: Int?,
) : WordBlock

class WordRow(val cells: List<WordCell>)

/** [span]: kaç ızgara sütununu kapsadığı. [continuesAbove]: üstteki hücreyle dikey birleşik devam hücresi (içi boş). */
class WordCell(
    val blocks: List<WordBlock>,
    val span: Int = 1,
    val background: Int? = null,
    val bordered: Boolean = false,
    val continuesAbove: Boolean = false,
)

/** Sayfa sonu (görüntüleyicide sayfalar arası boşluk). */
data object WordPageBreak : WordBlock

/**
 * Okunmuş belge. [images]: görsel anahtarı → dosya baytları (png/jpeg/gif/bmp/webp). [truncated]: belge çok
 * uzun olduğu için sonu gösterilmiyor. [format]: "docx" ya da "doc". [imageCount]: belgedeki görsel sayısı (gösterilemeyen
 * türler dahil); özetleme "görsel ağırlıklı mı" kararı için.
 */
class WordDoc(
    val blocks: List<WordBlock>,
    val images: Map<String, ByteArray>,
    val truncated: Boolean,
    val format: String,
    val imageCount: Int = images.size,
) {
    /** Belgedeki tüm düz metin (testler ve boş belge denetimi için). */
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

    /**
     * Özetleme için düz metin: her paragraf bir satır (tablo hücrelerindeki paragraflar da, sırayla), ardışık boş
     * satırlar teke iner. Biçim (kalın, renk...) taşımaz. Telefondaki .docx okuyucusunun ([com.eayd.arkive.ai.DocumentReader])
     * çıkardığı metinle aynı düzendedir, böylece .doc ile .docx aynı biçimde özetlenir.
     */
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

/** Dosya Word belgesi olarak okunamadı (bozuk, desteklenmeyen biçim…). */
class WordFormatException(message: String) : Exception(message)

