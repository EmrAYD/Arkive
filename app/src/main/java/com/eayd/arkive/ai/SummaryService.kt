package com.eayd.arkive.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eayd.arkive.MainActivity
import com.eayd.arkive.R
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.i18n.Txt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Özetleme sürerken çalışan ön plan servisi.
 *
 * Uygulama arka plandayken Android'in süreci dondurmasını önler ve ilerleme bildirimini gösterir.
 */
class SummaryService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppSettings.storedAppLanguage(this)
        goForeground(buildNotification(Txt.ai.serviceStarting))
        acquireWakeLock()
        if (watcher == null) {
            watcher = scope.launch {
                AiSummarizer.running.combine(AiSummarizer.status) { running, status -> running to status }
                    .collect { (running, status) ->
                        if (running.isEmpty()) {
                            stopSelf()
                        } else {
                            val text = status.values.firstOrNull() ?: Txt.ai.serviceSummarizing
                            val title = if (running.size > 1) Txt.ai.serviceSummarizingFiles(running.size) else Txt.ai.serviceSummarizingFile
                            notificationManager().notify(NOTIFICATION_ID, buildNotification(text, title))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arkive:summary").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun buildNotification(text: String, title: String = Txt.ai.serviceSummarizingFile): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, Txt.ai.serviceChannelName, NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val TAG = "SummaryService"
        private const val CHANNEL_ID = "ai_summary"
        private const val NOTIFICATION_ID = 4101

        /** Servisi başlatır. Manifest'te tanımlı değilse başlatmaz ve Logcat'e hata yazar. */
        fun start(context: Context) {
            val intent = Intent(context, SummaryService::class.java)
            if (context.packageManager.resolveService(intent, 0) == null) {
                Log.e(TAG, "SummaryService AndroidManifest.xml'de tanımlı değil; özetleme arka planda sürmez, bildirim gelmez.")
                return
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
