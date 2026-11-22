package com.eayd.arkive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.i18n.Txt

/** Klasör, dosya ve çöp kutusu satırlarının ortak kartı. [selectMark] null değilse solda seçim dairesi çizilir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selectMark: Boolean? = null,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    /** false ise satır soluk görünür ve tıklanmaz. */
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.35f else if (!enabled) 0.5f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                highlighted -> MaterialTheme.colorScheme.primaryContainer
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        border = if (highlighted || selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectMark != null) {
                SelectCircle(checked = selectMark)
                Spacer(Modifier.width(12.dp))
            }
            icon()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
}

/**
 * Satırın sağındaki düğmeler: yapay zeka, favori ve menü. Özet hazırlanırken yapay zeka düğmesinin yerinde yükleme
 * simgesi döner.
 */
@Composable
fun RowScope.RowActions(
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    ai: Boolean? = null,
    aiWorking: Boolean = false,
    onToggleAi: () -> Unit = {},
    menuEnabled: Boolean = true,
    menu: @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    if (ai != null) {
        IconButton(onClick = onToggleAi, modifier = Modifier.size(width = 36.dp, height = 48.dp)) {
            if (aiWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).semantics { contentDescription = Txt.ai.summarizing },
                    strokeWidth = 2.5.dp,
                )
            } else {
                Icon(
                    imageVector = if (ai) AiIcons.Filled else AiIcons.Outlined,
                    contentDescription = if (ai) Txt.ai.showSummaryA11y else Txt.ai.summarizeA11y,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    IconButton(onClick = onToggleFavorite) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (favorite) Txt.common.removeFromFavorites else Txt.common.addToFavorites,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Box {
        IconButton(onClick = { menuOpen = true }, enabled = menuEnabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = Txt.common.more)
        }
        menu(menuOpen) { menuOpen = false }
    }
}

/** Seçim dairesi. */
@Composable
fun SelectCircle(checked: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(primary)
                else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            .semantics { contentDescription = if (checked) Txt.common.selected else Txt.common.notSelected },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
