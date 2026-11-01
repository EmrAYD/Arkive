package com.eayd.arkive.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eayd.arkive.MainActivity
import com.eayd.arkive.R

/**
 * Uygulama arka plandayken ya da kapalıyken özetleme bitince gösterilen sistem bildirimi. Dokununca uygulama açılır ve
 * doğrudan o dosyanın özeti gösterilir ([MainActivity] isteği [AiSummarizer.requestOpenSummary] ile iletir).
 *
 * Özetleme sürerken gösterilen ilerleme bildirimi ([SummaryService]) ayrıdır ve iş bitince kalkar; bu bildirim kalıcıdır
 * (dokununca ya da kaydırılınca kapanır) ve her dosya için ayrı görünür.
 */
object SummaryNotifier {

    private const val CHANNEL_ID = "ai_summary_done"
    private const val ID_BASE = 5000

    /** [savedOnPhoneOnly]: özet telefonda saklandı, sunucuya henüz yüklenemedi. Bildirim izni yoksa sessizce bir şey yapmaz. */
    fun notifyDone(context: Context, request: SummaryOpenRequest, savedOnPhoneOnly: Boolean) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Özetleme bitti", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val key = request.fileId.hashCode()
            val open = PendingIntent.getActivity(
                context,
                key, // dosya başına ayrı istek kodu: her bildirim kendi dosyasını açar
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(SummaryOpenRequest.EXTRA_FILE_ID, request.fileId)
                    .putExtra(SummaryOpenRequest.EXTRA_FILE_NAME, request.fileName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = SummaryDoneMessage.notificationText(request.fileName, savedOnPhoneOnly)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setContentTitle(SummaryDoneMessage.NOTIFICATION_TITLE)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            manager.notify(ID_BASE + (key and 0xFFFF), notification)
        } catch (e: SecurityException) {
            // Bildirim izni sonradan geri alınmış olabilir; özet zaten kaydedildi, yalnızca haber verilemedi.
        }
    }
}
