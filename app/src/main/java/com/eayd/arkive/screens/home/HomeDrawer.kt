package com.eayd.arkive.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eayd.arkive.R
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.viewmodel.StorageInfo

/**
 * Yan menüden seçilen bölümler. [location] ER diyagramındaki Folders.Location değeridir; yalnızca
 * klasör ağacı gösteren bölümlerde vardır (Favorilerim ve çöp kutusunda yok).
 */
internal enum class HomeSection(
    val title: String,
    val icon: ImageVector,
    val location: String?,
) {
    Files("Ana Sayfa", Icons.Filled.Home, FolderLocations.HOME),
    Favorites("Favorilerim", Icons.Filled.Star, null),
    AiResults("AI Sonuçlarım", Icons.Filled.AutoAwesome, FolderLocations.AI_RESULTS),
    Trash("Çöp kutusu", Icons.Filled.Delete, null),
}

@Composable
internal fun ArkiveDrawerSheet(
    selected: HomeSection,
    storage: StorageInfo?,
    storageLoading: Boolean,
    storageError: String?,
    onRetryStorage: () -> Unit,
    onSelect: (HomeSection) -> Unit,
) {
    ModalDrawerSheet {
        // Küçük ekranlarda ya da yatay modda içerik sığmazsa kaydırılabilsin.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
            // Başlık: solda logo, yanında uygulama adı; altında ayırıcı çizgi.
            Row(
                modifier = Modifier.padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Arkive Logosu",
                    modifier = Modifier.size(36.dp),
                    tint = Color.Unspecified, // logo kendi renklerinde kalsın
                )
                Spacer(Modifier.width(12.dp))
                Text(text = "Arkive", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 16.dp))

            HomeSection.entries.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.title) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))

            StorageSection(storage, storageLoading, storageError, onRetryStorage)
        }
    }
}

/**
 * "Depolama alanı" bölümü: bulut simgesi + başlık, kalın kullanım çubuğu, "X toplam depolama alanından Y kadarı
 * kullanılıyor" metni ve "Daha fazla depolama alanı alın" düğmesi (şimdilik bir şey yapmaz; ödeme/plan
 * ekranı eklenince buraya bağlanacak).
 */
@Composable
private fun StorageSection(
    storage: StorageInfo?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cloud, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text("Depolama alanı", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))

        when {
            storage != null -> {
                LinearProgressIndicator(
                    progress = { storage.fraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${formatBytes(storage.limit)} toplam depolama alanından " +
                        "${formatBytes(storage.used)} kadarı kullanılıyor",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Kalan: ${formatBytes(storage.remaining)}  ·  Plan: ${storage.planName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (error != null) {
                    Text(
                        text = "Güncellenemedi: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { /* Şimdilik bir şey yapmaz. */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "Daha fazla depolama alanı alın",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            isLoading -> Text("Yükleniyor…", style = MaterialTheme.typography.bodyMedium)

            else -> {
                Text(
                    text = error ?: "Kullanım bilgisi alınamadı.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text("Tekrar dene") }
            }
        }
    }
}

/** Adın ilk harfini gösteren yuvarlak simge (üst çubuktaki profil düğmesi ve profil sayfasının başlığı). */
@Composable
internal fun ProfileAvatar(name: String, size: Dp = 42.dp) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = "Profil" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Üst çubuktaki profil düğmesi; basınca profil sayfası açılır. */
@Composable
internal fun ProfileButton(userName: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { ProfileAvatar(userName) }
}
