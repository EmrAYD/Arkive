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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ui.theme.arkiveColors

/**
 * Kısa bildirimlerin (işlem sonucu, hata) durumu. Eskiden Snackbar kullanılıyordu; yerini uygulamanın temasına uyan
 * [NoticeHost] kartı aldı. Bildirimler sırayla gösterilir ([NoticeQueue]).
 *
 * [show] bildirimi hemen kaydeder (askıya almaz). Kart ve süresi [NoticeHost]'a aittir; bu yüzden bildirimi gönderen
 * ViewModel durumunu hemen temizleyebilir, kullanıcı ekrandan çıksa bile bildirim ViewModel'de kalıp ekrana sonra
 * yeniden gelmez.
 */
@Stable
class NoticeHostState {
    private val queue = NoticeQueue()

    /** Şu an gösterilen bildirim; yoksa null. */
    var current: Notice? by mutableStateOf(null)
        private set

    /** [kind] verilmezse bildirimin türü metninden çıkarılır ([NoticeKind.classify]). */
    fun show(message: String, kind: NoticeKind? = null) {
        queue.add(message, kind)
        current = queue.current
    }

    fun dismiss(notice: Notice) {
        queue.dismiss(notice.id)
        current = queue.current
    }
}

@Composable
fun rememberNoticeHostState(): NoticeHostState = remember { NoticeHostState() }

/**
 * [state]'teki bildirimi alttan kayan, temaya uyumlu bir kart olarak gösterir. Karta dokunmak ya da X kapatır; süre
 * dolunca ([NoticeQueue.durationMillis]) kendiliğinden kapanır. Çağıran, bunu ekranın altına yerleştirir.
 */
@Composable
fun NoticeHost(state: NoticeHostState, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state.current,
        modifier = modifier,
        contentKey = { it?.text },
        transitionSpec = {
            (slideInVertically(tween(260)) { it } + fadeIn(tween(260))) togetherWith
                (slideOutVertically(tween(200)) { it } + fadeOut(tween(200)))
        },
        label = "noticeHost",
    ) { notice ->
        if (notice != null) {
            Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                NoticeCard(notice, onDismiss = { state.dismiss(notice) })
            }
        }
    }
}

/** Bildirim türüne göre kartın simgesi ve renkleri (hepsi temadan; açık/koyu temada uyumlu). */
private class KindStyle(val icon: ImageVector, val badge: Color, val tint: Color, val accent: Color)

@Composable
private fun styleOf(kind: NoticeKind): KindStyle {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        NoticeKind.Success -> {
            val success = MaterialTheme.arkiveColors.success
            KindStyle(Icons.Filled.CheckCircle, success.copy(alpha = 0.18f), success, success)
        }
        NoticeKind.Error -> KindStyle(Icons.Filled.Error, colors.errorContainer, colors.onErrorContainer, colors.error)
        NoticeKind.Warning -> KindStyle(Icons.Filled.Warning, colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
        NoticeKind.Info -> KindStyle(Icons.Filled.Info, colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary)
    }
}

@Composable
private fun NoticeCard(notice: Notice, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val style = styleOf(notice.kind)
    val shape = RoundedCornerShape(16.dp)
    val remaining = remember(notice) { Animatable(1f) }
    LaunchedEffect(notice) {
        remaining.animateTo(0f, tween(NoticeQueue.durationMillis(notice.text), easing = LinearEasing))
        onDismiss()
    }

    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = shape,
        color = colors.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, if (notice.kind == NoticeKind.Info) colors.outlineVariant else style.accent.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.clip(shape).clickable(onClick = onDismiss)) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(style.badge),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(style.icon, contentDescription = null, tint = style.tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = notice.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            LinearProgressIndicator(
                progress = { remaining.value },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = style.accent,
                trackColor = Color.Transparent,
            )
        }
    }
}
