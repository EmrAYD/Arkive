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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eayd.arkive.MainActivity
import com.eayd.arkive.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Özetleme sürerken çalışan ön plan servisi. Android, arka plana alınan bir uygulamanın işlemcisini kısıtlar ve
 * süreci dondurur ya da sonlandırır; dosyanın indirilmesi ve sunucudaki özetleme dakikalar sürebildiğinden bu olmadan iş yarıda kalırdı.
 * Servis yalnızca ilerleme bildirimini gösterir ve CPU uyumasın diye kısmi WakeLock tutar; asıl iş [AiSummarizer]'dadır.
 * Özetlenen dosya kalmayınca kendini kapatır.
 */
class SummaryService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() çağrısından sonra 5 sn içinde startForeground() gerekir: ilk iş budur.
        goForeground(buildNotification("Özetleme başlıyor…"))
        acquireWakeLock()
        if (watcher == null) {
            watcher = scope.launch {
                AiSummarizer.running.combine(AiSummarizer.status) { running, status -> running to status }
                    .collect { (running, status) ->
                        if (running.isEmpty()) {
                            stopSelf()
                        } else {
                            val text = status.values.firstOrNull() ?: "Özetleniyor…"
                            val title = if (running.size > 1) "${running.size} dosya özetleniyor" else "Dosya özetleniyor"
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
        // Üst sınırlı: en kötü durumda 30 dakika sonra kendiliğinden bırakılır.
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arkive:summary").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun buildNotification(text: String, title: String = "Dosya özetleniyor"): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Yapay zeka özetleme", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ai)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ai_summary"
        private const val NOTIFICATION_ID = 4101

        /** Servisi başlatır (zaten çalışıyorsa bir şey değişmez). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SummaryService::class.java))
        }
    }
}
