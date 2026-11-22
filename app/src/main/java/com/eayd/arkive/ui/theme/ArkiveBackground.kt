package com.eayd.arkive.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/** Giriş ekranlarının temaya uyan gradyan arka planı. */
@Composable
fun Modifier.arkiveGradientBackground(): Modifier {
    val colors = MaterialTheme.arkiveColors
    return this.background(
        brush = Brush.verticalGradient(
            colors = listOf(colors.gradientTop, colors.gradientBottom)
        )
    )
}