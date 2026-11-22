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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
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
import com.eayd.arkive.ai.LocalModel
import com.eayd.arkive.ai.LocalModelState
import com.eayd.arkive.data.PlusPlan
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.screens.home.ProfileAvatar
import com.eayd.arkive.ui.components.AiIcons
import com.eayd.arkive.ui.components.StorageSkeleton
import com.eayd.arkive.utils.formatBytes
import com.eayd.arkive.viewmodel.StorageInfo

/** Sayfanın başındaki profil simgesi ve e-posta. */
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

/** Başlıklı kart. */
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

/** Kart içindeki satır. [onClick] null ise yalnızca bilgi gösterir. */
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

/** Hesap bölümü. Google ile girenlerin şifresi Google'da olduğu için şifre satırı pasiftir. */
@Composable
internal fun AccountSection(email: String?, hasPassword: Boolean, onChangePassword: () -> Unit) {
    SectionCard(Txt.profile.account) {
        ProfileRow(Icons.Filled.Email, title = Txt.auth.email, subtitle = email ?: "—")
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Lock,
            title = Txt.profile.changePassword,
            subtitle = if (hasPassword) null else Txt.profile.googleManagedPassword,
            onClick = onChangePassword,
            enabled = hasPassword,
            trailing = { if (hasPassword) Chevron() },
        )
    }
}

/** Ayarlar bölümü. */
@Composable
internal fun SettingsSection(
    themeMode: ThemeMode,
    wifiOnly: Boolean,
    appLanguage: AppLanguage,
    summaryLanguage: AppLanguage,
    /** Geçerli işlem yeri; ücretsiz planda her zaman Yerel. */
    aiMode: AiMode,
    trashRetentionDays: Int?,
    onNotifications: () -> Unit,
    onTheme: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onLanguage: () -> Unit,
    onAiMode: () -> Unit,
    onTrashRetention: () -> Unit,
    /** Modelin durumu; null ise satır gösterilmez. */
    localModel: LocalModelState? = null,
    onLocalModel: () -> Unit = {},
) {
    SectionCard(Txt.profile.settings) {
        ProfileRow(
            icon = Icons.Filled.Notifications,
            title = Txt.profile.notificationSettings,
            subtitle = null,
            onClick = onNotifications,
            trailing = { Chevron() },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Language,
            title = Txt.language.title,
            subtitle = if (appLanguage == summaryLanguage) {
                appLanguage.nativeName
            } else {
                Txt.language.mixed(appLanguage.nativeName, summaryLanguage.nativeName)
            },
            onClick = onLanguage,
            trailing = { Chevron() },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Palette,
            title = Txt.profile.theme,
            subtitle = themeMode.label,
            onClick = onTheme,
            trailing = { Chevron() },
        )
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Wifi,
            title = Txt.profile.wifiOnly,
            subtitle = Txt.profile.wifiOnlyDetail,
            onClick = { onWifiOnlyChange(!wifiOnly) },
            trailing = { Switch(checked = wifiOnly, onCheckedChange = null) },
        )
        RowDivider()
        ProfileRow(
            icon = AiIcons.Filled,
            title = Txt.aiMode.title,
            subtitle = aiMode.label,
            onClick = onAiMode,
            trailing = { Chevron() },
        )
        if (localModel != null) {
            RowDivider()
            ProfileRow(
                icon = Icons.Filled.PhoneAndroid,
                title = Txt.localAi.title,
                subtitle = localModelSubtitle(localModel),
                onClick = onLocalModel,
                trailing = { Chevron() },
            )
        }
        RowDivider()
        ProfileRow(
            icon = Icons.Filled.Delete,
            title = Txt.profile.trashRetention,
            subtitle = trashRetentionDays?.let { Txt.profile.days(it) } ?: Txt.common.loadingEllipsis,
            onClick = onTrashRetention,
            enabled = trashRetentionDays != null,
            trailing = { Chevron() },
        )
    }
}

private fun localModelSubtitle(state: LocalModelState): String = when (state) {
    LocalModelState.NotDownloaded -> Txt.localAi.notDownloaded(LocalModel.sizeText)
    is LocalModelState.Downloading -> when {
        state.waitingForNetwork -> Txt.localAi.waitingShort
        else -> state.percent?.let { Txt.localAi.downloadingShort(it) } ?: Txt.localAi.waitingShort
    }
    LocalModelState.Ready -> Txt.localAi.ready(LocalModel.sizeText)
    LocalModelState.Unsupported -> Txt.localAi.unsupportedShort
}

/** Plan bölümü: plan adı, sınırlar ve kullanım. */
@Composable
internal fun PlanSection(storage: StorageInfo?, isLoading: Boolean, error: String?, onRetry: () -> Unit) {
    SectionCard(Txt.profile.myPlan) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                storage != null -> PlanDetails(storage)

                isLoading -> StorageSkeleton()

                else -> {
                    Text(
                        text = error ?: Txt.profile.planUnavailable,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(Txt.common.retry) }
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
        Text(Txt.profile.planName(storage.planName), style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(12.dp))
    LimitRow(Txt.profile.maxFileLimit, formatBytes(storage.maxFileSize))
    LimitRow(Txt.profile.totalStorage, formatBytes(storage.limit))
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { storage.fraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        drawStopIndicator = {},
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = Txt.profile.usedOf(formatBytes(storage.used), formatBytes(storage.limit)),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = Txt.profile.remaining(formatBytes(storage.remaining)),
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

/** Ücretsiz plandaki kullanıcıya gösterilen Plus tanıtımı. */
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
                Icon(AiIcons.Filled, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(Txt.profile.morePlus, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            PlusBenefit(Txt.profile.plusPerFile(formatBytes(storage.maxFileSize)))
            PlusBenefit(Txt.profile.plusStorage(formatBytes(storage.limit)))
            PlusBenefit(Txt.profile.plusAi(PlusPlan.FREE_SUMMARY_LIMIT))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text(Txt.plan.goPlus) }
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
