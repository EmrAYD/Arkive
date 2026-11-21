package com.eayd.arkive.ai

/** Telefonda özetlenirken uygulama kapanmış bir dosya için, uygulama yeniden açılınca ne yapılacağı ([LocalJobPolicy]). */
enum class LocalResume {
    /** Telefonda yeniden başlatılır ("Yerel"; ilk kez yarıda kaldıysa). */
    OnPhone,

    /** Bulutta sürdürülür (bulut kullanılabiliyor: "Yerel + Bulut" ya da "Bulut"). */
    InCloud,

    /** Telefonda yine yarıda kaldı (bellek yetmiyor olabilir): yeniden denenmez, kullanıcıya söylenir. */
    GiveUp,

    /** Çok eski: sessizce bırakılır. */
    Drop,
}

/**
 * Telefonda özetlenirken uygulama kapanan (süreç sonlandırılan) dosyaların kuralları. Uygulama son uygulamalardan
 * kaydırılınca bazı telefonlar süreci, ön plan servisine rağmen sonlandırır; telefondaki model belleği zorladığında
 * Android da sonlandırabilir. Bu durumda iş yarıda kalır; uygulama yeniden açılınca [decide]'a göre sürdürülür.
 *
 * Android sınıfı kullanmaz; tek başına test edilir.
 */
object LocalJobPolicy {

    /** Bundan eski yarım işler sürdürülmez (kullanıcı unutmuş olabilir; günler sonra kendiliğinden başlamasın). */
    const val RESUME_WINDOW_MS: Long = 24L * 60 * 60 * 1000

    /**
     * Bir dosya telefonda en çok bu kadar kez başlatılır. Telefonun belleği yetmediği için süreç sonlandırıldıysa
     * yeniden denemek her açılışta aynı şeyi yaşatırdı; ikinci kez de yarıda kalınca vazgeçilir.
     */
    const val MAX_PHONE_ATTEMPTS = 2

    /**
     * Yarım iş için karar. [route]: dosya şimdi nerede özetlenirdi ([AiPolicy.route]); [attempts]: telefonda kaç kez
     * başlatıldı; [startedAtMs]: son başlatıldığı an.
     */
    fun decide(route: SummaryRoute, attempts: Int, startedAtMs: Long, nowMs: Long): LocalResume = when {
        nowMs - startedAtMs !in 0..RESUME_WINDOW_MS -> LocalResume.Drop
        // Bulut kullanılabiliyorsa telefonda yeniden denenmez: aynı nedenle yine yarıda kalabilir, bulut kesin biter.
        route != SummaryRoute.PhoneOnly -> LocalResume.InCloud
        attempts < MAX_PHONE_ATTEMPTS -> LocalResume.OnPhone
        else -> LocalResume.GiveUp
    }
}
