package com.eayd.arkive.screens.preview

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eayd.arkive.word.WordAlign
import com.eayd.arkive.word.WordBlock
import com.eayd.arkive.word.WordDoc
import com.eayd.arkive.word.WordFont
import com.eayd.arkive.word.WordImage
import com.eayd.arkive.word.WordPageBreak
import com.eayd.arkive.word.WordParagraph
import com.eayd.arkive.word.WordRun
import com.eayd.arkive.word.WordTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WORD_MIN_SCALE = 0.6f
private const val WORD_MAX_SCALE = 3f
private const val WORD_MAX_IMAGE_PX = 2000
private val PAPER = Color.White
private val INK = Color(0xFF1B1B1F)

/** Belgedeki sayfa sonlarıyla bölünmüş bloklardan, listede tek tek çizilecek öğeler. */
private class WordItem(val block: WordBlock, val firstOnPage: Boolean, val lastOnPage: Boolean)

private fun layoutItems(blocks: List<WordBlock>): List<WordItem> {
    val out = ArrayList<WordItem>(blocks.size)
    var pageStart = 0
    fun closePage(endExclusive: Int) {
        if (endExclusive <= pageStart) return
        for (i in pageStart until endExclusive) out.add(WordItem(blocks[i], i == pageStart, i == endExclusive - 1))
    }
    for (i in blocks.indices) {
        if (blocks[i] === WordPageBreak) {
            closePage(i)
            pageStart = i + 1
        }
    }
    closePage(blocks.size)
    return out
}

/**
 * Word belgesi (.docx / .doc): sayfa sonlarıyla ayrılmış beyaz "kağıt" üzerinde, biçimi korunarak (başlıklar, kalın/
 * italik, renkler, listeler, tablolar, görseller, bağlantılar) çizilir. PDF'in aksine sayfa düzeni ekran genişliğine
 * göre akar, böylece telefonda yazılar okunur boyutta kalır. İki parmakla yazı boyutu büyütülüp küçültülür; metin
 * seçilip kopyalanabilir.
 */
@Composable
internal fun WordViewer(doc: WordDoc) {
    var scale by remember(doc) { mutableStateOf(1f) }
    val items = remember(doc) { layoutItems(doc.blocks) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Listenin yan boşlukları (8 dp + 8 dp) ve kağıdın iç boşluğu (16 dp + 16 dp) düşülmüş yazı genişliği.
        val contentWidth = maxWidth - 16.dp - 32.dp

        SelectionContainer(
            Modifier
                .fillMaxSize()
                // İki parmakla yakınlaştırma: "Initial" geçişinde dinlenir, böylece iki parmak listenin kendi
                // kaydırmasından önce yakalanır. Tek parmak olduğu gibi listeye gider (PdfViewer ile aynı yöntem).
                .pointerInput(doc) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val z = event.calculateZoom()
                                if (z != 1f) scale = (scale * z).coerceIn(WORD_MIN_SCALE, WORD_MAX_SCALE)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                if (doc.truncated) {
                    item {
                        Text(
                            "Belge çok uzun olduğu için yalnızca ilk bölümü gösteriliyor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                itemsIndexed(items) { index, item ->
                    Box(Modifier.fillMaxWidth().padding(top = if (item.firstOnPage && index > 0) 8.dp else 0.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(PAPER)
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = if (item.firstOnPage) 16.dp else 0.dp,
                                    bottom = if (item.lastOnPage) 16.dp else 0.dp,
                                ),
                        ) {
                            BlockView(item.block, doc, scale, contentWidth, backdrop = null, nested = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockView(block: WordBlock, doc: WordDoc, scale: Float, width: Dp, backdrop: Color?, nested: Boolean) {
    when (block) {
        is WordParagraph -> ParagraphView(block, scale, backdrop)
        is WordImage -> ImageView(block, doc, scale, width)
        is WordTable -> TableView(block, doc, scale, width, nested)
        WordPageBreak -> Unit
    }
}

// --- Paragraf -------------------------------------------------------------------------------------------

private fun familyOf(font: WordFont): FontFamily? = when (font) {
    WordFont.Serif -> FontFamily.Serif
    WordFont.Mono -> FontFamily.Monospace
    WordFont.Sans -> null
}

private fun buildText(p: WordParagraph, scale: Float): AnnotatedString = buildAnnotatedString {
    for (run in p.runs) {
        val shrink = if (run.script != 0) WordRun.SCRIPT_SHRINK else 1f
        val decoration = when {
            run.underline && run.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            run.underline -> TextDecoration.Underline
            run.strike -> TextDecoration.LineThrough
            else -> null
        }
        val style = SpanStyle(
            fontSize = (run.sizePt * shrink * scale).sp,
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontFamily = familyOf(run.font),
            color = run.color?.let { Color(it) } ?: Color.Unspecified,
            background = run.highlight?.let { Color(it) } ?: Color.Unspecified,
            textDecoration = decoration,
            baselineShift = when (run.script) {
                1 -> BaselineShift.Superscript
                -1 -> BaselineShift.Subscript
                else -> null
            },
        )
        val text = run.text.replace("\t", "    ")
        val link = run.link
        if (link != null) {
            withLink(LinkAnnotation.Url(link)) { withStyle(style) { append(text) } }
        } else {
            withStyle(style) { append(text) }
        }
    }
}

@Composable
private fun ParagraphView(p: WordParagraph, scale: Float, backdrop: Color?) {
    val background = p.background?.let { Color(it) }
    val ground = background ?: backdrop
    // Word'de "otomatik" renk, koyu zeminde beyaza döner.
    val ink = if (ground != null && ground.luminance() < 0.35f) Color.White else INK

    val maxSize = p.runs.maxOfOrNull { it.sizePt } ?: p.emptyPt
    val lineHeight = (maxSize * 1.25f * p.lineSpacing * scale).sp
    val align = when (p.align) {
        WordAlign.Center -> TextAlign.Center
        WordAlign.Right -> TextAlign.End
        WordAlign.Justify -> TextAlign.Justify
        WordAlign.Left -> TextAlign.Start
    }
    val decoration = Modifier
        .fillMaxWidth()
        .let { m -> if (background != null) m.background(background) else m }
        .let { m ->
            if (p.borderTop == null && p.borderBottom == null) m
            else m.drawBehind {
                val stroke = 1.dp.toPx()
                p.borderTop?.let { drawLine(Color(it), Offset(0f, 0f), Offset(size.width, 0f), stroke) }
                p.borderBottom?.let { drawLine(Color(it), Offset(0f, size.height), Offset(size.width, size.height), stroke) }
            }
        }
        .let { m -> if (p.heading > 0) m.semantics { heading() } else m }
        .padding(top = (p.beforePt * scale).dp, bottom = (p.afterPt * scale).dp)

    Box(decoration) {
        if (p.runs.isEmpty() && p.label == null) {
            // Boş paragraf: yalnızca bir satırlık boşluk
            Spacer(Modifier.fillMaxWidth().height((p.emptyPt * 1.25f * p.lineSpacing * scale).dp))
        } else if (p.label != null) {
            val indent = p.indentPt.coerceIn(0f, 400f)
            val labelStart = (indent + p.firstLinePt).coerceAtLeast(0f)
            val hanging = (indent - labelStart).coerceAtLeast(14f)
            val labelStyle = p.runs.firstOrNull()
            Row(Modifier.fillMaxWidth().padding(start = (labelStart * scale).dp)) {
                Box(Modifier.width((hanging * scale).dp)) {
                    Text(
                        text = p.label,
                        color = ink,
                        fontSize = ((labelStyle?.sizePt ?: p.emptyPt) * scale).sp,
                        fontWeight = if (labelStyle?.bold == true) FontWeight.Bold else null,
                        lineHeight = lineHeight,
                        style = TextStyle.Default,
                    )
                }
                Text(
                    text = buildText(p, scale),
                    color = ink,
                    textAlign = align,
                    lineHeight = lineHeight,
                    style = TextStyle.Default,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            val indent = p.indentPt.coerceIn(0f, 400f)
            val firstStart = (indent + p.firstLinePt).coerceIn(0f, 400f)
            val base = minOf(indent, firstStart)
            Text(
                text = buildText(p, scale),
                color = ink,
                textAlign = align,
                lineHeight = lineHeight,
                style = if (indent != firstStart) {
                    TextStyle(textIndent = TextIndent(((firstStart - base) * scale).sp, ((indent - base) * scale).sp))
                } else {
                    TextStyle.Default
                },
                modifier = Modifier.fillMaxWidth().padding(start = (base * scale).dp),
            )
        }
    }
}

// --- Görsel ---------------------------------------------------------------------------------------------

private class DecodedImage(val image: ImageBitmap, val width: Int, val height: Int)

private fun decodeImage(bytes: ByteArray, targetPx: Int): DecodedImage? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        DecodedImage(bitmap.asImageBitmap(), bounds.outWidth, bounds.outHeight)
    } catch (e: OutOfMemoryError) {
        null
    }
}

@Composable
private fun ImageView(block: WordImage, doc: WordDoc, scale: Float, availableWidth: Dp) {
    val bytes = doc.images[block.key]
    val density = LocalDensity.current
    val wantedPx = with(density) { (availableWidth * scale).roundToPx() }
    // Yakınlaştırma sırasında her karede yeniden çözmemek için genişlik 256 pikselin katlarına yuvarlanır.
    val decodePx = ((wantedPx + 255) / 256 * 256).coerceIn(256, WORD_MAX_IMAGE_PX)

    var decoded by remember(block.key) { mutableStateOf<DecodedImage?>(null) }
    var failed by remember(block.key) { mutableStateOf(bytes == null) }
    LaunchedEffect(block.key, decodePx) {
        if (bytes != null) {
            val result = withContext(Dispatchers.IO) { decodeImage(bytes, decodePx) }
            if (result != null) {
                decoded = result
            } else if (decoded == null) {
                failed = true
            }
        }
    }

    val img = decoded
    if (img == null) {
        if (failed) {
            Text(
                "[Görsel gösterilemiyor]",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF757575),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            Spacer(Modifier.height(24.dp))
        }
    } else {
        val widthPt = if (block.widthPt > 0f) block.widthPt else img.width * 0.75f
        val aspect = if (block.widthPt > 0f && block.heightPt > 0f) block.widthPt / block.heightPt else img.width.toFloat() / img.height
        val width = minOf((widthPt * scale).dp, availableWidth)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = when (block.align) {
                WordAlign.Center -> Alignment.TopCenter
                WordAlign.Right -> Alignment.TopEnd
                else -> Alignment.TopStart
            },
        ) {
            Image(
                bitmap = img.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(width).aspectRatio(aspect),
            )
        }
    }
}

// --- Tablo ----------------------------------------------------------------------------------------------

@Composable
private fun TableView(table: WordTable, doc: WordDoc, scale: Float, availableWidth: Dp, nested: Boolean) {
    val sumPt = table.columnsPt.sum().coerceAtLeast(1f)
    // Tablo ekrana sığmıyorsa orantılı küçülür; yakınlaştırınca ekrandan taşabilir ve yatay kaydırılır.
    // İç içe tabloda verilen genişlik zaten yakınlaştırılmış olduğundan karşılaştırma yakınlaştırmasız yapılır.
    val availablePt = if (nested) availableWidth.value / scale else availableWidth.value
    val fit = if (sumPt > availablePt) availablePt / sumPt else 1f
    val columns = table.columnsPt.map { (it * fit * scale).coerceAtLeast(36f).dp }
    var total = 0.dp
    for (c in columns) total += c
    val scroll = !nested && total > availableWidth
    val hScroll = rememberScrollState()
    val lineColor = table.borderColor?.let { Color(it) }

    Box(Modifier.fillMaxWidth().let { m -> if (scroll) m.horizontalScroll(hScroll) else m }.padding(vertical = 4.dp)) {
        Column(Modifier.width(total)) {
            for (row in table.rows) {
                Row(Modifier.height(IntrinsicSize.Max)) {
                    var col = 0
                    for (cell in row.cells) {
                        val span = cell.span.coerceAtLeast(1)
                        var sum = 0.dp
                        for (i in col until minOf(col + span, columns.size)) sum += columns[i]
                        col += span
                        val cellWidth = sum
                        if (cellWidth <= 0.dp) continue

                        val cellBackground = cell.background?.let { Color(it) }
                        val edge = if (lineColor != null) lineColor else if (cell.bordered) Color.Black else null
                        Box(
                            Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .let { m -> if (cellBackground != null) m.background(cellBackground) else m }
                                .let { m -> if (edge != null) m.border(0.75.dp, edge) else m }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Column {
                                for (inner in cell.blocks) {
                                    BlockView(inner, doc, scale, maxOf(cellWidth - 8.dp, 24.dp), cellBackground, nested = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
