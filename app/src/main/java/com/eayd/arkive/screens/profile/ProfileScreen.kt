package com.eayd.arkive.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.utils.openNotificationSettings
import com.eayd.arkive.viewmodel.ProfileDialog
import com.eayd.arkive.viewmodel.ProfileUiState
import com.eayd.arkive.viewmodel.ProfileViewModel

/**
 * Profil sayfası (ana ekrandaki profil düğmesinden açılır, sağdan kayarak gelir): hesap bilgileri ve şifre değiştirme,
 * ayarlar, mevcut plan ve limitler, ücretsiz plandaysa Plus tanıtımı; en altta kırmızı "Çıkış Yap" düğmesi.
 *
 * Bu bileşen yalnızca çizer; veriler ve işlemler [ProfileViewModel] içindedir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    NoticeEffect(state.notice, snackbar, viewModel::clearNotice)

    // Sayfa her açıldığında plan bilgisi tazelenir (ViewModel sayfa kapanınca da yaşar).
    LaunchedEffect(viewModel) { viewModel.load() }

    // Kendi arka planı var: sayfa ana ekranın üstüne kayarak gelirken alttaki ekran görünmesin.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TopAppBar(
            title = { Text("Profil") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            // Sistem çubuğu boşluğunu yukarıdaki safeDrawingPadding zaten ayırdı; ikinci kez eklenmesin.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ProfileHeader(state.name, state.email)

                AccountSection(
                    email = state.email,
                    hasPassword = state.hasPassword,
                    onChangePassword = { viewModel.openDialog(ProfileDialog.ChangePassword) },
                )

                SettingsSection(
                    themeMode = themeMode,
                    wifiOnly = wifiOnly,
                    // Değer sunucudan gelene kadar satır pasif kalır.
                    trashRetentionDays = if (state.storage != null) state.trashRetentionDays else null,
                    onNotifications = {
                        if (!context.openNotificationSettings()) viewModel.showNotice("Bildirim ayarları açılamadı.")
                    },
                    onTheme = { viewModel.openDialog(ProfileDialog.Theme) },
                    onWifiOnlyChange = viewModel::setWifiOnly,
                    onTrashRetention = { viewModel.openDialog(ProfileDialog.TrashRetention) },
                )

                PlanSection(state.storage, state.isLoading, state.error, viewModel::load)

                // Plus'taki kullanıcı yalnızca plan bilgisini görür; tanıtım ve düğme ücretsiz plana özel.
                state.storage?.takeIf { it.isFree }?.let { storage ->
                    PlusPromo(storage, onUpgrade = { /* Şimdilik bir şey yapmaz. */ })
                }

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Çıkış Yap")
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }

    ProfileDialogs(viewModel, state)
}

/** Açık/kapalı durumu ve işlemler ViewModel'dedir ([ProfileViewModel.dialogs]). */
@Composable
private fun ProfileDialogs(viewModel: ProfileViewModel, state: ProfileUiState) {
    val host by viewModel.dialogs.state.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val close = viewModel.dialogs::close

    when (host.current) {
        null -> Unit

        ProfileDialog.ChangePassword -> ChangePasswordDialog(
            saving = host.saving,
            error = host.error,
            onConfirm = viewModel::changePassword,
            onDismiss = close,
        )

        ProfileDialog.Theme -> ChoiceDialog(
            title = "Tema",
            description = null,
            options = ThemeMode.entries,
            current = themeMode,
            optionLabel = { it.label },
            saving = false,
            error = null,
            onSave = viewModel::setThemeMode,
            onDismiss = close,
        )

        ProfileDialog.TrashRetention -> ChoiceDialog(
            title = "Çöp kutusu saklama süresi",
            description = "Silinen öğeler çöp kutusunda bu kadar gün kalır, sonra kalıcı olarak silinir. " +
                "Süreyi kısaltırsanız, yeni süreyi aşan öğeler de silinir.",
            // Sunucudaki değer listede yoksa (başka yoldan ayarlanmışsa) o da seçenek olarak gösterilir.
            options = (RetentionChoices + state.trashRetentionDays).distinct().sorted(),
            current = state.trashRetentionDays,
            optionLabel = { "$it gün" },
            saving = host.saving,
            error = host.error,
            onSave = viewModel::saveTrashRetention,
            onDismiss = close,
        )
    }
}

/** Çöp kutusu için sık kullanılan süreler (gün). Sunucu 1-365 arasını kabul eder. */
private val RetentionChoices = listOf(7, 14, 30, 60, 90)

/** Radyo düğmeli tek seçimli pencere (tema ve saklama süresi için ortak). */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    description: String?,
    options: List<T>,
    current: T,
    optionLabel: (T) -> String,
    saving: Boolean,
    error: String?,
    onSave: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == option,
                                enabled = !saving,
                                role = Role.RadioButton,
                                onClick = { selected = option },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == option, onClick = null, enabled = !saving)
                        Spacer(Modifier.width(12.dp))
                        Text(optionLabel(option))
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = { onSave(selected) }) { Text("Kaydet") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Vazgeç") } },
    )
}
