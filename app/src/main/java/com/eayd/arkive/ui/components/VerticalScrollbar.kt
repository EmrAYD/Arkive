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

/**
 * Kaydırılabilir alanın sağ kenarına ince bir kaydırma çubuğu çizer: soluk bir ray ve üzerinde içeriğin görünen kısmını
 * gösteren tutamaç. İçerik alana sığıyorsa hiçbir şey çizilmez. Çubuk hep görünür (kaydırınca kaybolmaz): metnin
 * kaydırılabildiği ilk bakışta anlaşılsın diye.
 *
 * Compose'un `verticalScroll`'u kendiliğinden çubuk çizmez. Bu değiştirici `verticalScroll`'dan ÖNCE yazılmalıdır
 * (`Modifier.verticalScrollbar(state).verticalScroll(state)`): böylece görünen alanın ölçülerinde çizilir, içerikle
 * birlikte kaymaz. İçeriğe sağdan [width]'ten biraz fazla boşluk bırakılırsa yazı çubuğun altında kalmaz.
 */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    thumbColor: Color,
    trackColor: Color = thumbColor.copy(alpha = 0.15f),
    width: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
): Modifier = drawWithContent {
    drawContent()
    val hidden = state.maxValue
    // maxValue ilk ölçümden önce Int.MAX_VALUE'dur; 0 ise içerik sığıyordur.
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
