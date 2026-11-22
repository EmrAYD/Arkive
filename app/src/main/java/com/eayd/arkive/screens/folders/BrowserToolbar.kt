package com.eayd.arkive.screens.folders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.viewmodel.CategoryOption
import com.eayd.arkive.viewmodel.SortOption

/** Listenin üstündeki işlem satırı: solda filtreler, sağda "+" düğmesi. */
@Composable
fun FolderActionRow(
    canUpload: Boolean,
    onNewFolder: () -> Unit,
    onUploadFiles: () -> Unit,
    modifier: Modifier = Modifier,
    showAdd: Boolean = true,
    filters: @Composable RowScope.() -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = filters,
        )
        if (showAdd) {
            Box {
                FilledIconButton(
                    onClick = { if (canUpload) menuOpen = true else onNewFolder() },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = if (canUpload) Txt.browser.add else Txt.browser.newFolder,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(Txt.browser.newFolder) },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = { menuOpen = false; onNewFolder() },
                    )
                    DropdownMenuItem(
                        text = { Text(Txt.browser.uploadFile) },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                        onClick = { menuOpen = false; onUploadFiles() },
                    )
                }
            }
        }
    }
}

/** Filtre düğmesi. [active] true ise çerçeve tema renginde olur. */
@Composable
fun FilterPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    active: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = CircleShape,
        border = BorderStroke(
            width = if (active) 1.5.dp else 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "Sırala" ve "Kategori" filtreleri; liste telefonda süzülür. */
@Composable
fun BrowserFilters(
    sort: SortOption,
    category: CategoryOption,
    onSort: (SortOption) -> Unit,
    onCategory: (CategoryOption) -> Unit,
) {
    OptionPill(
        label = Txt.browser.sort,
        leadingIcon = Icons.Filled.SwapVert,
        options = SortOption.entries,
        selected = sort,
        optionLabel = { it.label },
        onSelect = onSort,
    )
    OptionPill(
        label = if (category == CategoryOption.All) Txt.browser.category else category.label,
        trailingIcon = Icons.Filled.KeyboardArrowDown,
        active = category != CategoryOption.All,
        options = CategoryOption.entries,
        selected = category,
        optionLabel = { it.label },
        onSelect = onCategory,
    )
}

/** Basınca seçenek menüsü açan filtre düğmesi. */
@Composable
private fun <T> OptionPill(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    active: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        FilterPill(
            label = label,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            active = active,
            onClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    trailingIcon = {
                        if (option == selected) Icon(Icons.Filled.Check, contentDescription = Txt.common.selected)
                    },
                    onClick = { onSelect(option); menuOpen = false },
                )
            }
        }
    }
}

/** Seçim modunda işlem satırının yerine gelen çubuk. */
@Composable
fun SelectionBar(
    count: Int,
    allFavorite: Boolean,
    canMove: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = Txt.browser.closeSelection) }
        Text(
            text = Txt.browser.selectedCount(count),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = Txt.browser.selectAll) }
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (allFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (allFavorite) Txt.common.removeFromFavorites else Txt.common.addToFavorites,
            )
        }
        IconButton(onClick = onMove, enabled = canMove) {
            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = Txt.common.move)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = Txt.common.delete, tint = MaterialTheme.colorScheme.error)
        }
    }
}
