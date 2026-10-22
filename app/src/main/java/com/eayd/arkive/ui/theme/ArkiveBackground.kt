package com.eayd.arkive.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * Splash, Login ve Register ekranlarının ortak, TEMA DUYARLI gradient arka planı.
 * Açık temada gökyüzü mavisi, koyu temada gece laciverti. Renkler Theme.kt'deki
 * ArkiveExtraColors'tan gelir.
 *
 * Gradient'i ekranın tamamına (status bar ve navigation bar arkası dahil) çizer.
 * İçeriğin sistem çubuklarının altında kalmaması için bu modifier'dan SONRA
 * safeDrawingPadding() kullanın.
 */
@Composable
fun Modifier.arkiveGradientBackground(): Modifier {
    val colors = MaterialTheme.arkiveColors
    return this.background(
        brush = Brush.verticalGradient(
            colors = listOf(colors.gradientTop, colors.gradientBottom)
        )
    )
}