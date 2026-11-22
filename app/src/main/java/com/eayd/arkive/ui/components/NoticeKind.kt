package com.eayd.arkive.ui.components

import com.eayd.arkive.i18n.Lang

/** Kısa bildirimin türü; kartın simgesini ve rengini belirler. */
enum class NoticeKind {
    /** İşlem tamamlandı. */
    Success,

    /** İşlem yapılamadı. */
    Error,

    /** Dikkat edilecek bir durum. */
    Warning,

    /** Bilgi. */
    Info,
    ;

    companion object {
        private val errorPattern = Regex(
            "amad[ıi]|emedi|başarısız|hata|geçersiz|yetkiniz yok|yanıt vermedi|çok büyük|yetersiz|çakışma|hizmet veremiyor|" +
                "couldn't|could not|failed|error|invalid|don't have permission|didn't respond|too large|not enough|" +
                "conflict|can't handle|wasn't found|incorrect|wrong|corrupt",
        )
        private val warningPattern = Regex(
            "durduruldu|emez\\b|amaz\\b|emiyor|amıyor|gerekli|zaten|iptal|" +
                "stopped|can't|cannot|isn't|aren't|\\bnot\\b|needed|required|already|cancel",
        )
        private val successPattern = Regex(
            "taşındı|silindi|silinmiş|yüklendi|kaydedildi|değiştirildi|boşaltıldı|indirildi|eklendi|özetlendi|oluşturuldu|" +
                "güncellendi|gönderildi|kopyalandı|yenilendi|geri yüklendi|olarak kaydedildi|" +
                "moved|deleted|uploaded|saved|changed|emptied|downloaded|added|summarized|created|updated|sent|copied|" +
                "restored|switched",
        )

        /** Bildirim metninden türünü çıkarır. */
        fun classify(text: String): NoticeKind {
            val t = text.lowercase(Lang.locale)
            return when {
                errorPattern.containsMatchIn(t) -> Error
                warningPattern.containsMatchIn(t) -> Warning
                successPattern.containsMatchIn(t) -> Success
                else -> Info
            }
        }
    }
}
