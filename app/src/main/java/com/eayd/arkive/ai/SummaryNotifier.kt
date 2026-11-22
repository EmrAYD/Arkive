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
import com.eayd.arkive.i18n.Txt

/** Özet bittiğinde ya da tamamlanamadığında gösterilen sistem bildirimleri. Her dosyanın tek bildirimi olur. */
object SummaryNotifier {

    private const val CHANNEL_ID = "ai_summary_done"
    private const val FAILED_CHANNEL_ID = "ai_summary_failed"
    private const val ACTION_FAILED = "com.eayd.arkive.action.SUMMARY_FAILED"
    private const val ID_BASE = 5000

    /** Özet hazır bildirimi; dokununca özet açılır. Bildirim izni yoksa bir şey yapmaz. */
    fun notifyDone(context: Context, request: SummaryOpenRequest, savedOnPhoneOnly: Boolean) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, Txt.ai.summaryDoneTitle, NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val key = request.fileId.hashCode()
            val open = PendingIntent.getActivity(
                context,
                key,
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
        } catch (e: SecurityException) {}
    }

    /** Özet tamamlanamadı bildirimi; dokununca uygulama açılır. */
    fun notifyFailed(context: Context, fileId: String, title: String, text: String) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(FAILED_CHANNEL_ID, Txt.ai.summaryFailedChannel, NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val key = fileId.hashCode()
            val open = PendingIntent.getActivity(
                context,
                key,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_FAILED)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, FAILED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
            manager.notify(ID_BASE + (key and 0xFFFF), notification)
        } catch (e: SecurityException) {}
    }
}
