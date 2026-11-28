package com.eayd.arkive.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** Kaydırma arka planının rengi. */
enum class SwipeKind { Destructive, Primary, Secondary }

/** Satır kaydırılınca yapılacak işlem. */
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val kind: SwipeKind,
    val onSwipe: () -> Unit,
)

/** Satırın genişliğinin bu kadarı kaydırılıp bırakılınca işlem yapılır. */
private const val THRESHOLD = 0.35f

/** Satır en çok genişliğinin bu kadarı kaydırılabilir. */
private const val MAX_SHIFT = 0.6f

/** İşlemden sonra bir pencerenin açılması bu kadar beklenir (ms). */
private const val DIALOG_WAIT_MS = 800L

/**
 * Satırı sağa ([toEnd]) ya da sola ([toStart]) kaydırıp bırakınca işlemi yapar. Null olan yön kapalıdır.
 *
 * İşlem bir pencere (onay, özet) açarsa satır pencere kapanana kadar bırakıldığı yerde bekler, sonra yerine döner.
 * Pencere açılmazsa ya da eşik geçilmediyse hemen yerine döner.
 */
@Composable
fun SwipeActions(
    toEnd: SwipeAction?,
    toStart: SwipeAction?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val currentEnd by rememberUpdatedState(toEnd)
    val currentStart by rememberUpdatedState(toStart)
    var width by remember { mutableIntStateOf(0) }
    var shift by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val window = LocalWindowInfo.current
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf<Job?>(null) }
    val active = enabled && (toEnd != null || toStart != null)

    val dragState = rememberDraggableState { delta ->
        val limit = width * MAX_SHIFT
        val low = if (currentStart != null) -limit else 0f
        val high = if (currentEnd != null) limit else 0f
        shift = (shift + delta).coerceIn(low, high)
    }
    val reached by remember { derivedStateOf { width > 0 && abs(shift) >= width * THRESHOLD } }
    val toRight by remember { derivedStateOf { shift > 0f } }
    val moved by remember { derivedStateOf { shift != 0f } }

    LaunchedEffect(reached) {
        if (reached) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(active) {
        if (!active) shift = 0f
    }

    val actions = if (active) listOfNotNull(toEnd, toStart) else emptyList()
    Box(
        modifier = modifier
            .onSizeChanged { width = it.width }
            .semantics {
                if (actions.isNotEmpty()) {
                    customActions = actions.map { action -> CustomAccessibilityAction(action.label) { action.onSwipe(); true } }
                }
            },
    ) {
        val action = if (toRight) toEnd else toStart
        if (moved && action != null) {
            SwipeBackground(action, fromStart = toRight, reached = reached, modifier = Modifier.matchParentSize())
        }
        Box(
            Modifier
                .offset { IntOffset(shift.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = active,
                    onDragStarted = { settling?.cancel() },
                    onDragStopped = {
                        val chosen = when {
                            !reached -> null
                            shift > 0f -> currentEnd
                            else -> currentStart
                        }
                        chosen?.onSwipe?.invoke()
                        settling = scope.launch {
                            if (chosen != null) waitForDialog(window)
                            animate(shift, 0f, animationSpec = spring()) { value, _ -> shift = value }
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/**
 * İşlemin açtığı pencere kapanana kadar bekler. Pencere açılınca uygulamanın penceresi odağı kaybeder, kapanınca geri
 * alır; kısa sürede odak kaybolmazsa pencere açılmamış sayılır.
 */
private suspend fun waitForDialog(window: WindowInfo) {
    val opened = withTimeoutOrNull(DIALOG_WAIT_MS.milliseconds) {
        snapshotFlow { window.isWindowFocused }.first { focused -> !focused }
    }
    if (opened != null) snapshotFlow { window.isWindowFocused }.first { focused -> focused }
}

/** Kaydırılan satırın altında görünen renkli alan, simge ve yazı. */
@Composable
private fun SwipeBackground(action: SwipeAction, fromStart: Boolean, reached: Boolean, modifier: Modifier) {
    val (container, onContainer) = colorsOf(action.kind)
    val scale by animateFloatAsState(if (reached) 1.2f else 1f, label = "swipeIcon")
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(horizontal = 24.dp),
        contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (fromStart) Icon(action.icon, contentDescription = null, tint = onContainer, modifier = Modifier.scale(scale))
            Text(action.label, color = onContainer, style = MaterialTheme.typography.labelLarge)
            if (!fromStart) Icon(action.icon, contentDescription = null, tint = onContainer, modifier = Modifier.scale(scale))
        }
    }
}

/** Türün arka plan ve yazı renkleri. */
@Composable
private fun colorsOf(kind: SwipeKind): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        SwipeKind.Destructive -> colors.errorContainer to colors.onErrorContainer
        SwipeKind.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        SwipeKind.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
    }
}
