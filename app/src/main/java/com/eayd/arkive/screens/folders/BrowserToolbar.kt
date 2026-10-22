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
import com.eayd.arkive.viewmodel.CategoryOption
import com.eayd.arkive.viewmodel.SortOption

/**
 * Üst çubuğun hemen altındaki işlem satırı: solda filtre seçenekleri ([filters] yuvası), sağda dolgulu "+" düğmesi.
 *
 * Satırın kenar boşlukları (16 dp) altındaki liste kartlarınınkiyle aynıdır; böylece filtreler listenin sol
 * kenarıyla, "+" düğmesi sağ kenarıyla hizalanır.
 *
 * "+" düz bir ikon yerine [FilledIconButton] (dolgulu, yuvarlatılmış kare) olarak çizilir; böylece
 * arka planda silik kalmaz ve dokunulabilir bir düğme olduğu belli olur.
 *  - Dosya yüklenebilen bir yerdeyken ([canUpload]) basınca küçük bir menü açılır: "Yeni klasör" / "Dosya yükle".
 *  - Yüklenemeyen yerde (ör. AI sonuçları) menü gereksizdir, doğrudan "yeni klasör" açılır.
 *
 * [filters] bir "slot"tur: çağıran yer `filters = { ... }` ile istediği bileşenleri (ör. [FilterPill]) verir,
 * bu dosyayı değiştirmek gerekmez.
 */
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
        // end = 12 dp: "+" düğmesinin kendi içindeki 4 dp'lik boşlukla birlikte görünür kenarı 16 dp'ye denk gelir.
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
                        contentDescription = if (canUpload) "Ekle" else "Yeni klasör",
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Yeni klasör") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = { menuOpen = false; onNewFolder() },
                    )
                    DropdownMenuItem(
                        text = { Text("Dosya yükle") },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                        onClick = { menuOpen = false; onUploadFiles() },
                    )
                }
            }
        }
    }
}

/**
 * Filtre düğmesi: ince çerçeveli, tam yuvarlak kenarlı "hap" ("Sırala", "Kategori ⌄" gibi).
 * Simgeler tema renginde, yazı normal metin renginde çizilir. [active] true ise (bir filtre seçiliyse)
 * çerçeve tema renginde olur; böylece filtrenin açık olduğu görülür.
 */
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

/**
 * "Sırala" ve "Kategori" filtre düğmeleri (işlem satırının solunda). Seçim istemcide, ekrandaki liste üzerinde
 * uygulanır (sunucuya yeni istek gitmez); durumu ViewModel tutar.
 */
@Composable
fun BrowserFilters(
    sort: SortOption,
    category: CategoryOption,
    onSort: (SortOption) -> Unit,
    onCategory: (CategoryOption) -> Unit,
) {
    OptionPill(
        label = "Sırala",
        leadingIcon = Icons.Filled.SwapVert,
        options = SortOption.entries,
        selected = sort,
        optionLabel = { it.label },
        onSelect = onSort,
    )
    OptionPill(
        // Bir kategori seçiliyse düğme onun adını gösterir ve çerçevesi renklenir.
        label = if (category == CategoryOption.All) "Kategori" else category.label,
        trailingIcon = Icons.Filled.KeyboardArrowDown,
        active = category != CategoryOption.All,
        options = CategoryOption.entries,
        selected = category,
        optionLabel = { it.label },
        onSelect = onCategory,
    )
}

/** Basınca seçenek menüsü açan [FilterPill]; geçerli seçenekte tik görünür. */
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
                        if (option == selected) Icon(Icons.Filled.Check, contentDescription = "Seçili")
                    },
                    onClick = { onSelect(option); menuOpen = false },
                )
            }
        }
    }
}

/** Seçim modunda işlem satırının yerini alan çubuk: seçili sayısı ve toplu işlemler. */
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
        // 48 dp: normal işlem satırıyla aynı yükseklik; seçime geçince liste yerinden oynamasın.
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Seçimi kapat") }
        Text(
            text = "$count seçildi",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = "Tümünü seç") }
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (allFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (allFavorite) "Favorilerden çıkar" else "Favorilere ekle",
            )
        }
        IconButton(onClick = onMove, enabled = canMove) {
            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Taşı")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
        }
    }
}
