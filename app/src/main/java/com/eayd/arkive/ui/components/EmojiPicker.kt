package com.eayd.arkive.ui.components

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Klasör için emoji seçimi: pencerede sık kullanılan birkaç emoji + "+" tuşu; "+" tam listeyi açar.

/** Hızlı seçim satırındaki bir kutu. */
private sealed interface EmojiSlot {
    data object None : EmojiSlot
    data class Emoji(val value: String) : EmojiSlot
    data object More : EmojiSlot
}

private const val SLOTS_PER_ROW = 6

/**
 * Emoji seçimi satırları: "yok" kutusu, temel emojiler ve "+" (tüm emojiler). [current], temel listede olmayan
 * (tam listeden seçilmiş) bir emojiyse hızlı satırda da görünür. Seçili emojiye tekrar basmak seçimi kaldırır.
 * [onSelect]: seçilen emoji; null = emoji yok.
 */
@Composable
fun EmojiChoices(current: String?, enabled: Boolean, onSelect: (String?) -> Unit) {
    var showAll by remember { mutableStateOf(false) }

    val extra = current?.takeIf { it !in BasicFolderEmojis }
    val slots: List<EmojiSlot> = buildList {
        add(EmojiSlot.None)
        extra?.let { add(EmojiSlot.Emoji(it)) }
        BasicFolderEmojis.take(if (extra != null) BasicFolderEmojis.size - 1 else BasicFolderEmojis.size)
            .forEach { add(EmojiSlot.Emoji(it)) }
        add(EmojiSlot.More)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(SLOTS_PER_ROW).forEach { rowSlots ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowSlots.forEach { slot ->
                    when (slot) {
                        EmojiSlot.None -> EmojiBox(
                            selected = current == null,
                            enabled = enabled,
                            description = "Emojisiz",
                            onClick = { onSelect(null) },
                        ) { Text("–", style = MaterialTheme.typography.labelMedium) }

                        is EmojiSlot.Emoji -> EmojiBox(
                            selected = slot.value == current,
                            enabled = enabled,
                            description = "Emoji ${slot.value}",
                            onClick = { onSelect(if (slot.value == current) null else slot.value) },
                        ) { Text(slot.value, fontSize = 20.sp) }

                        EmojiSlot.More -> EmojiBox(
                            selected = false,
                            enabled = enabled,
                            description = "Tüm emojiler",
                            onClick = { showAll = true },
                        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }

    if (showAll) {
        EmojiPickerDialog(
            onPick = {
                showAll = false
                onSelect(it)
            },
            onDismiss = { showAll = false },
        )
    }
}

@Composable
private fun EmojiBox(
    selected: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Tüm emojilerin bölümlere ayrılmış listesi (tam ekrana yakın pencere). Bir emojiye basınca [onPick] çağrılır.
 * Cihazın yazı tipinin çizemediği (daha yeni sürümlerin) emojiler gösterilmez; yoksa kutu kutu görünürlerdi.
 */
@Composable
private fun EmojiPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.78f),
        ) {
            var category by rememberSaveable { mutableStateOf(0) }
            val paint = remember { Paint() }
            // Her bölüm ilk açıldığında bir kez süzülür (yüzlerce hasGlyph çağrısı, anlık).
            val emojis = remember(category) { EmojiCategories[category].emojis.filter { paint.hasGlyph(it) } }

            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Emoji seç", style = MaterialTheme.typography.titleLarge)
                        Text(
                            EmojiCategories[category].label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Kapat") }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(EmojiCategories) { index, item ->
                        val isSelected = index == category
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                .clickable(role = Role.Tab) { category = index }
                                .semantics {
                                    contentDescription = item.label
                                    this.selected = isSelected
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(item.icon, fontSize = 22.sp) }
                    }
                }
                HorizontalDivider()

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 46.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(emojis) { value ->
                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { onPick(value) }
                                .semantics { contentDescription = "Emoji $value" },
                            contentAlignment = Alignment.Center,
                        ) { Text(value, fontSize = 26.sp) }
                    }
                }
            }
        }
    }
}
