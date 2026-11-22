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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.data.PlanPolicy
import com.eayd.arkive.data.PlanStatus
import com.eayd.arkive.data.PlusOffer
import com.eayd.arkive.data.ThemeMode
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.components.LocalModelDialog
import com.eayd.arkive.ui.components.NoticeEffect
import com.eayd.arkive.ui.components.NoticeHost
import com.eayd.arkive.ui.components.rememberNoticeHostState
import com.eayd.arkive.utils.openNotificationSettings
import com.eayd.arkive.viewmodel.ProfileDialog
import com.eayd.arkive.viewmodel.ProfileUiState
import com.eayd.arkive.viewmodel.ProfileViewModel

/** Profil sayfası: hesap, ayarlar, plan ve çıkış. */
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
    val localModel by viewModel.localModel.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
    val cloudAllowed = PlanStatus.quota.collectAsState().value?.cloud == true
    val appLanguage by viewModel.appLanguage.collectAsState()
    val summaryLanguage by viewModel.summaryLanguage.collectAsState()
    val context = LocalContext.current
    val notices = rememberNoticeHostState()
    NoticeEffect(state.notice, notices, viewModel::clearNotice)

    LaunchedEffect(viewModel) { viewModel.load() }
    val planName = PlanStatus.quota.collectAsState().value?.planName
    LaunchedEffect(planName) {
        val shown = state.storage?.planName
        if (planName != null && shown != null && planName != shown) viewModel.load()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TopAppBar(
            title = { Text(Txt.profile.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Txt.common.back)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ProfileHeader(state.email)

                AccountSection(
                    email = state.email,
                    hasPassword = state.hasPassword,
                    onChangePassword = { viewModel.openDialog(ProfileDialog.ChangePassword) },
                )

                SettingsSection(
                    themeMode = themeMode,
                    wifiOnly = wifiOnly,
                    appLanguage = appLanguage,
                    summaryLanguage = summaryLanguage,
                    aiMode = PlanPolicy.effectiveMode(aiMode, cloudAllowed),
                    trashRetentionDays = if (state.storage != null) state.trashRetentionDays else null,
                    onNotifications = {
                        if (!context.openNotificationSettings()) viewModel.showNotice(Txt.profile.notificationSettingsFailed)
                    },
                    onTheme = { viewModel.openDialog(ProfileDialog.Theme) },
                    onWifiOnlyChange = viewModel::setWifiOnly,
                    onLanguage = { viewModel.openDialog(ProfileDialog.Language) },
                    onAiMode = { viewModel.openDialog(ProfileDialog.AiProcessing) },
                    localModel = localModel,
                    onLocalModel = { viewModel.openDialog(ProfileDialog.LocalAi) },
                    onTrashRetention = { viewModel.openDialog(ProfileDialog.TrashRetention) },
                )

                PlanSection(state.storage, state.isLoading, state.error, viewModel::load)

                state.storage?.takeIf { it.isFree }?.let { storage ->
                    PlusPromo(storage, onUpgrade = { PlusOffer.show() })
                }
                if (state.storage?.isFree == false) {
                    OutlinedButton(onClick = viewModel::switchToFree, modifier = Modifier.fillMaxWidth()) {
                        Text(Txt.plan.backToFreeTrial)
                    }
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
                    Text(Txt.profile.signOut)
                }
            }

            NoticeHost(notices, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }

    ProfileDialogs(viewModel, state)
}

/** Profil pencereleri; durum ve işlemler ViewModel'dedir. */
@Composable
private fun ProfileDialogs(viewModel: ProfileViewModel, state: ProfileUiState) {
    val host by viewModel.dialogs.state.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val summaryLanguage by viewModel.summaryLanguage.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
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
            title = Txt.profile.theme,
            description = null,
            options = ThemeMode.entries,
            current = themeMode,
            optionLabel = { it.label },
            saving = false,
            error = null,
            onSave = viewModel::setThemeMode,
            onDismiss = close,
        )

        ProfileDialog.Language -> LanguageDialog(
            appLanguage = appLanguage,
            summaryLanguage = summaryLanguage,
            onSave = viewModel::saveLanguages,
            onDismiss = close,
        )

        ProfileDialog.AiProcessing -> {
            val plus = PlanStatus.quota.collectAsState().value?.cloud == true
            AiModeDialog(
                current = PlanPolicy.effectiveMode(aiMode, cloudAllowed = plus),
                plus = plus,
                onSave = viewModel::setAiMode,
                onDismiss = close,
            )
        }

        ProfileDialog.LocalAi -> LocalModelDialog(
            onDownload = viewModel::downloadLocalModel,
            onCancelDownload = viewModel::cancelLocalModelDownload,
            onDelete = viewModel::deleteLocalModel,
            onDismiss = close,
        )

        ProfileDialog.TrashRetention -> ChoiceDialog(
            title = Txt.profile.trashRetention,
            description = Txt.profile.trashRetentionDetail,
            options = (RetentionChoices + state.trashRetentionDays).distinct().sorted(),
            current = state.trashRetentionDays,
            optionLabel = { Txt.profile.days(it) },
            saving = host.saving,
            error = host.error,
            onSave = viewModel::saveTrashRetention,
            onDismiss = close,
        )
    }
}

/** Uygulamanın ve özetin dilini seçme penceresi. İki dil aynıysa özet dili uygulama diliyle birlikte değişir. */
@Composable
private fun LanguageDialog(
    appLanguage: AppLanguage,
    summaryLanguage: AppLanguage,
    onSave: (app: AppLanguage, summary: AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    var app by remember { mutableStateOf(appLanguage) }
    var summary by remember { mutableStateOf(summaryLanguage) }
    var summaryTouched by remember { mutableStateOf(appLanguage != summaryLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Txt.language.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LanguageChoices(
                    title = Txt.language.app,
                    description = Txt.language.appDetail,
                    selected = app,
                    onSelect = {
                        app = it
                        if (!summaryTouched) summary = it
                    },
                )
                Spacer(Modifier.height(16.dp))
                LanguageChoices(
                    title = Txt.language.summary,
                    description = Txt.language.summaryDetail,
                    selected = summary,
                    onSelect = {
                        summary = it
                        summaryTouched = true
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(app, summary) }) { Text(Txt.common.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Txt.common.cancel) } },
    )
}

@Composable
private fun LanguageChoices(
    title: String,
    description: String,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(description, style = MaterialTheme.typography.bodySmall)
    AppLanguage.entries.forEach { language ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected == language, role = Role.RadioButton, onClick = { onSelect(language) })
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == language, onClick = null)
            Spacer(Modifier.width(12.dp))
            Text(language.nativeName)
        }
    }
}

/** Yapay zeka işlem yeri penceresi. Ücretsiz planda bulut seçenekleri seçilirse Plus tanıtılır. */
@Composable
private fun AiModeDialog(
    current: AiMode,
    plus: Boolean,
    onSave: (AiMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Txt.aiMode.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AiMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == mode, role = Role.RadioButton, onClick = { selected = mode })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = selected == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!plus && mode.allowsCloud) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    Txt.aiMode.plusOnly,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(Txt.common.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Txt.common.cancel) } },
    )
}

/** Çöp kutusu için sunulan süreler (gün). */
private val RetentionChoices = listOf(7, 14, 30, 60, 90)

/** Radyo düğmeli tek seçimli pencere. */
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
        confirmButton = { TextButton(enabled = !saving, onClick = { onSave(selected) }) { Text(Txt.common.save) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(Txt.common.cancel) } },
    )
}
