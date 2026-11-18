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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.ai.AiSummarizer
import com.eayd.arkive.ai.SummaryUi
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.i18n.Txt

/**
 * AI düğmesine basılınca açılan pencereler: onay ("Bu dosyayı özetlemek istiyor musunuz?"), özetleme sürerken durdurma
 * sorusu ve özet metni. Özetleme başladıktan sonra pencere kapanır; ilerleme satırdaki yükleme simgesinde görünür.
 */
@Composable
fun AiSummaryDialogHost() {
    val ui by AiSummarizer.ui.collectAsState()
    when (val state = ui) {
        null -> Unit

        is SummaryUi.Confirm -> {
            val context = LocalContext.current
            // Android 13+: özetleme sırasında ilerleme bildirimi için izin istenir. Verilmese de özetleme aynen sürer.
            var pending by remember { mutableStateOf<FileDto?>(null) }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                pending?.let { AiSummarizer.confirm(it) }
                pending = null
            }
            val start = {
                val needsAsk = Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsAsk) {
                    pending = state.file
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    AiSummarizer.confirm(state.file)
                }
            }
            AlertDialog(
                onDismissRequest = AiSummarizer::dismiss,
                title = { Text(Txt.ai.confirmTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(Txt.ai.confirmBody(state.file.name))
                        Text(
                            Txt.ai.confirmDetail,
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
            val mode by AppSettings.aiMode.collectAsState()
            PasswordDialog(
                title = Txt.ai.passwordTitle,
                message = Txt.ai.passwordBody(state.file.name),
                note = when (mode) {
                    AiMode.Local -> Txt.ai.passwordLocalNote
                    AiMode.Cloud -> Txt.ai.passwordCloudNote
                    AiMode.Hybrid -> Txt.ai.passwordHybridNote
                },
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
            AlertDialog(
                onDismissRequest = AiSummarizer::dismiss,
                title = { Text(Txt.ai.summaryTitle) },
                text = {
                    Column {
                        Text(
                            Txt.ai.summaryMeta(state.fileName, state.source.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                        )
                        state.note?.let {
                            Spacer(Modifier.size(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Spacer(Modifier.size(12.dp))
                        // Uzun özet kaydırılır; metin seçilip kopyalanabilir.
                        SelectionContainer {
                            Text(
                                state.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText(Txt.ai.summaryTitle, state.text))
                            Toast.makeText(context, Txt.ai.summaryCopied, Toast.LENGTH_SHORT).show()
                        }) { Text(Txt.common.copy) }
                    }
                },
                confirmButton = { TextButton(onClick = AiSummarizer::dismiss) { Text(Txt.common.close) } },
            )
        }
    }
}
