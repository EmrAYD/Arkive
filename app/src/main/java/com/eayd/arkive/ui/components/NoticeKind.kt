package com.eayd.arkive.ui.components

import com.eayd.arkive.i18n.Lang

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
        // başarı sayılmasın diye hata önce denenir. Her kalıbın ilk satırı Türkçe, ikincisi İngilizce mesajlar içindir.
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

        /** Bildirim metninden türünü çıkarır; bu uygulamanın Türkçe ve İngilizce bildirim kalıplarına göre. */
        fun classify(text: String): NoticeKind {
            // Küçük harfe uygulamanın diliyle çevrilir: Türkçede "I" → "ı" olur, İngilizce metinde bu kalıpları bozardı.
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
