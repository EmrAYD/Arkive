package com.eayd.arkive.screens.auth

/**
 * Son Kullanıcı Lisans Sözleşmesi (EULA) metni.
 *
 * Metin, kayıt ekranındaki onay penceresinde ([EulaDialog]) bölüm bölüm gösterilir. Paragraflarda `**kalın**`
 * işareti desteklenir. Metni değiştirirsen [LAST_UPDATED] değerini de güncelle.
 */
sealed interface EulaBlock {
    /** Bölüm başlığı (ör. "1. LİSANS VERİLMESİ VE KULLANIM KAPSAMI"). */
    data class Heading(val text: String) : EulaBlock

    /** Düz paragraf ya da numaralı madde; `**...**` arası kalın yazılır. */
    data class Paragraph(val text: String) : EulaBlock
}

object EulaContent {
    const val TITLE = "Son Kullanıcı Lisans Sözleşmesi (EULA)"
    const val LAST_UPDATED = "22 Ekim 2026"

    private fun h(text: String) = EulaBlock.Heading(text)
    private fun p(text: String) = EulaBlock.Paragraph(text)

    val blocks: List<EulaBlock> = listOf(
        p("**Son Güncelleme Tarihi:** $LAST_UPDATED"),
        p("Lütfen **Arkive** mobil uygulamasını kullanmadan önce bu Son Kullanıcı Lisans Sözleşmesi'ni dikkatlice okuyunuz."),
        p("Bu Sözleşme, siz (\"Kullanıcı\") ile Arkive geliştirici ekibi (\"Geliştirici\" veya \"Hizmet Sağlayıcı\") arasında yapılan yasal bir anlaşmadır. Uygulamayı indirerek, kurarak veya kullanarak bu Sözleşme’nin tüm hükümlerini kabul etmiş sayılırsınız."),

        h("1. LİSANS VERİLMESİ VE KULLANIM KAPSAMI"),
        p("1.1. Geliştirici, Kullanıcı’ya işbu Sözleşme şartlarına uyması kaydıyla, Uygulama’yı kişisel ve ticari olmayan amaçlarla kullanması için devredilemez, münhasır olmayan, sınırlı bir lisans hakkı tanır."),
        p("1.2. Uygulama’nın tüm mülkiyet, telif, kaynak kodları ve fikri mülkiyet hakları münhasıran Geliştirici’ye aittir."),
        p("1.3. Kullanıcı; Uygulama’yı kopyalamayacağını, kaynak koda dönüştürmeyeceğini, çoğaltmayacağını veya yetkisiz üçüncü taraflarla paylaşmayacağını kabul eder."),

        h("2. GİZLİLİK, KULLANICI KİMLİK DOĞRULAMA VE İÇERİK YERELLİĞİ GÜVENCESİ"),
        p("2.1. **Kimlik Doğrulama Hizmeti:** Kullanıcı hesaplarının güvenli biçimde oluşturulması, oturum açılması ve kimlik doğrulama süreçleri için **Firebase Authentication** altyapısı kullanılmaktadır. Bu kapsamda yalnızca kimlik doğrulama için gerekli temel hesap bilgileri (e-posta adresi, kullanıcı kimliği/UID, erişim jetonları) ilgili bulut hizmeti altyapısında işlenmektedir."),
        p("2.2. **Dosya ve İçerik Verilerinin Yerelliği:** Kullanıcı tarafından Arkive uygulamasına yüklenen **hiçbir dosya, doküman, metin, ses kaydı, görsel veya bu içeriklere ait kişisel veriler yurt dışındaki bulut sunucularına aktarılmaz, yurt dışı servislerde saklanmaz**."),
        p("2.3. **Harici Yapay Zeka Servislerinin Kullanılmaması:** Dosya işleme, transkripsiyon, analiz ve özetleme hizmetlerinde **hiçbir üçüncü taraf yurt dışı yapay zeka servisine/API’sine veri gönderilmez**."),
        p("2.4. **Yerli ve Özelleştirilmiş Altyapı:** Tüm içerik depolama, veritabanı yönetimi ve yapay zeka işlemleri; tamamen Geliştirici’nin denetimindeki **yerel/özgün sunucu altyapısında** veya **Kullanıcı’nın kendi cihazı üzerinde** gerçekleştirilir."),

        h("3. YAPAY ZEKA VE HİBRİT DOSYA İŞLEME MİMARİSİ"),
        p("3.1. **Cihaz Üstü (Local) İşleme:** Düşük boyutlu ve kısa içerikli dokümanlar, sunucuya yüklenmeksizin doğrudan Kullanıcı’nın mobil cihazının işlem gücü kullanılarak işlenebilir."),
        p("3.2. **Sunucu Tarafında Asenkron İşleme:** Büyük boyutlu dokümanlar, ses kayıtları ve görsel dosyalar; sunucu yükünü ve cihaz performansını optimize etmek amacıyla yerel sunucularda çalışan asenkron iş kuyrukları aracılığıyla işlenir."),
        p("3.3. **AI Çıktılarının Niteliği:** Yerel yapay zeka modelleri tarafından üretilen özetler, metin transkripsiyonları ve analiz sonuçları yalnızca bilgilendirme ve kolaylık sağlama amaçlıdır. Çıktıların %100 doğruluğu veya eksiksizliği garanti edilmez."),

        h("4. DEPOLAMA, ABONELİK VE KULLANICI İÇERİKLERİ"),
        p("4.1. **Dosya Sorumluluğu:** Kullanıcı, depolama alanına yüklediği tüm içeriklerin hukuki sorumluluğunu taşır. Telif hakkı ihlali oluşturan, yasadışı veya zararlı içeriklerin yüklenmesi yasaktır."),
        p("4.2. **Abonelik Planları:** Uygulama, Free ve Plus olmak üzere farklı depolama ve kullanım kotaları sunar. Kullanıcı, seçtiği planın sunduğu depolama sınırlarına uymakla yükümlüdür."),
        p("4.3. **Çöp Kutusu ve Kalıcı Silme:** Kullanıcı tarafından silinen dosyalar, sistemde belirlenen saklama süresi (varsayılan 30 gün) sonunda yerel depolama birimlerinden tamamen ve geri döndürülemez şekilde silinir."),

        h("5. HİZMET KALİTESİ VE SORUMLULUĞUN SINIRLANDIRILMASI"),
        p("5.1. Uygulama \"OLDUĞU GİBİ\" sunulmaktadır. Geliştirici, kesintisiz hizmet garantisi vermez."),
        p("5.2. Teknik arızalar, donanım yetersizlikleri veya mücbir sebepler nedeniyle yaşanabilecek olası veri kayıplarından Geliştirici doğrudan sorumlu tutulamaz. Kullanıcı kritik verilerini yedeklemekle yükümlüdür."),

        h("6. SÖZLEŞMENİN FESHİ"),
        p("Kullanıcı’nın işbu Sözleşme hükümlerini ihlal etmesi durumunda, Geliştirici Kullanıcı’nın lisansını ve hesaba erişim hakkını önceden bildirmeksizin tek taraflı olarak askıya alma veya feshetme hakkına sahiptir."),

        h("7. İLETİŞİM"),
        p("Bu EULA metni veya veri gizliliği politikalarımızla ilgili her türlü soru için Arkive geliştirici ekibi ile iletişime geçebilirsiniz."),
    )
}
