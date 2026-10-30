package com.eayd.arkive.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Yüklenen içeriğin yerinde gösterilen iskelet (skeleton) görünümler: dönen çark yerine, içeriğin şeklini taklit eden
// ve üzerinden ışık geçen gri bloklar.

/** Arka planı soldan sağa kayan bir ışık şeridiyle dolar. Işığın konumu yalnızca çizim aşamasında okunur. */
@Composable
private fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val shift by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "skeletonShift",
    )
    return drawBehind {
        val band = size.width.coerceAtLeast(1f)
        val start = -band + 2 * band * shift
        drawRect(Brush.horizontalGradient(listOf(base, highlight, base), startX = start, endX = start + band))
    }
}

/** Tek bir iskelet bloğu (metin çubuğu, simge, görsel alanı…). Boyutunu çağıran verir. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.clip(shape).shimmer())
}

/** Liste yüklenirken: gerçek satırlarla ([ItemRow]) aynı kart biçiminde, simge + iki satır metin görünümlü satırlar. */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 10,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
) {
    Column(
        modifier = modifier
            .clipToBounds()
            .padding(contentPadding)
            .semantics { contentDescription = "Yükleniyor" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rows) { index -> SkeletonRow(titleWidth = 0.4f + 0.15f * (index % 3)) }
    }
}

@Composable
private fun SkeletonRow(titleWidth: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(36.dp), RoundedCornerShape(8.dp))
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.fillMaxWidth(titleWidth).height(14.dp))
                SkeletonBox(Modifier.fillMaxWidth(titleWidth * 0.5f).height(10.dp))
            }
        }
    }
}

/** Depolama kullanımı (çubuk + iki satır metin) yüklenirken. */
@Composable
fun StorageSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Yükleniyor" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(8.dp), RoundedCornerShape(4.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.85f).height(14.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
    }
}
