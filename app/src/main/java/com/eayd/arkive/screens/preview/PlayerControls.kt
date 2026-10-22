package com.eayd.arkive.screens.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlinx.coroutines.delay

// Video ve ses oynatıcılarının ortak parçaları.

/** Milisaniyeyi "dakika:saniye" olarak yazar (ör. 3:07). */
internal fun formatMillis(ms: Int): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/**
 * Oynatma sürerken ([playing]) ilerleme çubuğunu 4 kez/sn günceller: [current] oynatıcının şu anki konumunu
 * (alınamazsa null) verir, [onPosition] onu ekrana yazar. Kullanıcı çubuğu sürüklerken ([dragging]) güncellenmez.
 */
@Composable
internal fun TrackPosition(playing: Boolean, dragging: Boolean, current: () -> Int?, onPosition: (Int) -> Unit) {
    val isDragging by rememberUpdatedState(dragging)
    val read by rememberUpdatedState(current)
    LaunchedEffect(playing) {
        while (playing) {
            if (!isDragging) runCatching { read() }.getOrNull()?.let(onPosition)
            delay(250)
        }
    }
}

/**
 * İlerleme çubuğu ve altında geçen/toplam süre. [light] true ise koyu (video) zemin için beyaz çizilir.
 * [onChange] sürüklerken, [onFinished] parmak kalkınca çağrılır.
 */
@Composable
internal fun SeekBar(
    position: Int,
    duration: Int,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit,
    light: Boolean = false,
) {
    Slider(
        value = position.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        onValueChangeFinished = onFinished,
        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
        colors = if (light) {
            SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            )
        } else {
            SliderDefaults.colors()
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val color = if (light) Color.White else Color.Unspecified
        Text(formatMillis(position), style = MaterialTheme.typography.bodySmall, color = color)
        Text(formatMillis(duration), style = MaterialTheme.typography.bodySmall, color = color)
    }
}
