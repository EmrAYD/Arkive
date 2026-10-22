package com.eayd.arkive.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Kullanıcı Sözleşmesi'nin tamamını gösteren tam ekran pencere.
 *
 * "Okudum, onaylıyorum" düğmesi, metin en sonuna kadar kaydırılana kadar KAPALIDIR. Bir kez en sona inildikten
 * sonra (kullanıcı geri yukarı kaydırsa bile) açık kalır. Pencere her açıldığında bu durum sıfırdan başlar.
 */
@Composable
fun EulaDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val listState = rememberLazyListState()

            // Düzen henüz ölçülmeden canScrollForward = false görünür; yanlışlıkla "sona geldi" saymamak için
            // en az bir öğenin ekranda olması şart koşulur.
            val atEnd by remember {
                derivedStateOf {
                    listState.layoutInfo.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward
                }
            }
            var reachedEnd by remember { mutableStateOf(false) }
            LaunchedEffect(atEnd) { if (atEnd) reachedEnd = true }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = EulaContent.TITLE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat")
                    }
                }
                HorizontalDivider()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(EulaContent.blocks) { block -> EulaBlockView(block) }
                }

                HorizontalDivider()
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (!reachedEnd) {
                        Text(
                            text = "Onaylamak için sözleşmeyi sonuna kadar kaydırın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onAccept,
                        enabled = reachedEnd,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Okudum, onaylıyorum")
                    }
                }
            }
        }
    }
}

@Composable
private fun EulaBlockView(block: EulaBlock) {
    when (block) {
        is EulaBlock.Heading -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )

        is EulaBlock.Paragraph -> Text(
            text = boldMarkup(block.text),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** `**kalın**` işaretli metni, kalın kısımları koyu yazılmış bir [AnnotatedString]'e çevirir. */
private fun boldMarkup(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
        } else {
            append(part)
        }
    }
}

/**
 * Kayıt ekranındaki "Kullanıcı Sözleşmesi'ni okudum ve kabul ediyorum" satırı.
 *
 * Kutu doğrudan işaretlenemez: işaretli değilken dokunmak sözleşmeyi açtırır ([onOpen]), kutu yalnızca
 * [EulaDialog] içinde onaylanınca işaretlenir. İşaretliyken dokunmak onayı geri alır ([onWithdraw]).
 */
@Composable
fun EulaConsentRow(accepted: Boolean, onOpen: () -> Unit, onWithdraw: () -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox) { if (accepted) onWithdraw() else onOpen() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = accepted, onCheckedChange = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append("Kullanıcı Sözleşmesi")
                }
                append("'ni okudum ve kabul ediyorum.")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
