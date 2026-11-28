package com.eayd.arkive.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Kaydırma arka planının rengi. */
enum class SwipeKind { Destructive, Primary, Secondary }

/** Satır kaydırılınca yapılacak işlem. */
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val kind: SwipeKind,
    val onSwipe: () -> Unit,
)

/** Satırın genişliğinin bu kadarı kaydırılınca işlem yapılır. */
private const val THRESHOLD = 0.35f

/**
 * Satırı sağa ([toEnd]) ya da sola ([toStart]) kaydırınca işlemi yapar; satır sonra yerine döner. Null olan yön kapalıdır.
 *
 * İşlemler ekran okuyucuya da satırın eylemleri olarak sunulur.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * THRESHOLD })
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.currentValue) {
        val action = when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> currentEnd
            SwipeToDismissBoxValue.EndToStart -> currentStart
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        action?.onSwipe?.invoke()
        state.reset()
    }
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val actions = if (enabled) listOfNotNull(toEnd, toStart) else emptyList()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            if (actions.isNotEmpty()) {
                customActions = actions.map { action -> CustomAccessibilityAction(action.label) { action.onSwipe(); true } }
            }
        },
        enableDismissFromStartToEnd = toEnd != null,
        enableDismissFromEndToStart = toStart != null,
        gesturesEnabled = enabled && (toEnd != null || toStart != null),
        backgroundContent = { SwipeBackground(state, toEnd, toStart) },
    ) {
        content()
    }
}

/** Kaydırılan satırın altında görünen renkli alan, simge ve yazı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(state: SwipeToDismissBoxState, toEnd: SwipeAction?, toStart: SwipeAction?) {
    val direction = state.dismissDirection
    val action = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> toEnd
        SwipeToDismissBoxValue.EndToStart -> toStart
        SwipeToDismissBoxValue.Settled -> null
    } ?: return
    val (container, onContainer) = colorsOf(action.kind)
    val scale by animateFloatAsState(if (state.targetValue == direction) 1.2f else 1f, label = "swipeIcon")
    val fromStart = direction == SwipeToDismissBoxValue.StartToEnd

    Box(
        modifier = Modifier
            .fillMaxSize()
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
