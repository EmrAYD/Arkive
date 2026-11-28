package com.eayd.arkive.ai

import android.content.Context
import com.eayd.arkive.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Seslendirilen özet. */
class PlaybackSession(
    val fileId: String,
    val fileName: String,
    val text: String,
    /** Özetin altındaki ek not; pencere bildirimden yeniden açılınca gösterilir. */
    val note: String?,
    val speaker: SummarySpeaker,
)

/** Bildirimin gösterdiği an: hangi özet, hangi durumda. */
data class PlaybackNow(val session: PlaybackSession, val state: SpeechState)

/**
 * Sesli okunan özet. Okuma başlayınca bildirim ve kilit ekranı denetimleri çıkar; özet penceresi kapansa da okuma sürer.
 *
 * Bir anda tek özet okunur. Başka bir özette "Dinle"ye basılınca önceki kapanır. Duraklatılmış okuma bir süre sonra
 * kendiliğinden kapanır. Bütün işlevler ana iş parçacığında çağrılır.
 */
object SummaryPlayback {

    /** Duraklatılmış okuma bu kadar beklerse bildirim kalkar (ms). */
    private const val IDLE_TIMEOUT_MS = 10L * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var idleTimer: Job? = null

    /** Açık özet penceresinin seslendiricisi. */
    private var attached: SummarySpeaker? = null

    private val _now = MutableStateFlow<PlaybackNow?>(null)

    /** Okunan özet ve durumu; okuma yoksa null. */
    val now: StateFlow<PlaybackNow?> = _now.asStateFlow()

    /** Okunan özet; okuma yoksa null. */
    val session: PlaybackSession?
        get() = _now.value?.session

    /** Özet penceresi için seslendirici: bu özet zaten okunuyorsa onu, değilse yenisini verir. */
    fun speakerFor(context: Context, fileId: String, fileName: String, text: String, note: String?): SummarySpeaker {
        val current = session
        val speaker = if (current != null && current.fileId == fileId && current.text == text) {
            current.speaker
        } else {
            val appContext = context.applicationContext
            SummarySpeaker(appContext, text, AppSettings.summaryLanguage.value) { started ->
                activate(appContext, PlaybackSession(fileId, fileName, text, note, started))
            }
        }
        attached = speaker
        return speaker
    }

    /** Özet penceresi kapandı. Hiç başlatılmayan seslendirici kapatılır; okunan özet bildirimden yönetilmeye devam eder. */
    fun detach(speaker: SummarySpeaker) {
        if (attached === speaker) attached = null
        val current = session
        when {
            current?.speaker !== speaker -> speaker.release()
            speaker.state.value.status.let { it == SpeechStatus.Idle || it == SpeechStatus.Unavailable } -> stop()
        }
    }

    fun play() {
        session?.speaker?.play()
    }

    fun pause() {
        session?.speaker?.pause()
    }

    fun toggle() {
        session?.speaker?.toggle()
    }

    fun cycleSpeed() {
        session?.speaker?.cycleSpeed()
    }

    /** Okumayı bitirir ve bildirimi kaldırır. Özet penceresi açıksa seslendirici yalnızca duraklatılır. */
    fun stop() {
        val current = session ?: return
        watcher?.cancel()
        idleTimer?.cancel()
        watcher = null
        idleTimer = null
        _now.value = null
        if (current.speaker === attached) current.speaker.pause() else current.speaker.release()
    }

    private fun activate(context: Context, started: PlaybackSession) {
        val current = session
        if (current?.speaker === started.speaker) return
        watcher?.cancel()
        idleTimer?.cancel()
        idleTimer = null
        if (current != null) {
            if (current.speaker !== attached) current.speaker.release() else current.speaker.pause()
        }
        _now.value = PlaybackNow(started, started.speaker.state.value)
        watcher = scope.launch {
            started.speaker.state.collect { state ->
                _now.value = PlaybackNow(started, state)
                onStatus(started, state.status)
            }
        }
        SummaryPlaybackService.start(context)
    }

    /** Okuma bitince ya da duraklayınca bildirimin ne kadar kalacağına karar verir. */
    private fun onStatus(current: PlaybackSession, status: SpeechStatus) {
        when (status) {
            SpeechStatus.Playing, SpeechStatus.Preparing -> {
                idleTimer?.cancel()
                idleTimer = null
            }
            SpeechStatus.Paused -> startIdleTimer(current)
            SpeechStatus.Idle, SpeechStatus.Unavailable ->
                if (current.speaker === attached) startIdleTimer(current) else stop()
        }
    }

    private fun startIdleTimer(current: PlaybackSession) {
        if (idleTimer?.isActive == true) return
        idleTimer = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (session === current) stop()
        }
    }
}
