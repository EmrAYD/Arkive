package com.eayd.arkive.ui.components

import java.util.Locale

/**
 * Kısa bildirimin türü: kartın simgesini ve rengini belirler. Hiçbir Android/Compose sınıfı kullanmaz; bu yüzden tek
 * başına test edilebilir (bkz. NoticeKindTest).
 */
enum class NoticeKind {
    /** İşlem tamamlandı (taşındı, silindi, kaydedildi…). */
    Success,

    /** İşlem yapılamadı (ağ/sunucu hatası, reddedilen işlem…). */
    Error,

    /** Dikkat edilecek bir durum (izin gerekli, özetleme durduruldu, desteklenmeyen tür…). */
    Warning,

    /** Nötr bilgi (indiriliyor…). */
    Info,
    ;

    companion object {
        // Öncelik sırası: hata > uyarı > başarı > bilgi. "yüklenemedi" gibi olumsuz bir sözcük, içindeki "yüklen" yüzünden
        // başarı sayılmasın diye hata önce denenir.
        private val errorPattern = Regex(
            "amad[ıi]|emedi|başarısız|hata|geçersiz|yetkiniz yok|yanıt vermedi|çok büyük|yetersiz|çakışma|hizmet veremiyor",
        )
        private val warningPattern = Regex(
            "durduruldu|emez\\b|amaz\\b|emiyor|amıyor|gerekli|zaten|iptal",
        )
        private val successPattern = Regex(
            "taşındı|silindi|silinmiş|yüklendi|kaydedildi|değiştirildi|boşaltıldı|indirildi|eklendi|özetlendi|oluşturuldu|" +
                "güncellendi|gönderildi|kopyalandı|yenilendi|geri yüklendi|olarak kaydedildi",
        )

        /** Bildirim metninden türünü çıkarır; bu uygulamanın Türkçe bildirim kalıplarına göre. */
        fun classify(text: String): NoticeKind {
            val t = text.lowercase(Locale.forLanguageTag("tr"))
            return when {
                errorPattern.containsMatchIn(t) -> Error
                warningPattern.containsMatchIn(t) -> Warning
                successPattern.containsMatchIn(t) -> Success
                else -> Info
            }
        }
    }
}
