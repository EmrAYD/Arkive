package com.eayd.arkive.screens.preview

import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eayd.arkive.data.FileDto
import kotlinx.coroutines.delay

/**
 * Video: Android'in yerleşik [VideoView]'ı imzalı adresten akış olarak oynatır (dosya önce indirilmez).
 * Denetimler (oynat/durdur, 10 sn geri/ileri, ilerleme çubuğu) yerleşik MediaController yerine bu ekranda
 * çizilir; çünkü yerleşik denetim paneli video duraklatılınca bile birkaç saniyede kayboluyor ve görünümü eski.
 *
 * Denetimlerin görünme kuralı:
 *  - Video DURAKLATILMIŞSA (ya da bittiyse, hazırlanıyorsa) denetimler hep görünür.
 *  - Video OYNUYORSA ekrana dokunulunca görünür, son dokunuştan 4 sn sonra kaybolur.
 *  - Ekrana dokunmak denetimleri açar/kapatır.
 */
@Composable
internal fun VideoPlayer(url: String, file: FileDto, onOpenExternally: () -> Unit) {
    var failed by remember(url) { mutableStateOf(false) }
    var prepared by remember(url) { mutableStateOf(false) }
    var buffering by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var position by remember(url) { mutableStateOf(0) }
    var duration by remember(url) { mutableStateOf(0) }
    var dragging by remember(url) { mutableStateOf(false) }
    var controlsVisible by remember(url) { mutableStateOf(true) }
    // Her dokunuşta artar; otomatik gizleme sayacı bu yüzden sıfırlanır.
    var interaction by remember(url) { mutableStateOf(0) }
    // Oynatıcıyı ekrandan çıkınca durdurabilmek ve komut verebilmek için görünüm burada tutulur.
    val holder = remember(url) { arrayOfNulls<VideoView>(1) }

    DisposableEffect(url) {
        onDispose { holder[0]?.stopPlayback() }
    }

    TrackPosition(playing, dragging, current = { holder[0]?.currentPosition }) { position = it }

    // Otomatik gizleme: yalnızca video oynuyor ve kullanıcı çubuğu sürüklemiyorsa.
    LaunchedEffect(controlsVisible, playing, dragging, interaction) {
        if (controlsVisible && playing && !dragging) {
            delay(4000)
            controlsVisible = false
        }
    }

    if (failed) {
        NoPreviewMessage(file, "Video oynatılamadı. Bu biçim telefonda desteklenmiyor olabilir.", onOpenExternally)
        return
    }

    val showControls = controlsVisible || !playing || dragging

    fun seekBy(deltaMs: Int) {
        val target = (position + deltaMs).coerceIn(0, duration)
        position = target
        holder[0]?.seekTo(target)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val video = VideoView(ctx)
                holder[0] = video
                video.keepScreenOn = true   // izlerken ekran kararmasın
                video.setOnPreparedListener { mp ->
                    duration = video.duration
                    prepared = true
                    mp.setOnInfoListener { _, what, _ ->
                        when (what) {
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
                            MediaPlayer.MEDIA_INFO_BUFFERING_END,
                            MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> buffering = false
                        }
                        false
                    }
                    video.start()
                    playing = true
                }
                video.setOnCompletionListener { playing = false; controlsVisible = true }
                video.setOnErrorListener { _, _, _ -> failed = true; true }
                video.setVideoURI(Uri.parse(url))
                // VideoView, görüntü oranını koruyarak verilen alana sığar; FrameLayout ile ortalanır.
                FrameLayout(ctx).apply {
                    addView(
                        video,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER),
                    )
                }
            },
        )

        // Videonun üstündeki şeffaf katman: dokunuşları yakalar (alttaki VideoView'a gitmez).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        controlsVisible = !controlsVisible
                        interaction++
                    })
                },
        )

        if (!prepared || buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Videoyla karışmasın diye altta yumuşak koyu geçiş
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 12.dp),
            ) {
                if (prepared) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { seekBy(-10_000); interaction++ }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.Replay10, "10 saniye geri", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(
                            onClick = {
                                val video = holder[0]
                                if (playing) {
                                    video?.pause()
                                    playing = false
                                } else {
                                    // Video bittiyse baştan oynat
                                    if (duration > 0 && position >= duration - 500) { video?.seekTo(0); position = 0 }
                                    video?.start()
                                    playing = true
                                }
                                interaction++
                            },
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Durdur" else "Oynat",
                                tint = Color.White,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = { seekBy(10_000); interaction++ }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.Forward10, "10 saniye ileri", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    SeekBar(
                        position = position,
                        duration = duration,
                        light = true,
                        onChange = { dragging = true; position = it },
                        onFinished = {
                            holder[0]?.seekTo(position)
                            dragging = false
                            interaction++
                        },
                    )
                }
            }
        }
    }
}
