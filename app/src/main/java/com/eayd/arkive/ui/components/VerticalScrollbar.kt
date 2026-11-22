package com.eayd.arkive.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Kaydırılabilir alanın sağına ince bir kaydırma çubuğu çizer. İçerik sığıyorsa çizilmez. */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    thumbColor: Color,
    trackColor: Color = thumbColor.copy(alpha = 0.15f),
    width: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
): Modifier = drawWithContent {
    drawContent()
    val hidden = state.maxValue
    if (hidden <= 0 || hidden == Int.MAX_VALUE) return@drawWithContent
    val viewport = size.height
    val barWidth = width.toPx()
    val left = size.width - barWidth
    val radius = CornerRadius(barWidth / 2)
    val thumbHeight = (viewport * viewport / (viewport + hidden))
        .coerceAtLeast(minThumbHeight.toPx())
        .coerceAtMost(viewport)
    val thumbTop = (viewport - thumbHeight) * state.value / hidden
    drawRoundRect(trackColor, topLeft = Offset(left, 0f), size = Size(barWidth, viewport), cornerRadius = radius)
    drawRoundRect(thumbColor, topLeft = Offset(left, thumbTop), size = Size(barWidth, thumbHeight), cornerRadius = radius)
}
