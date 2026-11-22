package com.eayd.arkive.ai

import kotlin.math.exp

/** Özetlemenin aşaması. */
sealed interface SummaryPhase {
    /** Dosya okunuyor ya da indiriliyor. */
    data object Reading : SummaryPhase

    /** Model belleğe yükleniyor. */
    data object LoadingModel : SummaryPhase

    /** Telefonda özetleniyor: [total] adımın [step]. adımı. */
    data class OnPhone(val step: Int, val total: Int) : SummaryPhase

    /** Sunucuda özetleniyor; büyük dosya daha uzun sürer. */
    data class InCloud(val sizeBytes: Long) : SummaryPhase
}

/** Bir aşamanın başladığı an ve o andaki yüzde. */
data class PhaseRun(val phase: SummaryPhase, val startPercent: Int, val startedAtMs: Long)

/**
 * Özetlemenin tahmini yüzdesi.
 *
 * Model de sunucu da ilerleme bildirmez; yüzde aşamalardan ve beklenen sürelerden hesaplanır. Hiç geri gitmez, iş
 * bitmeden [MAX]'ı geçmez.
 */
object SummaryProgress {

    /** İş bitmeden gösterilebilecek en yüksek yüzde. */
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

    /** Aşamanın beklenen süresi; yüzde bu sürede aralığın çoğunu geçer. */
    fun expectedMs(phase: SummaryPhase): Long = when (phase) {
        SummaryPhase.Reading -> 3_000
        SummaryPhase.LoadingModel -> 10_000
        is SummaryPhase.OnPhone -> 20_000
        is SummaryPhase.InCloud -> (15_000 + phase.sizeBytes / (1024 * 1024) * 10_000).coerceIn(15_000, 180_000)
    }

    /** Yeni aşamayı başlatır; yüzde geri gitmesin diye o anki yüzdeden devam eder. */
    fun start(phase: SummaryPhase, currentPercent: Int, nowMs: Long): PhaseRun =
        PhaseRun(phase, maxOf(currentPercent, range(phase).first).coerceIn(0, MAX), nowMs)

    /** Aşamanın şu anki yüzdesi. */
    fun percent(run: PhaseRun, nowMs: Long): Int {
        val to = range(run.phase).last
        if (run.startPercent >= to) return run.startPercent
        val elapsed = (nowMs - run.startedAtMs).coerceAtLeast(0)
        val share = 1 - exp(-2.0 * elapsed / expectedMs(run.phase))
        return (run.startPercent + ((to - run.startPercent) * share).toInt()).coerceIn(run.startPercent, to)
    }
}
