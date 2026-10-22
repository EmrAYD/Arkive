package com.eayd.arkive.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Liste, çöp kutusu ve ön izleme ekranlarının ortak "hata / boş durum / bildirim" parçaları.

/** Ekranın ortasında hata mesajı ve "Tekrar dene" düğmesi. Çağıran, bunu `Box` içinde ortalar. */
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
        Button(onClick = onRetry) { Text("Tekrar dene") }
    }
}

/** Ekranın ortasında kısa bir bilgi metni (boş liste gibi). */
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

/**
 * ViewModel'in [notice] (kısa bildirim) alanı dolunca Snackbar gösterir, ardından [onShown] ile alanı temizletir.
 * Liste, çöp kutusu ve ön izleme ekranları aynı kuralı kullanır.
 */
@Composable
fun NoticeEffect(notice: String?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onShown()
    }
}
