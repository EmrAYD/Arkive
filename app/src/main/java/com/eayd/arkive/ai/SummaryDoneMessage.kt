package com.eayd.arkive.ai

/**
 * "Özetleme bitti" haberinin metinleri ve nasıl iletileceği. Hiçbir Android sınıfı kullanmaz; bu yüzden tek başına
 * test edilebilir (bkz. SummaryDoneMessageTest).
 *
 * Haber iki yoldan gelir ve ikisi de dokunulabilir: uygulama açıkken liste ekranında bir kart (SummaryDoneBanner), arka plandayken
 * ya da kapalıyken sistem bildirimi ([SummaryNotifier]). Dokununca özet açılır (özetin PDF dosyasının ön izlemesi, dosya
 * yoksa özet penceresi). Kullanıcı bunu bilsin diye metinlerin kendisi de bunu söyler.
 */
object SummaryDoneMessage {

    /** Uygulama içi kartın ve sistem bildiriminin başlığı. */
    const val NOTIFICATION_TITLE = "Özetleme bitti"

    /** Kartın tıklanabilir olduğunu söyleyen satır: dokununca özet açılır. */
    const val TAP_HINT = "Özeti görmek için dokunun"

    /** Kartın dosya satırı. */
    fun fileLine(fileName: String): String = "“$fileName” özetlendi"

    /**
     * Kartın ayrıntı satırı. [savedOnPhoneOnly]: özet telefonda üretildi ama sunucuya henüz yüklenemedi (internet yok);
     * bekleyen özet telefondaki kopyadan açılır ve bağlantı gelince yüklenir.
     */
    fun detail(savedOnPhoneOnly: Boolean): String =
        if (savedOnPhoneOnly) {
            "Sunucuya ulaşılamadı; özet telefonda saklandı, bağlantı gelince yüklenecek."
        } else {
            "Özet, AI Sonuçlarım sayfasına da kaydedildi."
        }

    /** Sistem bildiriminin metni. */
    fun notificationText(fileName: String, savedOnPhoneOnly: Boolean): String =
        if (savedOnPhoneOnly) {
            "“$fileName” özetlendi ama sunucuya ulaşılamadı; özet telefonda saklandı, bağlantı gelince yüklenecek. " +
                "Özeti görmek için dokunun."
        } else {
            "“$fileName” özetlendi. Özet, AI Sonuçlarım sayfasına da kaydedildi. Özeti görmek için dokunun."
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
