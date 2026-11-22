package com.eayd.arkive.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eayd.arkive.i18n.Txt

/** Hata mesajı ve "Tekrar dene" düğmesi. */
@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(Txt.common.retry) }
    }
}

/** Ortada kısa bir bilgi metni. */
@Composable
fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(24.dp),
    )
}

/** ViewModel'deki bildirimi gösterir ve alanı hemen temizletir; aynı bildirim iki kez görünmesin. */
@Composable
fun NoticeEffect(notice: String?, host: NoticeHostState, onShown: () -> Unit) {
    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        host.show(message)
        onShown()
    }
}
