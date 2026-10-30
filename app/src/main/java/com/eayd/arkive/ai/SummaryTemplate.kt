package com.eayd.arkive.ai

/** Özet PDF'inde yazı ailesi: Serif (Times benzeri) ya da Sans (Arial benzeri). Yazı tipleri assets/fonts içindeki Liberation ailesidir. */
enum class PdfFamily { Serif, Sans }

enum class PdfAlign { Left, Center, Justify }

/**
 * Özet PDF'inin görünümü (Ayarlar'dan seçilir). [id] sunucudaki şablon adıyla aynıdır (backend `app/summary_pdf.py`,
 * TEMPLATES); sunucu tanımadığı bir adı varsayılan şablona çevirir. Şablon yalnızca YENİ üretilen özetleri etkiler.
 *
 * Yazı boyutları punto (pt), kenar boşlukları santimetredir. Telefon ([SummaryPdfWriter]) ve sunucu aynı değerleri
 * kullanır; bir şablonu değiştirirken iki tarafı birlikte güncelleyin. Android sınıfı kullanmaz; tek başına test edilebilir.
 */
enum class SummaryTemplate(
    val id: String,
    val label: String,
    val description: String,
    val family: PdfFamily,
    val bodySize: Float,
    val headingSize: Float,
    val titleSize: Float,
    /** Satır aralığı: yazı boyutunun katı. */
    val leading: Float,
    val align: PdfAlign,
    val titleAlign: PdfAlign,
    /** Sol, sağ, üst, alt kenar boşluğu (cm). */
    val marginsCm: List<Float>,
    /** Başlık rengi (ARGB); null: siyah. */
    val accent: Int?,
    /** Başlığın altında çizgi. */
    val titleRule: Boolean,
) {
    Classic(
        "klasik", "Klasik", "Serif yazı tipi; başlıklar kalın 14 punto, metin 12 punto, sola yaslı.",
        PdfFamily.Serif, 12f, 14f, 14f, 1.25f, PdfAlign.Left, PdfAlign.Left, listOf(2.5f, 2.5f, 2.5f, 2.5f), null, true,
    ),
    Academic(
        "akademik", "Akademik", "Serif yazı tipi; 1,5 satır aralığı, iki yana yaslı metin, ortalı başlık.",
        PdfFamily.Serif, 12f, 14f, 14f, 1.5f, PdfAlign.Justify, PdfAlign.Center, listOf(3.0f, 2.5f, 2.5f, 2.5f), null, false,
    ),
    Modern(
        "modern", "Modern", "Sans-serif yazı tipi; büyük mavi başlık ve ince çizgi.",
        PdfFamily.Sans, 11f, 14f, 20f, 1.35f, PdfAlign.Left, PdfAlign.Left, listOf(2.2f, 2.2f, 2.2f, 2.2f), 0xFF1E40AF.toInt(), true,
    ),
    Compact(
        "kompakt", "Kompakt", "Dar kenar boşlukları ve küçük punto; uzun özetler daha az sayfa tutar.",
        PdfFamily.Serif, 10.5f, 13f, 13f, 1.15f, PdfAlign.Left, PdfAlign.Left, listOf(1.8f, 1.8f, 1.8f, 1.8f), null, false,
    ),
    ;

    companion object {
        val Default = Classic

        /** Kayıtlı ad tanınmıyorsa (eski sürüm, bozuk kayıt) varsayılan şablon. */
        fun fromId(id: String?): SummaryTemplate = entries.firstOrNull { it.id == id } ?: Default
    }
}
