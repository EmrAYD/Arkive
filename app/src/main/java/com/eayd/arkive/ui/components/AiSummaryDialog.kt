package com.eayd.arkive.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eayd.arkive.ai.AiPolicy
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.ai.LocalModel
import com.eayd.arkive.ai.LocalModelState
import com.eayd.arkive.ai.SpeechStatus
import com.eayd.arkive.ai.SummaryRoute
import com.eayd.arkive.ai.SummaryUi
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.PlanPolicy
import com.eayd.arkive.data.PlanStatus
import com.eayd.arkive.i18n.Txt

/** Özetlemeyi başlatır. Android 13+'te bildirim izni yoksa önce izin ister; izin verilmese de özetleme başlar. */
@Composable
private fun rememberSummaryStarter(): (FileDto) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<FileDto?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { AiSummarizer.confirm(it) }
        pending = null
    }
    return { file ->
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            pending = file
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AiSummarizer.confirm(file)
        }
    }
}

/** Dosyanın nerede özetleneceği. */
@Composable
private fun summaryRoute(file: FileDto): SummaryRoute {
    val cloud = PlanStatus.quota.collectAsState().value?.cloud == true
    val aiMode by AppSettings.aiMode.collectAsState()
    val modelReady = LocalModel.state.collectAsState().value == LocalModelState.Ready
    return AiPolicy.route(PlanPolicy.effectiveMode(aiMode, cloud), modelReady, file.size)
}

/** AI düğmesinin pencereleri: onay, parola, durdurma, özet, silme ve model indirme. */
@Composable
fun AiSummaryDialogHost() {
    val ui by AiSummarizer.ui.collectAsState()
    val startSummary = rememberSummaryStarter()
    when (val state = ui) {
        null -> Unit

        is SummaryUi.Confirm -> {
            val start = { startSummary(state.file) }
            AlertDialog(
                onDismissRequest = AiSummarizer::dismiss,
                title = { Text(Txt.ai.confirmTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(Txt.ai.confirmBody(state.file.name))
                        Text(
                            when (summaryRoute(state.file)) {
                                SummaryRoute.PhoneOnly -> Txt.ai.confirmOnPhone
                                SummaryRoute.PhoneThenCloud -> Txt.ai.confirmPhoneThenCloud
                                SummaryRoute.Cloud -> Txt.ai.confirmInCloud
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = start) { Text(Txt.ai.summarize) } },
                dismissButton = { TextButton(onClick = AiSummarizer::dismiss) { Text(Txt.common.cancel) } },
            )
        }

        is SummaryUi.Password -> {
            val note = when (summaryRoute(state.file)) {
                SummaryRoute.PhoneOnly -> Txt.ai.passwordLocalNote
                SummaryRoute.PhoneThenCloud -> Txt.ai.passwordHybridNote
                SummaryRoute.Cloud -> Txt.ai.passwordCloudNote
            }
            PasswordDialog(
                title = Txt.ai.passwordTitle,
                message = Txt.ai.passwordBody(state.file.name),
                note = note,
                confirmLabel = Txt.ai.summarize,
                busy = false,
                error = if (state.wrong) Txt.docs.wrongPassword else null,
                onConfirm = { password -> AiSummarizer.confirm(state.file, password) },
                onDismiss = AiSummarizer::dismiss,
            )
        }

        is SummaryUi.StopAsk -> AlertDialog(
            onDismissRequest = AiSummarizer::dismiss,
            title = { Text(Txt.ai.runningTitle) },
            text = {
                Text(
                    Txt.ai.runningBody(state.file.name),
                )
            },
            confirmButton = { TextButton(onClick = { AiSummarizer.cancel(state.file) }) { Text(Txt.common.stop) } },
            dismissButton = { TextButton(onClick = AiSummarizer::dismiss) { Text(Txt.ai.keepWaiting) } },
        )

        is SummaryUi.Viewing -> {
            val context = LocalContext.current
            val scroll = rememberScrollState()
            val speaker = rememberSummarySpeaker(state.text)
            val speech by speaker.state.collectAsState()
            val reading = speaker.currentRange(speech)
            var layout by remember(state.text) { mutableStateOf<TextLayoutResult?>(null) }
            var viewport by remember { mutableIntStateOf(0) }
            LaunchedEffect(reading, speech.status) {
                val textLayout = layout
                if (speech.status == SpeechStatus.Playing && reading != null && textLayout != null) {
                    scroll.reveal(textLayout, reading, viewport)
                }
            }
            AlertDialog(
                onDismissRequest = AiSummarizer::dismiss,
                icon = { Icon(AiIcons.Filled, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(Txt.ai.summaryOf(state.fileName), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        state.note?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.size(8.dp))
                        }
                        SelectionContainer {
                            Text(
                                text = highlightSentence(
                                    state.text,
                                    reading,
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    foreground = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                onTextLayout = { layout = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .onSizeChanged { viewport = it.height }
                                    .verticalScrollbar(scroll, thumbColor = MaterialTheme.colorScheme.primary)
                                    .verticalScroll(scroll)
                                    .padding(end = 12.dp),
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        SummaryPlayer(speaker, speech)
                    }
                },
                confirmButton = { TextButton(onClick = AiSummarizer::dismiss) { Text(Txt.common.close) } },
                dismissButton = {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(Txt.ai.summaryTitle, state.text))
                        Toast.makeText(context, Txt.ai.summaryCopied, Toast.LENGTH_SHORT).show()
                    }) { Text(Txt.common.copy) }
                },
            )
        }

        is SummaryUi.ModelSetup -> LocalModelDialog(
            onDownload = AiSummarizer::downloadModel,
            onCancelDownload = LocalModel::cancelDownload,
            onDelete = { LocalModel.delete(); AiSummarizer.dismiss() },
            onDismiss = AiSummarizer::dismiss,
            onSummarizeInCloud = state.cloudAlternative?.let { file -> { startSummary(file) } },
        )

        is SummaryUi.DeleteAsk -> AlertDialog(
            onDismissRequest = AiSummarizer::dismiss,
            title = { Text(Txt.ai.deleteSummary) },
            text = { Text(Txt.ai.deleteSummaryBody(state.fileName)) },
            confirmButton = {
                TextButton(onClick = { AiSummarizer.deleteSummary(state.fileId, state.fileName) }) {
                    Text(Txt.common.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = AiSummarizer::dismiss) { Text(Txt.common.cancel) } },
        )
    }
}
