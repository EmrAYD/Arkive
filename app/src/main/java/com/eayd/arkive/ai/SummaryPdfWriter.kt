package com.eayd.arkive.ai

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import java.io.ByteArrayOutputStream

/**
 * Özet metnini A4 PDF'e çevirir (Android'in kendi [PdfDocument]'i; ek kütüphane yok). Telefonda üretilen özetin PDF'i
 * budur ve internet gelince sunucuya yüklenir; bulutta üretilenleri sunucu aynı şablon ölçüleriyle kendisi yazar
 * (backend/app/summary_pdf.py). Metin bloklara [SummaryBlocks] ile, görünüm [SummaryTemplate] ile belirlenir.
 *
 * Yazı tipleri assets/fonts altındaki Liberation Serif/Sans'tır (Times New Roman / Arial ile aynı ölçülere sahip serbest
 * karşılıkları): cihazın yazı tipine bağlı kalınmaz ve Türkçe harflerin hepsi vardır.
 */
object SummaryPdfWriter {

    private const val PAGE_W = 595 // A4, 72 dpi'de nokta (pt)
    private const val PAGE_H = 842
    private const val PT_PER_CM = 72f / 2.54f
    private val GRAY = Color.rgb(89, 89, 89)
    private val FOOTER_GRAY = Color.rgb(102, 102, 102)

    private class Fonts(val regular: Typeface, val bold: Typeface)

    private val fontCache = HashMap<PdfFamily, Fonts>()

    private fun fonts(context: Context, family: PdfFamily): Fonts = synchronized(fontCache) {
        fontCache.getOrPut(family) {
            val name = if (family == PdfFamily.Serif) "LiberationSerif" else "LiberationSans"
            Fonts(
                Typeface.createFromAsset(context.assets, "fonts/$name-Regular.ttf"),
                Typeface.createFromAsset(context.assets, "fonts/$name-Bold.ttf"),
            )
        }
    }

    /** Paragraf içinde kalın yazı: gerçek kalın yazı tipi dosyasını kullanır (StyleSpan sahte kalın üretirdi). */
    private class FaceSpan(private val face: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) {
            paint.typeface = face
        }

        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = face
        }
    }

    /** Özetin PDF baytları. Sorun çıkarsa istisna atar; çağıran, sunucunun kendisinin üretmesine güvenebilir. */
    fun render(context: Context, sourceName: String, summary: String, template: SummaryTemplate): ByteArray {
        val doc = PdfDocument()
        try {
            Writer(doc, fonts(context, template.family), template).write(sourceName, summary)
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    private class Writer(private val doc: PdfDocument, private val fonts: Fonts, private val t: SummaryTemplate) {

        private val left = t.marginsCm[0] * PT_PER_CM
        private val right = t.marginsCm[1] * PT_PER_CM
        private val top = t.marginsCm[2] * PT_PER_CM
        private val bottom = t.marginsCm[3] * PT_PER_CM
        private val contentWidth = PAGE_W - left - right
        private val limit = PAGE_H - bottom
        private val accent = t.accent ?: Color.BLACK

        private var page: PdfDocument.Page? = null
        private var number = 0
        private var y = top

        private val canvas get() = page!!.canvas

        private fun paint(face: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }

        private fun newPage() {
            finishPage()
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
            y = top
        }

        private fun finishPage() {
            val p = page ?: return
            val footer = paint(fonts.regular, 9f, FOOTER_GRAY).apply { textAlign = Paint.Align.CENTER }
            p.canvas.drawText("Sayfa $number", PAGE_W / 2f, PAGE_H - bottom / 2f, footer)
            doc.finishPage(p)
            page = null
        }

        private fun layoutOf(text: CharSequence, paint: TextPaint, width: Float, align: PdfAlign, leading: Float): StaticLayout {
            val fm = paint.fontMetrics
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(if (align == PdfAlign.Center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                // Satır aralığı = istenen "leading": doğal satır yüksekliğine eklenecek fark.
                .setLineSpacing(leading - (fm.descent - fm.ascent), 1f)
                .setIncludePad(false)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            if (align == PdfAlign.Justify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            }
            return builder.build()
        }

        private fun styled(text: String, boldFace: Typeface): CharSequence {
            val sb = SpannableStringBuilder()
            for (run in SummaryBlocks.runs(text)) {
                val start = sb.length
                sb.append(run.text)
                if (run.bold) sb.setSpan(FaceSpan(boldFace), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sb
        }

        /**
         * [layout]'u sayfalara yayarak yerleştirir. Tek satır kalan başlangıç/son satırlar (yetim/dul) sayfalar arasında
         * bölünmez; [keepWithNext] başlıkların sayfa dibinde tek kalmasını önler. [marker]: ilk satırın solundaki madde işareti.
         */
        private fun place(
            layout: StaticLayout,
            x: Float,
            spaceBefore: Float,
            spaceAfter: Float,
            keepWithNext: Boolean = false,
            marker: String? = null,
            markerX: Float = 0f,
            markerPaint: TextPaint? = null,
        ) {
            if (page == null) newPage()
            if (y > top) y += spaceBefore
            val count = layout.lineCount
            var line = 0
            while (line < count) {
                val avail = limit - y
                var end = line
                while (end < count && layout.getLineBottom(end) - layout.getLineTop(line) <= avail) end++
                val atTop = y <= top + 1
                if (end == line) {
                    if (!atTop) { newPage(); continue }
                    end = line + 1 // sayfadan uzun tek satır: yine de yerleştir
                }
                if (!atTop && line == 0 && end < count && end < 2) { newPage(); continue } // yetim satır
                if (end < count && count - end == 1 && end - line > 2) end-- // dul satır: son sayfaya iki satır kalsın
                if (keepWithNext && end == count && !atTop && line == 0) {
                    val spare = limit - (y + layout.getLineBottom(count - 1))
                    if (spare < spaceAfter + t.bodySize * t.leading * 2) { newPage(); continue }
                }
                val from = layout.getLineTop(line).toFloat()
                val to = layout.getLineBottom(end - 1).toFloat()
                if (line == 0 && marker != null && markerPaint != null) {
                    canvas.drawText(marker, markerX, y + layout.getLineBaseline(0), markerPaint)
                }
                canvas.save()
                canvas.translate(x, y - from)
                canvas.clipRect(0f, from, layout.width.toFloat(), to)
                layout.draw(canvas)
                canvas.restore()
                y += to - from
                line = end
                if (line < count) newPage()
            }
            y += spaceAfter
        }

        fun write(sourceName: String, summary: String) {
            val body = t.bodySize
            val regular = paint(fonts.regular, body, Color.BLACK)
            val headingPaint = paint(fonts.bold, t.headingSize, accent)
            val titlePaint = paint(fonts.bold, t.titleSize, accent)
            val subtitlePaint = paint(fonts.regular, body, GRAY)

            newPage()
            place(
                layoutOf(styled("Arkive ile özetlenmiştir", fonts.bold), titlePaint, contentWidth, t.titleAlign, t.titleSize * 1.25f),
                left, 0f, body * 0.3f,
            )
            place(
                layoutOf("Kaynak dosya: $sourceName", subtitlePaint, contentWidth, t.titleAlign, body * t.leading),
                left, 0f, body * 0.8f,
            )
            if (t.titleRule) {
                val rule = Paint().apply { color = accent; strokeWidth = 0.8f }
                canvas.drawLine(left, y, left + contentWidth, y, rule)
                y += 0.8f + body * 0.6f
            }

            val indent = body * 1.6f
            for (block in SummaryBlocks.parse(summary)) {
                when (block.kind) {
                    BlockKind.Heading -> place(
                        layoutOf(styled(block.text, fonts.bold), headingPaint, contentWidth, PdfAlign.Left, t.headingSize * 1.25f),
                        left, t.headingSize * 0.7f, body * 0.35f, keepWithNext = true,
                    )
                    BlockKind.Bullet -> place(
                        layoutOf(styled(block.text, fonts.bold), regular, contentWidth - indent, t.align, body * t.leading),
                        left + indent, 0f, body * 0.35f,
                        marker = block.marker, markerX = left + body * 0.4f, markerPaint = regular,
                    )
                    BlockKind.Paragraph -> place(
                        layoutOf(styled(block.text, fonts.bold), regular, contentWidth, t.align, body * t.leading),
                        left, 0f, body * 0.6f,
                    )
                }
            }
            finishPage()
        }
    }
}
