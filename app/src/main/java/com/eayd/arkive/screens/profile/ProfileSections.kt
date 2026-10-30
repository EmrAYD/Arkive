package com.eayd.arkive.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.ai.SummaryTemplate
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.screens.home.ProfileAvatar
import com.eayd.arkive.ui.components.StorageSkeleton
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.viewmodel.StorageInfo

// Profil ekranının bölümleri. Bu dosyadaki bileşenler yalnızca verilen değerleri çizer; durum ProfileViewModel'dedir.


/** Sayfanın başındaki büyük profil simgesi ve e-posta. */
@Composable
internal fun ProfileHeader(email: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(email.orEmpty(), size = 84.dp)
        if (!email.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Başlıklı, yuvarlak köşeli kart; satırlar [content] içinde alt alta dizilir. */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ) {
            Column(content = content)
        }
    }
}

/**
 * Kart içindeki tek satır: solda simge, ortada başlık + açıklama, sağda isteğe bağlı [trailing].
 * [onClick] null ise satır yalnızca bilgi gösterir; [enabled] false ise soluk çizilir ve dokunulamaz.
 */
@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
    )
}

/** "Hesap": e-posta ve şifre değiştirme. Google ile girenlerin şifresi Google'da olduğu için o satır pasiftir. */
@Composable
internal fun AccountSection(email: String?, hasPassword: Boolean, onChangePassword: () -> Unit) {
    SectionCard("Hesap") {
        ProfileRow(Icons.Filled.Email, title = "E-posta", subtitle = email ?: "—")
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Lock,
            title = "Şifreyi değiştir",
            subtitle = if (hasPassword) null else "Google hesabıyla giriş yaptınız; şifreniz Google'da yönetilir",
            onClick = onChangePassword,
            enabled = hasPassword,
            trailing = { if (hasPassword) Chevron() },
        )
    }
}

/**
 * "Ayarlar": bildirimler (sistem ayarlarını açar), tema, yalnızca Wi-Fi, yapay zeka işlem yeri,
 * çöp kutusu saklama süresi. Tema, Wi-Fi ve yapay zeka ayarları bu cihazda, saklama süresi sunucuda tutulur; süre henüz yüklenmediyse ([trashRetentionDays] null)
 * o satır pasiftir.
 */
@Composable
internal fun SettingsSection(
    themeMode: ThemeMode,
    wifiOnly: Boolean,
    aiMode: AiMode,
    summaryTemplate: SummaryTemplate,
    trashRetentionDays: Int?,
    onNotifications: () -> Unit,
    onTheme: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAiMode: () -> Unit,
    onSummaryTemplate: () -> Unit,
    onTrashRetention: () -> Unit,
) {
    SectionCard("Ayarlar") {
        ProfileRow(
            icon = Icons.Filled.Notifications,
            title = "Bildirim ayarları",
            subtitle = null,
            onClick = onNotifications,
            trailing = { Chevron() },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Palette,
            title = "Tema",
            subtitle = themeMode.label,
            onClick = onTheme,
            trailing = { Chevron() },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Wifi,
            title = "Yalnızca Wi-Fi ile dosya aktar",
            subtitle = "Açıkken yükleme ve indirme yalnızca kablosuz ağda yapılır",
            onClick = { onWifiOnlyChange(!wifiOnly) },
            // Satırın tamamı dokunulabilir olduğu için anahtarın kendi dokunuşu kapalıdır (çift işlem olmasın).
            trailing = { Switch(checked = wifiOnly, onCheckedChange = null) },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.AutoAwesome,
            title = "Yapay zeka işlem yeri",
            subtitle = aiMode.label,
            onClick = onAiMode,
            trailing = { Chevron() },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Description,
            title = "Özet şablonu",
            subtitle = summaryTemplate.label,
            onClick = onSummaryTemplate,
            trailing = { Chevron() },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Delete,
            title = "Çöp kutusu saklama süresi",
            subtitle = trashRetentionDays?.let { "$it gün" } ?: "Yükleniyor…",
            onClick = onTrashRetention,
            enabled = trashRetentionDays != null,
            trailing = { Chevron() },
        )
    }
}

/** "Planım": plan adı, tek dosya ve toplam yükleme limiti, kullanım çubuğu. */
@Composable
internal fun PlanSection(storage: StorageInfo?, isLoading: Boolean, error: String?, onRetry: () -> Unit) {
    SectionCard("Planım") {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                storage != null -> PlanDetails(storage)

                isLoading -> StorageSkeleton()

                else -> {
                    Text(
                        text = error ?: "Plan bilgisi alınamadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Tekrar dene") }
                }
            }
        }
    }
}

@Composable
private fun PlanDetails(storage: StorageInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text("${storage.planName} planı", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(12.dp))
    LimitRow("Tek dosya yükleme limiti", formatBytes(storage.maxFileSize))
    LimitRow("Toplam depolama alanı", formatBytes(storage.limit))
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { storage.fraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "${formatBytes(storage.used)} / ${formatBytes(storage.limit)} kullanıldı",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "Kalan: ${formatBytes(storage.remaining)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun LimitRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Yalnızca ücretsiz plandaki kullanıcıya gösterilen Plus tanıtımı: avantajlar ve "Plus'a geç" düğmesi.
 * Düğme şimdilik bir şey yapmaz; ödeme ekranı eklenince [onUpgrade] oraya bağlanacak.
 */
@Composable
internal fun PlusPromo(storage: StorageInfo, onUpgrade: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Plus ile daha fazlası", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            PlusBenefit("Dosya başına ??GB yükleme (şu an ${formatBytes(storage.maxFileSize)})")
            PlusBenefit("??GB toplam depolama alanı (şu an ${formatBytes(storage.limit)})")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text("Plus'a geç") }
        }
    }
}

@Composable
private fun PlusBenefit(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
