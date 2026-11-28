package com.eayd.arkive.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.eayd.arkive.MainActivity
import com.eayd.arkive.R
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.i18n.Lang
import com.eayd.arkive.i18n.Txt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Özet sesli okunurken çalışan ön plan servisi: bildirimi, kilit ekranı denetimlerini ve kulaklık tuşlarını yönetir.
 *
 * Okumanın kendisi [SummaryPlayback]'tedir; servis yalnızca durumu gösterir ve komutları ona iletir. Okuma bitince
 * kendini kapatır.
 */
class SummaryPlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var media: MediaSession? = null

    /** Son başlatma isteği; daha yeni bir istek gelmişse servis kapanmaz. */
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppSettings.storedAppLanguage(this)
        lastStartId = startId
        val session = media ?: createSession().also { media = it }
        goForeground(buildNotification(session, SummaryPlayback.now.value))
        when (intent?.action) {
            ACTION_TOGGLE -> SummaryPlayback.toggle()
            ACTION_SPEED -> SummaryPlayback.cycleSpeed()
            ACTION_STOP -> SummaryPlayback.stop()
        }
        if (watcher == null) {
            watcher = scope.launch { SummaryPlayback.now.collect { show(it) } }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        media?.isActive = false
        media?.release()
        media = null
        super.onDestroy()
    }

    /** Bildirimi ve kilit ekranını günceller; okuma bittiyse servisi kapatır. */
    private fun show(now: PlaybackNow?) {
        val session = media ?: return
        if (now == null) {
            session.isActive = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
            return
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, now.session.fileName)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle(now.state))
                .build(),
        )
        session.setPlaybackState(playbackState(now.state))
        session.isActive = true
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(session, now))
    }

    private fun createSession(): MediaSession = MediaSession(this, TAG).apply {
        setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() = SummaryPlayback.play()

                override fun onPause() = SummaryPlayback.pause()

                override fun onStop() = SummaryPlayback.stop()

                override fun onCustomAction(action: String, extras: Bundle?) {
                    when (action) {
                        ACTION_SPEED -> SummaryPlayback.cycleSpeed()
                        ACTION_STOP -> SummaryPlayback.stop()
                    }
                }
            },
        )
        setSessionActivity(openIntent())
    }

    /** Kilit ekranındaki ve yeni Android sürümlerindeki medya denetimleri bu durumdan çizilir. */
    private fun playbackState(state: SpeechState): PlaybackState {
        val code = when (state.status) {
            SpeechStatus.Playing -> PlaybackState.STATE_PLAYING
            SpeechStatus.Preparing -> PlaybackState.STATE_BUFFERING
            else -> PlaybackState.STATE_PAUSED
        }
        val speed = SpeechText.speedText(state.speed, Lang.locale)
        return PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP,
            )
            .setState(code, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (code == PlaybackState.STATE_PLAYING) state.speed else 0f)
            .addCustomAction(
                PlaybackState.CustomAction.Builder(ACTION_SPEED, Txt.speech.speed(speed), R.drawable.ic_speech_speed).build(),
            )
            .addCustomAction(
                PlaybackState.CustomAction.Builder(ACTION_STOP, Txt.common.close, R.drawable.ic_speech_close).build(),
            )
            .build()
    }

    private fun buildNotification(session: MediaSession, now: PlaybackNow?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, Txt.speech.channelName, NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setSmallIcon(R.drawable.ic_stat_logo)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setContentIntent(openIntent())
        if (now == null) return builder.setContentTitle(Txt.speech.channelName).build()
        val status = now.state.status
        val playing = status == SpeechStatus.Playing || status == SpeechStatus.Preparing
        val speed = SpeechText.speedText(now.state.speed, Lang.locale)
        return builder
            .setContentTitle(now.session.fileName)
            .setContentText(subtitle(now.state))
            .addAction(
                if (playing) {
                    action(R.drawable.ic_speech_pause, Txt.speech.pause, ACTION_TOGGLE)
                } else {
                    action(R.drawable.ic_speech_play, Txt.speech.listen, ACTION_TOGGLE)
                },
            )
            .addAction(action(R.drawable.ic_speech_speed, Txt.speech.speed(speed), ACTION_SPEED))
            .addAction(action(R.drawable.ic_speech_close, Txt.common.close, ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun subtitle(state: SpeechState): String =
        Txt.speech.notificationText(SpeechText.speedText(state.speed, Lang.locale))

    private fun action(icon: Int, title: String, action: String): Notification.Action {
        val intent = PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, SummaryPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, intent).build()
    }

    /** Bildirime dokununca okunan özetin penceresi açılır. */
    private fun openIntent(): PendingIntent {
        val current = SummaryPlayback.session
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (current != null) {
            intent.putExtra(SummaryOpenRequest.EXTRA_FILE_ID, current.fileId)
                .putExtra(SummaryOpenRequest.EXTRA_FILE_NAME, current.fileName)
        }
        return PendingIntent.getActivity(
            this,
            OPEN_REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun goForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Seslendirme bildirimi gösterilemedi", e)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "SummaryPlayback"
        private const val CHANNEL_ID = "summary_speech"
        private const val NOTIFICATION_ID = 4102
        private const val OPEN_REQUEST = 4102
        private const val ACTION_TOGGLE = "com.eayd.arkive.action.SPEECH_TOGGLE"
        private const val ACTION_SPEED = "com.eayd.arkive.action.SPEECH_SPEED"
        private const val ACTION_STOP = "com.eayd.arkive.action.SPEECH_STOP"

        /** Servisi başlatır. Manifest'te tanımlı değilse okuma bildirimsiz sürer ve Logcat'e hata yazılır. */
        fun start(context: Context) {
            val intent = Intent(context, SummaryPlaybackService::class.java)
            if (context.packageManager.resolveService(intent, 0) == null) {
                Log.e(TAG, "SummaryPlaybackService AndroidManifest.xml'de tanımlı değil; seslendirme bildirimi gösterilmez.")
                return
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Seslendirme servisi başlatılamadı", e)
            }
        }
    }
}
