package com.eayd.arkive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eayd.arkive.R
import com.eayd.arkive.data.PlanStatus
import com.eayd.arkive.data.PlanSwitcher
import com.eayd.arkive.data.PlusOffer
import com.eayd.arkive.data.PlusPlan
import com.eayd.arkive.utils.toApiErrorMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Plus tanıtımı açık mı ([PlusOffer]); açıksa tam ekran pencereyi gösterir. Uygulamada bir kez, ana gezinmenin
 * yanında durur (ArkiveNavHost): AI düğmesi, yan menüdeki "Daha fazla depolama alanı alın" ve profildeki "Plus'a geç"
 * aynı pencereyi açar.
 */
@Composable
fun PlusOfferHost() {
    val request by PlusOffer.request.collectAsState()
    val quota by PlanStatus.quota.collectAsState()
    request?.let { PlusOfferDialog(reason = it.reason, alreadyPlus = quota?.isPlus == true, onDismiss = PlusOffer::dismiss) }
}

/**
 * Tüm ekranı kaplayan Plus tanıtımı: logo ve başlık, (varsa) neden açıldığı, Plus'ın sundukları (toplam depolama, tek
 * seferde yükleme sınırı, yapay zeka özetleri) ve fiyatlı "Plus'a geç" düğmesi. Sağ üstteki çarpı ya da geri tuşu kapatır.
 *
 * DENEME İÇİN: ödeme sistemi yok; "Plus'a geç" ödeme almadan Plus'a geçirir ([PlanSwitcher], 30 gün), Plus'taki kullanıcı
 * buradan ücretsiz plana dönebilir. Sunucuda PLAN_SWITCH_ENABLED kapatılınca düğme hatayı gösterir.
 */
@Composable
private fun PlusOfferDialog(reason: String?, alreadyPlus: Boolean, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun switchTo(planName: String, success: String) {
        busy = true
        error = null
        result = null
        scope.launch {
            try {
                PlanSwitcher.switchTo(planName)
                result = success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toApiErrorMessage()
            } finally {
                busy = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
            Box(Modifier.fillMaxSize()) {
                // Üstte temanın renginden arka plana yumuşak geçiş.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.background))),
                )

                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(56.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Arkive",
                            tint = Color.Unspecified,
                            modifier = Modifier.width(150.dp).height(87.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Arkive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                                Text(
                                    PlusPlan.NAME,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Daha fazla alan, sınırsız ve daha akıllı yapay zeka özetleri.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        if (reason != null) {
                            Spacer(Modifier.height(20.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.tertiaryContainer,
                                contentColor = colors.onTertiaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(reason, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Feature(
                                icon = Icons.Outlined.Cloud,
                                title = "${PlusPlan.STORAGE_TEXT} toplam depolama",
                                detail = "Ücretsiz planda ${PlusPlan.FREE_STORAGE_TEXT}.",
                            )
                            Feature(
                                icon = Icons.Filled.UploadFile,
                                title = "Tek seferde ${PlusPlan.MAX_FILE_TEXT}'a kadar dosya yükleme",
                                detail = "Ücretsiz planda dosya başına ${PlusPlan.FREE_MAX_FILE_TEXT}.",
                            )
                            Feature(
                                icon = AiIcons.Filled,
                                title = "Sınırsız yapay zeka özeti",
                                detail = "Ücretsiz planda toplam ${PlusPlan.FREE_SUMMARY_LIMIT} dosya özetlenebilir.",
                            )
                            Feature(
                                icon = Icons.Filled.CloudDone,
                                title = "Bulutta yapay zeka özeti",
                                detail = "Büyük, uzun ve görselli belgeler sunucudaki yapay zekayla, konuları anlatan " +
                                    "yeniden yazılmış bir özetle özetlenir. Ücretsiz planda yalnızca telefonda, küçük " +
                                    "dosyalar özetlenir.",
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    // Altta sabit: fiyatlı düğme.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        result?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                        }
                        error?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { switchTo(PlusPlan.NAME, "Plus planına geçildi. Artık bulutta ve sınırsız özetleyebilirsiniz.") },
                            enabled = !alreadyPlus && !busy,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = if (alreadyPlus) "Plus planındasınız" else "Plus'a geç · ${PlusPlan.PRICE_TEXT}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (alreadyPlus) {
                            TextButton(onClick = { switchTo("Free", "Ücretsiz plana dönüldü.") }, enabled = !busy) {
                                Text("Ücretsiz plana dön (deneme)")
                            }
                        } else {
                            Text(
                                text = "Deneme sürümü: ödeme alınmaz, Plus 30 gün geçerlidir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Kapat")
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
