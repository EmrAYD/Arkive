package com.eayd.arkive.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ai.SummaryDone
import com.eayd.arkive.ai.SummaryDoneMessage
import com.eayd.arkive.ui.theme.arkiveColors

/** Kartın ekranda kalma süresi (ms). Alttaki ince çizgi kalan süreyi gösterir. */
private const val SHOW_MILLIS = 9_000

/**
 * "Özetleme bitti" kartı: uygulamanın temasına uyan, ekranın altından kayarak gelen kısa bir bildirim. Kartın herhangi bir yerine
 * dokunmak özeti açar ([onOpen]); X ile ya da süre dolunca ([SHOW_MILLIS]) kapanır ([onDismiss]). [done] null ise kart yoktur.
 */
@Composable
fun SummaryDoneBanner(
    done: SummaryDone?,
    onOpen: (SummaryDone) -> Unit,
    onDismiss: (SummaryDone) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = done,
        modifier = modifier,
        contentKey = { it?.file?.id },
        transitionSpec = {
            (slideInVertically(tween(280)) { it } + fadeIn(tween(280))) togetherWith
                (slideOutVertically(tween(220)) { it } + fadeOut(tween(220)))
        },
        label = "summaryDoneBanner",
    ) { shown ->
        if (shown != null) BannerCard(shown, onOpen, onDismiss)
    }
}

@Composable
private fun BannerCard(done: SummaryDone, onOpen: (SummaryDone) -> Unit, onDismiss: (SummaryDone) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val remaining = remember(done) { Animatable(1f) }
    LaunchedEffect(done) {
        remaining.animateTo(0f, tween(SHOW_MILLIS, easing = LinearEasing))
        onDismiss(done)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.clip(shape).clickable { onOpen(done) }) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AiIcons.Filled, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.arkiveColors.success,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = SummaryDoneMessage.NOTIFICATION_TITLE,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                    }
                    Text(
                        text = SummaryDoneMessage.fileLine(done.file.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = SummaryDoneMessage.detail(done.savedOnPhoneOnly),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = SummaryDoneMessage.TAP_HINT,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                    )
                }
                IconButton(onClick = { onDismiss(done) }, modifier = Modifier.align(Alignment.Top).size(40.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Kapat",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { remaining.value },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = colors.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}
