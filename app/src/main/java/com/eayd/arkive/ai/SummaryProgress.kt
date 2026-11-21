package com.eayd.arkive.ai

import kotlin.math.exp

/** Özetlemenin aşaması; dosya satırındaki yüzde buna göre ilerler ([SummaryProgress]). */
sealed interface SummaryPhase {
    /** Hazırlık ve dosyanın okunması (gerekirse indirilmesi). */
    data object Reading : SummaryPhase

    /** Telefondaki yapay zeka modeli belleğe yükleniyor (yalnızca ilk özetlemede birkaç saniye). */
    data object LoadingModel : SummaryPhase

    /** Telefonda özetleniyor: toplam [total] adımın [step]. adımı (uzun belgede bölümler + birleştirme; kısada 1/1). */
    data class OnPhone(val step: Int, val total: Int) : SummaryPhase

    /** Sunucuda özetleniyor. [sizeBytes]: dosyanın boyutu (büyük dosya daha uzun sürer); bilinmiyorsa 0. */
    data class InCloud(val sizeBytes: Long) : SummaryPhase
}

/** Bir aşamanın başladığı an ve o andaki yüzde. */
data class PhaseRun(val phase: SummaryPhase, val startPercent: Int, val startedAtMs: Long)

/**
 * Özetlemenin yüzdesi. Ne telefondaki model ne sunucu bir isteğin içinde ne kadar ilerlediğini söyler; bu yüzden yüzde,
 * aşamalardan (okuma, model yükleme, telefondaki adımlar, bulut) ve her aşamanın beklenen süresinden tahmin edilir:
 * her aşamanın bir yüzde aralığı vardır, aşama sürdükçe yüzde aralığın sonuna yaklaşır ama varmaz (aşama beklenenden
 * uzun sürerse yavaşlar, durmaz gibi görünmez). Aşama bitince sonraki aşamanın aralığına geçilir. Yüzde hiç geri gitmez
 * (ör. telefonda özetlenemeyen dosya buluta geçerken) ve özetleme bitene kadar [MAX]'ı aşmaz.
 *
 * Android sınıfı kullanmaz; tek başına test edilir.
 */
object SummaryProgress {

    /** Özetleme bitmeden gösterilebilecek en yüksek yüzde. */
    const val MAX = 98

    private const val PHONE_FROM = 15

    /** Aşamanın yüzde aralığı. */
    fun range(phase: SummaryPhase): IntRange = when (phase) {
        SummaryPhase.Reading -> 0..6
        SummaryPhase.LoadingModel -> 6..PHONE_FROM
        is SummaryPhase.OnPhone -> {
            val total = phase.total.coerceAtLeast(1)
            val step = phase.step.coerceIn(1, total)
            val span = MAX - PHONE_FROM
            (PHONE_FROM + span * (step - 1) / total)..(PHONE_FROM + span * step / total)
        }
        is SummaryPhase.InCloud -> 6..MAX
    }

    /** Aşamanın beklenen süresi (ms): yüzde bu sürede aralığın ~%85'ine gelir. */
    fun expectedMs(phase: SummaryPhase): Long = when (phase) {
        SummaryPhase.Reading -> 3_000
        SummaryPhase.LoadingModel -> 10_000
        // Telefonda bir bölüm (~7000 karakter) ya da birleştirme: GPU'da ~10 sn, CPU'da daha uzun.
        is SummaryPhase.OnPhone -> 20_000
        // Sunucu: büyük dosyada daha uzun (sayfa görüntüleri, çok bölüm); 15 sn ile 3 dk arası.
        is SummaryPhase.InCloud -> (15_000 + phase.sizeBytes / (1024 * 1024) * 10_000).coerceIn(15_000, 180_000)
    }

    /** [phase] başlıyor: o anki yüzde [currentPercent] (ilk aşamada 0). Yüzde geri gitmesin diye başlangıç ikisinin büyüğüdür. */
    fun start(phase: SummaryPhase, currentPercent: Int, nowMs: Long): PhaseRun =
        PhaseRun(phase, maxOf(currentPercent, range(phase).first).coerceIn(0, MAX), nowMs)

    /** [run] aşaması [nowMs] anında yüzde kaçta. */
    fun percent(run: PhaseRun, nowMs: Long): Int {
        val to = range(run.phase).last
        if (run.startPercent >= to) return run.startPercent
        val elapsed = (nowMs - run.startedAtMs).coerceAtLeast(0)
        val share = 1 - exp(-2.0 * elapsed / expectedMs(run.phase))
        return (run.startPercent + ((to - run.startPercent) * share).toInt()).coerceIn(run.startPercent, to)
    }
}
