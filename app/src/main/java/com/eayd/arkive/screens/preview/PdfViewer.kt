package com.eayd.arkive.screens.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.eayd.arkive.data.PdfDoc
import com.eayd.arkive.ui.components.SkeletonBox
import kotlin.math.roundToInt

private const val PDF_MAX_ZOOM = 4f
private const val PDF_DOUBLE_TAP_ZOOM = 2.5f
// Tek bir sayfa görüntüsünün en geniş hâli (piksel). ARGB_8888 olduğu için 2000 px genişlikte bir A4 sayfa
// ~22 MB tutar; daha büyüğü birkaç sayfa aynı anda bellekteyken uygulamayı çökertebilir.
private const val PDF_MAX_RENDER_WIDTH = 2000

/**
 * PDF: sayfalar alt alta kaydırılır; iki parmakla yakınlaştırılır, çift dokunuşla 2,5 kat yakınlaştırılıp sıfırlanır.
 *
 * Yakınlaştırma iki aşamalıdır:
 *  1. Parmaklar ekrandayken görüntü sadece büyütülür ([pinch], `graphicsLayer`): akıcıdır ama bulanıklaşır.
 *     Büyütme, parmakların ortasındaki nokta etrafında yapılır (o nokta yerinde kalır).
 *  2. Parmaklar kalkınca yakınlaştırma ([zoom]) sayfaların GERÇEK genişliğine işlenir ve sayfalar o genişlikte
 *     yeniden çizilir: yazılar yeniden keskinleşir. Yeni görüntü gelene kadar eskisi görünür kalır.
 *
 * Parmaklar çekilince görüntünün "gidip gelmemesi" için 2. aşamadaki üç değişiklik AYNI karede uygulanır:
 *  geçici büyütmenin sıfırlanması, sayfa genişliğinin değişmesi ve kaydırma konumunun ayarlanması.
 *  (Kaydırma konumunu sonraki karede ayarlarsak, yeni genişlikteki sayfalar bir an yanlış yerde görünüp
 *  sonra zıplar.) Bunun için yatay kaydırma da ScrollState yerine kendi tuttuğumuz [panX] ile yapılır:
 *  ScrollState sınırını yerleşimden sonra öğrendiği için, genişleme anında konumu ayarlamaya izin vermez.
 *  Dikey konum `requestScrollToItem` ile istenir, o da bir sonraki yerleşimde (aynı karede) uygulanır.
 */
@Composable
internal fun PdfViewer(doc: PdfDoc) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidth = maxWidth
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }.toFloat()
        val pagePaddingPx = with(density) { 16.dp.toPx() }   // listenin sağ+sol iç boşluğu (8 dp + 8 dp)

        var zoom by remember(doc) { mutableStateOf(1f) }     // sayfalara işlenmiş yakınlaştırma
        var pinch by remember(doc) { mutableStateOf(1f) }    // parmaklar ekrandayken geçici ek büyütme
        var panX by remember(doc) { mutableStateOf(0f) }     // yatay kaydırma: 0..(genişlik - ekran) piksel
        // Geçici büyütmenin merkezi (ekranın oranı olarak: 0..1) ve parmakların ekran koordinatı.
        var pinchOrigin by remember(doc) { mutableStateOf(Offset(0.5f, 0f)) }
        var pinchCentroid by remember(doc) { mutableStateOf(Offset.Zero) }
        val listState = rememberLazyListState()

        /** Yakınlaştırmayı [newZoom]'a çıkarır; [centroid] noktasının altındaki içerik yerinde kalır. */
        fun applyZoom(newZoom: Float, centroid: Offset) {
            val target = newZoom.coerceIn(1f, PDF_MAX_ZOOM)
            if (target == zoom) return
            val ratio = target / zoom

            // Noktanın altındaki sayfa (aralıkta kaldıysa en yakın sayfa) ve noktanın sayfa içindeki konumu (0..1).
            val items = listState.layoutInfo.visibleItemsInfo
            val page = items.firstOrNull { centroid.y >= it.offset && centroid.y < it.offset + it.size }
                ?: items.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - centroid.y) }
                ?: return
            val fraction = ((centroid.y - page.offset) / page.size).coerceIn(0f, 1f)

            // "Kaydırma ofseti 0 iken bir sayfanın üst kenarı nerede durur" değeri hep aynıdır (iç boşluk kadar);
            // ilk görünen sayfanın konumundan okunur. Böylece iç boşluk gibi ayrıntıları varsaymak gerekmez.
            val first = items.first()
            val base = first.offset + listState.firstVisibleItemScrollOffset

            // Yatay: parmakların altındaki içerik noktası yerinde kalsın.
            panX = ((panX + centroid.x) * ratio - centroid.x).coerceIn(0f, viewportWidthPx * (target - 1f))

            // Dikey: sayfanın yeni yüksekliğine göre, noktanın (centroid.y) altında kalacağı yere kaydır.
            val (w, h) = doc.pageSizes[page.index]
            val newPageHeight = (viewportWidthPx * target - pagePaddingPx) * h / w
            val wantedTop = centroid.y - fraction * newPageHeight
            listState.requestScrollToItem(page.index, (base - wantedTop).roundToInt().coerceAtLeast(0))

            zoom = target
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                // İki parmakla yakınlaştırma. "Initial" geçişinde dinlenir: böylece iki parmak, listenin kendi
                // kaydırmasından ÖNCE yakalanır ve kaydırma sanılmaz. Tek parmak olduğu gibi listeye gider.
                .pointerInput(doc) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var zooming = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                if (!zooming) {
                                    // İkinci parmak değdiği an: büyütmenin merkezi parmakların ortası olur.
                                    zooming = true
                                    val c = event.calculateCentroid()
                                    pinchCentroid = c
                                    pinchOrigin = Offset(c.x / size.width, c.y / size.height)
                                }
                                val z = event.calculateZoom()
                                if (z != 1f) pinch = (pinch * z).coerceIn(1f / zoom, PDF_MAX_ZOOM / zoom)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (zooming) {
                            // Üç değişiklik (pinch, zoom, kaydırma) aynı karede yazılır; arada ara durum çizilmez.
                            val committed = zoom * pinch
                            pinch = 1f
                            applyZoom(committed, pinchCentroid)
                        }
                    }
                }
                .pointerInput(doc) {
                    detectTapGestures(onDoubleTap = { tap ->
                        applyZoom(if (zoom > 1f) 1f else PDF_DOUBLE_TAP_ZOOM, tap)
                    })
                }
                // Yakınlaşmışken yatay sürükleme. Dikey sürükleme listeye gider (liste önce tüketir).
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        panX = (panX - delta).coerceIn(0f, viewportWidthPx * (zoom - 1f))
                    },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pinch
                        scaleY = pinch
                        transformOrigin = TransformOrigin(pinchOrigin.x, pinchOrigin.y)
                    },
            ) {
                val pageWidthPx = with(density) { (viewportWidth * zoom - 16.dp).roundToPx() }
                LazyColumn(
                    state = listState,
                    // requiredWidth: ekrandan GENİŞ olabilir (width() üst sınıra kırpardı). Kutu, fazla genişliği
                    // iki yana eşit paylaştırarak ortalar; graphicsLayer bunu telafi edip panX kadar kaydırır:
                    //   ekran x = içerik x + (ekran - genişlik)/2 + t   ve   ekranın solunda içerik x = panX olmalı.
                    modifier = Modifier
                        .requiredWidth(viewportWidth * zoom)
                        .fillMaxHeight()
                        .graphicsLayer { translationX = (viewportWidthPx * zoom - viewportWidthPx) / 2f - panX },
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = doc.pageCount, key = { it }) { index ->
                        PdfPage(doc, index, pageWidthPx.coerceIn(1, PDF_MAX_RENDER_WIDTH))
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int) {
    val (w, h) = doc.pageSizes[index]
    // Genişlik (yakınlaştırma) değişince yeni görüntü çizilir; o gelene kadar ESKİ görüntü görünür kalır
    // (produceState bunu yapmazdı: anahtar değişince değeri sıfırlar, sayfa bir an boş kalırdı).
    var bitmap by remember(doc, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc, index, widthPx) {
        bitmap = doc.render(index, widthPx) ?: bitmap
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(w.toFloat() / h)   // yükseklik önceden ayrılır; sayfalar çizildikçe liste zıplamaz
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Sayfa ${index + 1}",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SkeletonBox(Modifier.fillMaxSize(), RectangleShape)
        }
    }
}
