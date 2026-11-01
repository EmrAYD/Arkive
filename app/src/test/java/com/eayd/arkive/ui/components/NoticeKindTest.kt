package com.eayd.arkive.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Uygulamanın gerçekten ürettiği bildirim metinlerinin hangi türe düştüğünü sabitler. */
class NoticeKindTest {

    private fun check(kind: NoticeKind, vararg texts: String) {
        for (t in texts) assertEquals("“$t”", kind, NoticeKind.classify(t))
    }

    @Test
    fun `tamamlanan islemler basaridir`() = check(
        NoticeKind.Success,
        "Şifreniz değiştirildi",
        "Çöp kutusu saklama süresi 30 gün olarak kaydedildi",
        "Dosyalar “Ders” klasörüne taşındı",
        "Çöp kutusu boşaltıldı",
        "3 öğe geri yüklendi",
        "2 öğe kalıcı olarak silindi",
        "“not.pdf” İndirilenler/Arkive klasörüne kaydedildi",
        "“ders.docx” özeti sunucuya yüklendi.",
    )

    @Test
    fun `yapilamayan islemler hatadir`() = check(
        NoticeKind.Error,
        "Sunucuya ulaşılamadı. Backend çalışıyor mu?",
        "Sunucu hatası, biraz sonra tekrar deneyin.",
        "İstenen kayıt bulunamadı.",
        "Girilen bilgi geçersiz.",
        "Bu işlem için yetkiniz yok.",
        "Dosya çok büyük ya da depolama alanınız yetersiz.",
        "Sunucu yanıt vermedi.",
        "Oturum doğrulanamadı. Çıkış yapıp tekrar giriş yapın.",
        "Bildirim ayarları açılamadı.",
        "“a.docx” özetlenemedi: Dosya okunamadı (bozuk olabilir).",
        "“a.docx” özeti sunucuya kaydedilemedi.",
        "İstek başarısız oldu (418).",
        "Bu işlem bir çakışma nedeniyle yapılamadı.",
        "Dosya İndirilenler klasörüne yazılamadı.",
        "Yükleme başarısız oldu.",
        "“a.pdf” yüklenemedi.",
    )

    @Test
    fun `dikkat gerektirenler uyaridir`() = check(
        NoticeKind.Warning,
        "Dosyayı indirmek için depolama izni gerekli.",
        "“a.pdf” için özetleme durduruldu.",
        "Bu hesapta şifre değiştirilemez.",
        "Bu dosya türü özetlenemiyor. Metin, PDF ve Word (DOCX, DOC) dosyaları özetlenebilir.",
        "Bu konumda aynı adlı bir klasör zaten var",
    )

    @Test
    fun `olumsuz sozcuk basari sozcugunden once gelir`() {
        // “yüklenemedi” içinde “yüklen…” geçer ama hatadır; “özetlenemedi” de öyle.
        assertEquals(NoticeKind.Error, NoticeKind.classify("“a” özetlenemedi"))
        assertEquals(NoticeKind.Error, NoticeKind.classify("Dosya yüklenemedi"))
    }

    @Test
    fun `digerleri bilgidir`() = check(NoticeKind.Info, "“a.pdf” indiriliyor…", "Hazırlanıyor")

    @Test
    fun `buyuk I harfiyle baslayan mesajlar da dogru taninir`() {
        assertEquals(NoticeKind.Error, NoticeKind.classify("İstek başarısız oldu (500)."))
    }
}
