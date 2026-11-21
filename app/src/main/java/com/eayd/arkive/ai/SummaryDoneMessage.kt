package com.eayd.arkive.ai

import com.eayd.arkive.i18n.Txt

/**
 * "Özetleme bitti" haberinin metinleri ve nasıl iletileceği. Hiçbir Android sınıfı kullanmaz; bu yüzden tek başına
 * test edilebilir (bkz. SummaryDoneMessageTest).
 *
 * Uygulama açıkken özet doğrudan pencerede açılır. Açılamazsa (o sırada başka bir pencere açıksa) liste ekranında bir
 * kart (SummaryDoneBanner), arka plandayken ya da kapalıyken sistem bildirimi ([SummaryNotifier]) çıkar; ikisine de
 * dokununca özet penceresi açılır. Kullanıcı bunu bilsin diye metinlerin kendisi de bunu söyler.
 */
object SummaryDoneMessage {

    /** Uygulama içi kartın ve sistem bildiriminin başlığı. */
    val NOTIFICATION_TITLE: String get() = Txt.ai.summaryDoneTitle

    /** Kartın tıklanabilir olduğunu söyleyen satır: dokununca özet açılır. */
    val TAP_HINT: String get() = Txt.ai.tapToView

    /** Kartın dosya satırı. */
    fun fileLine(fileName: String): String = Txt.ai.summarizedFile(fileName)

    /**
     * Kartın ayrıntı satırı. [savedOnPhoneOnly]: özet telefonda üretildi ama sunucuya henüz yüklenemedi (internet yok);
     * bekleyen özet telefondaki kopyadan açılır ve bağlantı gelince yüklenir.
     */
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

/** "Özetleme bitti" haberinin hangi yoldan verileceği. */
object SummaryDoneDelivery {

    /**
     * Sistem bildirimi mi gösterilsin? Uygulama ekranda değilse evet. Ekrandaysa da, mesajı gösterecek bir ekran (liste)
     * açık değilse (örneğin bir dosyanın ön izlemesindeyken) evet; yoksa haber hiç görünmezdi.
     * [listeners]: şu an mesajı dinleyen ekran sayısı.
     */
    fun useNotification(appVisible: Boolean, listeners: Int): Boolean = !appVisible || listeners <= 0
}

/**
 * Bildirime dokunulunca açılacak özet: hangi dosyanın özeti. Bildirimin Intent'inde iki metin olarak taşınır
 * ([EXTRA_FILE_ID], [EXTRA_FILE_NAME]); dosyanın kendisi (FileDto) o sırada elde olmayabilir, bu yüzden yalnızca
 * kimlik ve ad yeter.
 */
data class SummaryOpenRequest(val fileId: String, val fileName: String) {
    companion object {
        const val EXTRA_FILE_ID = "com.eayd.arkive.extra.SUMMARY_FILE_ID"
        const val EXTRA_FILE_NAME = "com.eayd.arkive.extra.SUMMARY_FILE_NAME"

        /** Intent'ten okunan değerlerden istek; kimlik yoksa (sıradan bir açılış) null. */
        fun fromExtras(fileId: String?, fileName: String?): SummaryOpenRequest? =
            if (fileId.isNullOrBlank()) null else SummaryOpenRequest(fileId, fileName.orEmpty())
    }
}
