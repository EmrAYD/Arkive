package com.eayd.arkive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.eayd.arkive.ai.AiMode
import com.eayd.arkive.ai.LocalModel
import com.eayd.arkive.ai.LocalModelState
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.PlanPolicy
import com.eayd.arkive.data.PlanStatus
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.formatBytes

/**
 * "Telefondaki yapay zeka" penceresi: dosyaları telefonda özetleyen modelin ([LocalModel]) durumu ve işlemleri. Metinler
 * geçerli yapay zeka işlem yerine göre değişir ([AiMode]; ücretsiz planda her zaman Yerel): Yerel'de dosyalar yalnızca
 * bununla, Yerel + Bulut'ta küçük dosyalar bununla özetlenir, Bulut'ta model kullanılmaz. Durum canlıdır; indirme
 * sürerken ilerleme çubuğu dolar.
 *  - İndirilmedi: ne işe yaradığı, boyutu, Wi-Fi notu → "İndir".
 *  - İniyor: ilerleme → "İndirmeyi durdur".
 *  - Hazır: → "Modeli sil" (yer açmak için; önce onay sorulur).
 *  - Bu telefonda çalışmaz: nedeni.
 *
 * Hem AI düğmesinden (dosya telefonda özetlenecekken model hazır değilse) hem Profil > Ayarlar'dan açılır.
 * [onSummarizeInCloud]: AI düğmesinden "Yerel + Bulut"ta açıldıysa, model inmeden dosyayı hemen bulutta özetler
 * ("Bulutta özetle" düğmesi); yoksa null.
 */
@Composable
fun LocalModelDialog(
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSummarizeInCloud: (() -> Unit)? = null,
) {
    val state by LocalModel.state.collectAsState()
    val wifiOnly by AppSettings.wifiOnly.collectAsState()
    val plus = PlanStatus.quota.collectAsState().value?.cloud == true
    val aiMode by AppSettings.aiMode.collectAsState()
    val mode = PlanPolicy.effectiveMode(aiMode, cloudAllowed = plus)
    var confirmDelete by remember { mutableStateOf(false) }
    val model = LocalModel.DISPLAY_NAME
    val size = LocalModel.sizeText
    val secondary = MaterialTheme.typography.bodySmall
    val secondaryColor = MaterialTheme.colorScheme.outline

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AiIcons.Filled, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(Txt.localAi.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    LocalModelState.NotDownloaded -> {
                        Text(
                            when (mode) {
                                AiMode.Local -> Txt.localAi.intro(model, size)
                                AiMode.Hybrid -> Txt.localAi.introHybrid(model, size)
                                AiMode.Cloud -> Txt.localAi.introCloud(model, size)
                            },
                        )
                        Text(
                            if (wifiOnly) Txt.localAi.wifiNote else Txt.localAi.meteredNote,
                            style = secondary,
                            color = secondaryColor,
                        )
                    }

                    is LocalModelState.Downloading -> {
                        Text(
                            if (s.waitingForNetwork) {
                                Txt.localAi.waitingBody
                            } else {
                                Txt.localAi.downloadingBody(formatBytes(s.downloadedBytes), formatBytes(s.totalBytes))
                            },
                        )
                        val percent = s.percent
                        if (percent != null) {
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                drawStopIndicator = {}, // Material 3'ün çubuğun sonuna çizdiği nokta çizilmesin
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(Txt.localAi.downloadingNote, style = secondary, color = secondaryColor)
                    }

                    LocalModelState.Ready -> Text(
                        when {
                            confirmDelete -> Txt.localAi.deleteBody(size)
                            mode == AiMode.Local -> Txt.localAi.readyBody(model, size)
                            mode == AiMode.Hybrid -> Txt.localAi.readyBodyHybrid(model, size)
                            else -> Txt.localAi.readyBodyCloud(model, size)
                        },
                    )

                    LocalModelState.Unsupported -> Text(
                        if (plus) Txt.localAi.unsupportedBodyPlus else Txt.localAi.unsupportedBody,
                    )
                }
            }
        },
        confirmButton = {
            when {
                state == LocalModelState.NotDownloaded -> TextButton(onClick = onDownload) { Text(Txt.localAi.download) }
                // Model inerken "Yerel + Bulut"ta dosya beklemeden bulutta özetlenebilir.
                state is LocalModelState.Downloading && onSummarizeInCloud != null ->
                    TextButton(onClick = onSummarizeInCloud) { Text(Txt.localAi.summarizeInCloud) }
                state == LocalModelState.Ready && confirmDelete -> TextButton(onClick = onDelete) {
                    Text(Txt.common.delete, color = MaterialTheme.colorScheme.error)
                }
                else -> TextButton(onClick = onDismiss) { Text(Txt.common.close) }
            }
        },
        dismissButton = {
            when (state) {
                LocalModelState.NotDownloaded -> if (onSummarizeInCloud != null) {
                    TextButton(onClick = onSummarizeInCloud) { Text(Txt.localAi.summarizeInCloud) }
                } else {
                    TextButton(onClick = onDismiss) { Text(Txt.common.cancel) }
                }

                is LocalModelState.Downloading -> TextButton(onClick = onCancelDownload) {
                    Text(Txt.localAi.cancelDownload, color = MaterialTheme.colorScheme.error)
                }

                LocalModelState.Ready -> if (confirmDelete) {
                    TextButton(onClick = { confirmDelete = false }) { Text(Txt.common.cancel) }
                } else {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(Txt.localAi.deleteModel, color = MaterialTheme.colorScheme.error)
                    }
                }

                LocalModelState.Unsupported -> Unit
            }
        },
    )
}
