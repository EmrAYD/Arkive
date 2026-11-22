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
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.ui.components.FolderIcon
import com.eayd.arkive.ui.components.fileTypeIcon
import com.eayd.arkive.ui.components.folderAccent
import com.eayd.arkive.viewmodel.SelectedItems
import kotlin.math.roundToInt

private const val TAG = "ArkiveDrag"

/** Bir öğeyi basılı tutup klasörün üstüne sürükleme durumu. */
class FileDragState {
    internal val itemCoords = HashMap<String, LayoutCoordinates>()
    internal val folderCoords = HashMap<String, LayoutCoordinates>()
    internal val folderAreas = HashMap<String, String>()
    internal var listCoords: LayoutCoordinates? = null

    /** Sürüklenenler; null ise sürükleme yok. */
    var dragged by mutableStateOf<SelectedItems?>(null)
        private set

    /** Parmağın pencere içindeki konumu. */
    var position by mutableStateOf(Offset.Zero)
        private set

    /** Parmağın üstünde olduğu klasör. */
    var hoverFolderId by mutableStateOf<String?>(null)
        private set

    internal fun itemAt(pos: Offset): String? = hit(itemCoords, pos)

    private fun hit(map: Map<String, LayoutCoordinates>, pos: Offset, except: Set<String> = emptySet()): String? =
        map.entries.firstOrNull { (k, c) ->
            k !in except && c.isAttached && c.boundsInRoot().contains(pos)
        }?.key

    /** Sürüklenenlerin sayfası; karışıksa null. */
    private var draggedArea: String? = null

    private fun folderAt(pos: Offset): String? {
        val except = dragged?.folders?.map { it.id }?.toSet().orEmpty() +
            folderAreas.filterValues { it != draggedArea }.keys
        return hit(folderCoords, pos, except = except)
    }

    internal fun start(payload: SelectedItems, pos: Offset) {
        draggedArea = areaOf(payload)
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

    /** Parmak kalkınca bırakılan klasörü döner ve durumu sıfırlar. */
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

private fun areaOf(items: SelectedItems): String? =
    (items.files.map { FolderLocations.HOME } + items.folders.map { it.location }).toSet().singleOrNull()

/** Klasör satırını bırakma hedefi olarak kaydeder. */
@Composable
internal fun Modifier.trackDropFolder(state: FileDragState, folderId: String, area: String): Modifier {
    DisposableEffect(state, folderId) {
        onDispose {
            state.folderCoords.remove(folderId)
            state.folderAreas.remove(folderId)
        }
    }
    return onGloballyPositioned {
        state.folderCoords[folderId] = it
        state.folderAreas[folderId] = area
    }
}

/** Bileşenin ekrandaki yerini [map]'te tutar. */
@Composable
internal fun Modifier.trackBounds(map: MutableMap<String, LayoutCoordinates>, key: String): Modifier {
    DisposableEffect(map, key) { onDispose { map.remove(key) } }
    return onGloballyPositioned { map[key] = it }
}

/** Listeye uzun basınca seçim ve sürükleme. Bırakılan yer bir klasörse [onDrop] çağrılır. */
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
                if (cancelled != null) return@awaitEachGesture

                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val item = currentLongPress(key)
                Log.d(TAG, "uzun basış: $key, sürüklenebilir=${item != null}")

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
                            if (item == null) break
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
                    state.cancel()
                }
            }
        }
}

/** Sürüklerken liste kenarına yaklaşılınca listeyi kaydırır. */
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
                state.updateHover()
            }
            withFrameNanos { }
        }
    }
}

/** Parmağın altında süzülen küçük kart. */
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
            if (singleFolder != null && single == null) {
                FolderIcon(folderAccent(singleFolder.color), singleFolder.emoji, 24.dp)
            } else {
                Icon(
                    imageVector = when {
                        single != null && item.count == 1 -> fileTypeIcon(single.mimeType)
                        else -> Icons.AutoMirrored.Filled.DriveFileMove
                    },
                    contentDescription = null,
                    tint = folderAccent(null),
                    modifier = Modifier.size(24.dp),
                )
            }
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
