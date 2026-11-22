package com.eayd.arkive.ai

import com.eayd.arkive.i18n.Txt

/** "Özetleme bitti" haberinin metinleri. */
object SummaryDoneMessage {

    val NOTIFICATION_TITLE: String get() = Txt.ai.summaryDoneTitle

    val TAP_HINT: String get() = Txt.ai.tapToView

    fun fileLine(fileName: String): String = Txt.ai.summarizedFile(fileName)

    /** Kartın ayrıntı satırı. [savedOnPhoneOnly] özetin henüz sunucuya yüklenmediğini söyler. */
    fun detail(savedOnPhoneOnly: Boolean): String =
        if (savedOnPhoneOnly) {
            Txt.ai.savedOnPhoneOnly
        } else {
            Txt.ai.alsoSavedToAiResults
        }

    /** Sistem bildiriminin metni. */
    fun notificationText(fileName: String, savedOnPhoneOnly: Boolean): String =
        if (savedOnPhoneOnly) {
            Txt.ai.notificationTextSavedOnPhone(fileName)
        } else {
            Txt.ai.notificationText(fileName)
        }
}

/** "Özetleme tamamlanamadı" bildiriminin metinleri. */
object SummaryFailedMessage {

    val TITLE: String get() = Txt.ai.failedNotificationTitle

    val PASSWORD_TITLE: String get() = Txt.ai.passwordNeededTitle

    fun passwordText(fileName: String): String = Txt.ai.passwordNeededText(fileName)

    /** Bildirim metni. Hatada dosyanın adı geçmiyorsa başına eklenir. */
    fun text(fileName: String, message: String): String = when {
        message.isBlank() -> Txt.ai.failedPlain(fileName)
        fileName.isNotEmpty() && message.contains(fileName) -> message
        else -> Txt.ai.failedNotificationText(fileName, message)
    }
}

/** Haberin uygulama içinde mi bildirimle mi verileceği. */
object SummaryDoneDelivery {

    /** Uygulama ekranda değilse ya da mesajı gösterecek liste açık değilse bildirim kullanılır. */
    fun useNotification(appVisible: Boolean, listeners: Int): Boolean = !appVisible || listeners <= 0
}

/** Bildirime dokunulunca açılacak özet. */
data class SummaryOpenRequest(val fileId: String, val fileName: String) {
    companion object {
        const val EXTRA_FILE_ID = "com.eayd.arkive.extra.SUMMARY_FILE_ID"
        const val EXTRA_FILE_NAME = "com.eayd.arkive.extra.SUMMARY_FILE_NAME"

        /** Intent'teki bilgilerden istek; kimlik yoksa null. */
        fun fromExtras(fileId: String?, fileName: String?): SummaryOpenRequest? =
            if (fileId.isNullOrBlank()) null else SummaryOpenRequest(fileId, fileName.orEmpty())
    }
}
