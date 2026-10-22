package com.eayd.arkive.screens.folders

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ui.components.fileTypeIcon
import com.eayd.arkive.ui.components.folderColorOrNull
import com.eayd.arkive.viewmodel.SelectedItems
import kotlin.math.roundToInt

private const val TAG = "ArkiveDrag"

/**
 * Bir öğeye basılı tutup bir klasörün üstüne sürükleme işleminin durumu.
 *
 * Satırların ve klasörlerin ekrandaki yerleri ([LayoutCoordinates]) burada tutulur; parmağın o an hangi
 * satırın üstünde olduğu bu yerlerle hesaplanır. Liste kaydırılırken satırlar yer değiştirdiği için
 * "hangi klasörün üstündeyim" bilgisi parmak kıpırdamasa bile yeniden hesaplanır ([updateHover]).
 */
class FileDragState {
    // Ekrandaki (oluşturulmuş) satırların yerleri. Ekrandan çıkan satır kendini listeden siler ([trackBounds]).
    internal val itemCoords = HashMap<String, LayoutCoordinates>()     // tüm satırlar (uzun basış için)
    internal val folderCoords = HashMap<String, LayoutCoordinates>()   // bırakılabilir klasörler (anahtar: klasör id)
    internal var listCoords: LayoutCoordinates? = null

    /** Sürüklenenler; null ise sürükleme yok. */
    var dragged by mutableStateOf<SelectedItems?>(null)
        private set

    /** Parmağın pencere içindeki konumu. */
    var position by mutableStateOf(Offset.Zero)
        private set

    /** Parmağın şu an üstünde olduğu klasör (varsa). */
    var hoverFolderId by mutableStateOf<String?>(null)
        private set

    internal fun itemAt(pos: Offset): String? = hit(itemCoords, pos)

    private fun hit(map: Map<String, LayoutCoordinates>, pos: Offset, except: Set<String> = emptySet()): String? =
        map.entries.firstOrNull { (k, c) ->
            k !in except && c.isAttached && c.boundsInRoot().contains(pos)
        }?.key

    // Sürüklenen klasörler kendi üstlerine bırakılamaz.
    private fun folderAt(pos: Offset): String? =
        hit(folderCoords, pos, except = dragged?.folders?.map { it.id }?.toSet().orEmpty())

    internal fun start(payload: SelectedItems, pos: Offset) {
        dragged = payload
        position = pos
        hoverFolderId = folderAt(pos)
    }

    internal fun moveTo(pos: Offset) {
        position = pos
        updateHover()
    }

    internal fun updateHover() {
        val id = folderAt(position)
        if (id != hoverFolderId) hoverFolderId = id
    }

    /** Parmak kalktı: bırakılan klasörün kimliğini döner (klasör üstünde değilse null) ve durumu sıfırlar. */
    internal fun finish(): String? {
        val target = hoverFolderId
        cancel()
        return target
    }

    internal fun cancel() {
        dragged = null
        hoverFolderId = null
    }
}

/** Bu bileşenin ekrandaki yerini [map] içinde [key] altında tutar; bileşen ekrandan kalkınca siler. */
@Composable
internal fun Modifier.trackBounds(map: MutableMap<String, LayoutCoordinates>, key: String): Modifier {
    DisposableEffect(map, key) { onDispose { map.remove(key) } }
    return onGloballyPositioned { map[key] = it }
}

/**
 * Listenin üstüne konur. Bir satıra (dosya ya da klasör) basılı tutulursa (uzun basış) [onLongPress] çağrılır:
 * ekran öğeyi seçer. Seçili tek öğe varsa ve parmak kaymaya başlarsa öğe sürüklenir; parmak kalkınca bir
 * klasörün üstündeyse [onDrop] çağrılır. Birden fazla öğe seçiliyse sürükleme olmaz.
 *
 * Dokunuşlar "Initial" aşamasında izlenir: böylece kısa dokunuş, kaydırma ve satırdaki düğmeler olduğu gibi
 * çalışır; yalnızca uzun basış tamamlanınca bırakma olayı tüketilir (satır "tıklanmış" sayılmasın diye).
 * Uzun basış iptal koşulları: parmak kalktı, ikinci parmak değdi ya da parmak kaymaya başladı (kaydırma).
 *
 * Hareketi satırın kendisine değil listenin kabına koyduk: otomatik kaydırmada sürüklenen satır
 * ekrandan çıkıp yok olsa bile hareket kesilmesin diye.
 *
 * @param onLongPress satır anahtarını alır, öğeyi seçer; sürüklenecekleri (seçimin tamamı), sürükleme olamıyorsa null döner.
 */
@Composable
internal fun Modifier.itemDragSource(
    state: FileDragState,
    onLongPress: (key: String) -> SelectedItems?,
    onDrop: (payload: SelectedItems, folderId: String) -> Unit,
): Modifier {
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentDrop by rememberUpdatedState(onDrop)
    val haptic = LocalHapticFeedback.current

    return this
        .onGloballyPositioned { state.listCoords = it }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val origin = state.listCoords?.takeIf { it.isAttached }?.positionInRoot() ?: return@awaitEachGesture
                val key = state.itemAt(origin + down.position) ?: return@awaitEachGesture

                // 1) Uzun basışı bekle. Süre dolarsa blok null döner (= uzun basış); önce iptal olursa true.
                val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var gone = false
                    while (!gone) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        gone = change == null ||
                            change.changedToUpIgnoreConsumed() ||
                            event.changes.count { it.pressed } > 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    }
                    true
                }
                if (cancelled != null) return@awaitEachGesture   // süre dolmadan bırakıldı/kaydırıldı: uzun basış değil

                // 2) Seçime geç. Bundan sonra parmak kalkana kadar bütün olayları biz tüketiriz (liste kaymasın,
                //    satır "tıklanmış" sayılmasın, kenar menüsü açılmasın).
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val item = currentLongPress(key)
                Log.d(TAG, "uzun basış: $key, sürüklenebilir=${item != null}")

                // 3) Parmak kaymaya başlarsa sürükleme başlar; kaymadan kalkarsa yalnızca seçim kalır.
                //    Mesafe pencere koordinatlarıyla ölçülür: seçime geçince liste birkaç dp oynasa bile
                //    parmak "kaymış" sayılmasın.
                val downRoot = origin + down.position
                var dragging = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val listOrigin = state.listCoords?.takeIf { it.isAttached }?.positionInRoot() ?: break
                        val pos = listOrigin + change.position
                        val up = change.changedToUpIgnoreConsumed() || !change.pressed

                        if (!dragging && !up && (pos - downRoot).getDistance() > viewConfiguration.touchSlop) {
                            if (item == null) break   // bu seçim sürüklenemiyor (ör. AI klasörü): hareketi liste alsın
                            dragging = true
                            state.start(item, pos)
                            Log.d(TAG, "sürükleme başladı: ${item.label}")
                        }
                        event.changes.forEach { it.consume() }

                        if (up) {
                            if (dragging) {
                                state.moveTo(pos)
                                val target = state.finish()
                                Log.d(TAG, "bırakıldı, hedef klasör=$target")
                                if (target != null && item != null) currentDrop(item, target)
                            }
                            break
                        }
                        if (dragging) state.moveTo(pos)
                    }
                } finally {
                    state.cancel()   // bırakma sırasında zaten sıfırlandıysa zararsız
                }
            }
        }
}

/**
 * Sürükleme sürerken parmak listenin üst ya da alt kenarına yaklaşırsa listeyi kaydırır, böylece ekranda
 * görünmeyen klasörlere de ulaşılır. Kaydırma hızı kenara yaklaştıkça artar.
 */
@Composable
internal fun AutoScrollWhileDragging(state: FileDragState, listState: LazyListState) {
    val active = state.dragged != null
    val density = LocalDensity.current
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val zone = with(density) { 72.dp.toPx() }
        val maxStep = with(density) { 12.dp.toPx() }
        while (true) {
            val list = state.listCoords
            if (list != null && list.isAttached) {
                val bounds = list.boundsInRoot()
                val y = state.position.y
                val topEdge = bounds.top + zone
                val bottomEdge = bounds.bottom - zone
                val delta = when {
                    y < topEdge -> -maxStep * ((topEdge - y) / zone).coerceIn(0f, 1f)
                    y > bottomEdge -> maxStep * ((y - bottomEdge) / zone).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (delta != 0f) listState.scrollBy(delta)
                // Liste kaydıysa parmağın altındaki klasör değişmiş olabilir.
                state.updateHover()
            }
            withFrameNanos { }
        }
    }
}

/** Sürüklenen dosyayı temsil eden, parmağın altında süzülen küçük kart. Listenin kabı içinde çizilir. */
@Composable
internal fun DragGhost(state: FileDragState) {
    val item = state.dragged ?: return
    val list = state.listCoords?.takeIf { it.isAttached } ?: return
    val local = state.position - list.positionInRoot()
    val density = LocalDensity.current
    val halfWidth = with(density) { 110.dp.toPx() }
    val halfHeight = with(density) { 22.dp.toPx() }

    Card(
        modifier = Modifier
            .offset { IntOffset((local.x - halfWidth).roundToInt(), (local.y - halfHeight).roundToInt()) }
            .width(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val single = item.files.singleOrNull()
            val singleFolder = item.folders.singleOrNull().takeIf { item.count == 1 }
            Icon(
                imageVector = when {
                    single != null && item.count == 1 -> fileTypeIcon(single.mimeType)
                    singleFolder != null -> Icons.Filled.Folder
                    else -> Icons.AutoMirrored.Filled.DriveFileMove
                },
                contentDescription = null,
                tint = singleFolder?.let { folderColorOrNull(it.color) } ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
