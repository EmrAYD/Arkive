package com.eayd.arkive.screens.auth

import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Lang

/**
 * Son Kullanıcı Lisans Sözleşmesi (EULA) metni.
 *
 * Metin, kayıt ekranındaki onay penceresinde ([EulaDialog]) bölüm bölüm gösterilir. Paragraflarda `**kalın**`
 * işareti desteklenir. Metin Türkçe ve İngilizce olarak tutulur; birini değiştirirsen ötekini ve tarihleri de güncelle.
 */
sealed interface EulaBlock {
    /** Bölüm başlığı (ör. "1. LİSANS VERİLMESİ VE KULLANIM KAPSAMI"). */
    data class Heading(val text: String) : EulaBlock

    /** Düz paragraf ya da numaralı madde; `**...**` arası kalın yazılır. */
    data class Paragraph(val text: String) : EulaBlock
}

object EulaContent {
    /** Seçili dildeki başlık. */
    val TITLE: String get() = forLanguage(Lang.app).title

    /** Seçili dildeki metin. Türkçe metin asıldır; İngilizce metin onun çevirisidir. */
    val blocks: List<EulaBlock> get() = forLanguage(Lang.app).blocks

    /** Bir dildeki sözleşme metni. */
    class Text(val title: String, val lastUpdated: String, val blocks: List<EulaBlock>)

    fun forLanguage(language: AppLanguage): Text = when (language) {
        AppLanguage.Turkish -> Turkish
        AppLanguage.English -> English
    }

    private fun h(text: String) = EulaBlock.Heading(text)
    private fun p(text: String) = EulaBlock.Paragraph(text)

    private val Turkish = Text(
        title = "Son Kullanıcı Lisans Sözleşmesi (EULA)",
        lastUpdated = "22 Ekim 2026",
        blocks = turkishBlocks("22 Ekim 2026"),
    )

    private fun turkishBlocks(lastUpdated: String): List<EulaBlock> = listOf(
        p("**Son Güncelleme Tarihi:** $lastUpdated"),
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

    private val English = Text(
        title = "End User License Agreement (EULA)",
        lastUpdated = "October 22, 2026",
        blocks = englishBlocks("October 22, 2026"),
    )

    private fun englishBlocks(lastUpdated: String): List<EulaBlock> = listOf(
        p("**Last Updated:** $lastUpdated"),
        p("Please read this End User License Agreement carefully before using the **Arkive** mobile application."),
        p("This Agreement is a legal agreement between you (the \"User\") and the Arkive development team (the \"Developer\" or \"Service Provider\"). By downloading, installing or using the Application, you are deemed to have accepted all terms of this Agreement."),

        h("1. GRANT OF LICENSE AND SCOPE OF USE"),
        p("1.1. Provided that the User complies with the terms of this Agreement, the Developer grants the User a non-transferable, non-exclusive, limited license to use the Application for personal and non-commercial purposes."),
        p("1.2. All ownership rights, copyrights, source code and intellectual property rights in the Application belong exclusively to the Developer."),
        p("1.3. The User agrees not to copy, decompile, reproduce or share the Application with unauthorized third parties."),

        h("2. PRIVACY, USER AUTHENTICATION AND CONTENT LOCALITY GUARANTEE"),
        p("2.1. **Authentication Service:** **Firebase Authentication** is used to create user accounts securely, to sign in and for authentication. In this context, only the basic account information needed for authentication (email address, user ID/UID, access tokens) is processed on the infrastructure of that cloud service."),
        p("2.2. **Locality of Files and Content:** **No file, document, text, audio recording or image uploaded to Arkive by the User, nor any personal data in such content, is transferred to cloud servers abroad or stored in services abroad**."),
        p("2.3. **No External AI Services:** For file processing, transcription, analysis and summarization, **no data is sent to any third-party AI service or API abroad**."),
        p("2.4. **Local and Dedicated Infrastructure:** All content storage, database management and AI processing take place entirely on the **local/dedicated server infrastructure** under the Developer's control or **on the User's own device**."),

        h("3. AI AND HYBRID FILE PROCESSING ARCHITECTURE"),
        p("3.1. **On-Device (Local) Processing:** Small and short documents may be processed directly with the processing power of the User's mobile device, without being uploaded to the server."),
        p("3.2. **Asynchronous Server-Side Processing:** Large documents, audio recordings and image files are processed through asynchronous job queues running on local servers, to optimize server load and device performance."),
        p("3.3. **Nature of AI Output:** Summaries, text transcriptions and analysis results produced by local AI models are provided for information and convenience only. The output is not guaranteed to be 100% accurate or complete."),

        h("4. STORAGE, SUBSCRIPTIONS AND USER CONTENT"),
        p("4.1. **Responsibility for Files:** The User bears legal responsibility for all content uploaded to their storage. Uploading content that infringes copyright, or that is illegal or harmful, is prohibited."),
        p("4.2. **Subscription Plans:** The Application offers different storage and usage quotas under the Free and Plus plans. The User must stay within the storage limits of the plan they choose."),
        p("4.3. **Trash and Permanent Deletion:** Files deleted by the User are completely and irreversibly deleted from the local storage units at the end of the retention period set in the system (30 days by default)."),

        h("5. SERVICE QUALITY AND LIMITATION OF LIABILITY"),
        p("5.1. The Application is provided \"AS IS\". The Developer does not guarantee uninterrupted service."),
        p("5.2. The Developer cannot be held directly liable for any data loss that may occur due to technical failures, hardware limitations or force majeure. The User is responsible for backing up their critical data."),

        h("6. TERMINATION"),
        p("If the User violates the terms of this Agreement, the Developer has the right to unilaterally suspend or terminate the User's license and access to the account without prior notice."),

        h("7. CONTACT"),
        p("For any questions about this EULA or our data privacy policies, you can contact the Arkive development team."),
    )
}
