package com.eayd.arkive.screens.preview

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eayd.arkive.data.FileDto

/** Ses: [MediaPlayer] ile akıştan çalar; oynat/durdur düğmesi ve ilerleme çubuğu bu ekranda çizilir. */
@Composable
internal fun AudioPlayer(url: String, file: FileDto, onOpenExternally: () -> Unit) {
    var failed by remember(url) { mutableStateOf(false) }
    var prepared by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var position by remember(url) { mutableStateOf(0) }
    var duration by remember(url) { mutableStateOf(0) }
    var dragging by remember(url) { mutableStateOf(false) }
    val player = remember(url) { MediaPlayer() }

    DisposableEffect(player) {
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(url)
            player.setOnPreparedListener {
                duration = it.duration
                prepared = true
                it.start()
                playing = true
            }
            player.setOnCompletionListener { playing = false; position = 0 }
            player.setOnErrorListener { _, _, _ -> failed = true; true }
            player.prepareAsync()   // arka planda hazırlanır, arayüzü bloklamaz
        } catch (e: Exception) {
            failed = true
        }
        onDispose { player.release() }
    }

    TrackPosition(playing, dragging, current = { player.currentPosition }) { position = it }

    if (failed) {
        NoPreviewMessage(file, "Ses oynatılamadı. Bu biçim telefonda desteklenmiyor olabilir.", onOpenExternally)
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.AudioFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        if (!prepared) {
            CircularProgressIndicator()
        } else {
            SeekBar(
                position = position,
                duration = duration,
                onChange = { dragging = true; position = it },
                onFinished = { player.seekTo(position); dragging = false },
            )
            Spacer(Modifier.height(8.dp))
            IconButton(
                onClick = {
                    if (playing) { player.pause(); playing = false } else { player.start(); playing = true }
                },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Durdur" else "Oynat",
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
