package com.eayd.arkive.ai

/** Yarıda kalmış telefon özeti için uygulama açılınca ne yapılacağı. */
enum class LocalResume {
    /** Telefonda yeniden başlatılır. */
    OnPhone,

    /** Bulutta sürdürülür. */
    InCloud,

    /** Telefonda yine yarıda kaldı; yeniden denenmez. */
    GiveUp,

    /** Çok eski; sessizce bırakılır. */
    Drop,
}

/**
 * Telefonda özetlenirken uygulama kapanan dosyaların kuralları.
 *
 * Bazı telefonlar uygulama kaydırılınca süreci kapatır; model belleği zorlarsa Android da kapatabilir.
 */
object LocalJobPolicy {

    /** Bundan eski yarım işler sürdürülmez. */
    const val RESUME_WINDOW_MS: Long = 24L * 60 * 60 * 1000

    /** Bir dosya telefonda en çok bu kadar kez başlatılır; bellek yetmiyorsa her açılışta aynı şey olmasın. */
    const val MAX_PHONE_ATTEMPTS = 2

    /** Yarım iş için karar: [route] dosyanın şimdi nerede özetleneceği, [attempts] telefonda kaç kez başlatıldığı. */
    fun decide(route: SummaryRoute, attempts: Int, startedAtMs: Long, nowMs: Long): LocalResume = when {
        nowMs - startedAtMs !in 0..RESUME_WINDOW_MS -> LocalResume.Drop
        route != SummaryRoute.PhoneOnly -> LocalResume.InCloud
        attempts < MAX_PHONE_ATTEMPTS -> LocalResume.OnPhone
        else -> LocalResume.GiveUp
    }
}
