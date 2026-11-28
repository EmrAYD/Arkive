package com.eayd.arkive.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.eayd.arkive.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Seslendiricinin durumu. */
enum class SpeechStatus {
    /** Henüz başlatılmadı ya da sona geldi. */
    Idle,

    /** Ses motoru açılıyor. */
    Preparing,

    Playing,
    Paused,

    /** Ses kullanılamıyor; nedeni [SpeechState.problem]. */
    Unavailable,
}

/** Seslendirmenin neden yapılamadığı. */
enum class SpeechProblem { NoEngine, NoVoice, Failed }

data class SpeechState(
    val status: SpeechStatus = SpeechStatus.Idle,
    /** Okunan ya da sıradaki parça. */
    val sentence: Int = 0,
    val count: Int = 0,
    val speed: Float = 1f,
    val problem: SpeechProblem? = null,
)

/**
 * Özeti telefonun metin okuma motoruyla sesli okur.
 *
 * Metin cümlelere bölünüp sırayla okunur; duraklatınca okunan cümlenin başından sürer. Başka bir ses (arama, müzik)
 * gelince ya da kulaklık çıkarılınca durur.
 */
class SummarySpeaker(context: Context, private val text: String, fallback: AppLanguage) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val audio = appContext.getSystemService(AudioManager::class.java)

    /** Okunacak parçaların metindeki yerleri. */
    val sentences: List<IntRange> = SpeechText.sentences(text)

    /** Özetin dili; ses buna göre seçilir. */
    val language: AppLanguage = SpeechText.languageOf(text, fallback)

    private val _state = MutableStateFlow(
        SpeechState(count = sentences.size, speed = prefs.getFloat(KEY_SPEED, 1f).takeIf { it in SpeechText.SPEEDS } ?: 1f),
    )
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var playWhenReady = false
    private var resumeOnFocus = false
    private var released = false

    /** Her yeni okumada artar; eski okumadan gelen geri bildirimler yok sayılır. */
    @Volatile
    private var generation = 0

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (_state.value.status == SpeechStatus.Playing) {
                stopSpeaking(SpeechStatus.Paused, keepFocus = true)
                resumeOnFocus = true
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocus) {
                resumeOnFocus = false
                play()
            }
        }
    }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else {
        null
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    private var noisyRegistered = false

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            val index = indexOf(utteranceId) ?: return
            _state.update { it.copy(status = SpeechStatus.Playing, sentence = index) }
        }

        override fun onDone(utteranceId: String) {
            val index = indexOf(utteranceId) ?: return
            if (index == sentences.lastIndex) {
                generation++
                quiet()
                _state.update { it.copy(status = SpeechStatus.Idle, sentence = 0) }
            }
        }

        @Deprecated("Eski Android sürümleri bunu çağırır.")
        override fun onError(utteranceId: String) = failed(utteranceId)

        override fun onError(utteranceId: String, errorCode: Int) = failed(utteranceId)
    }

    /** Okumayı başlatır ya da duraklatılan yerden sürdürür. Ses motoru ilk kez burada açılır. */
    fun play() {
        if (released || sentences.isEmpty()) return
        when (_state.value.status) {
            SpeechStatus.Playing, SpeechStatus.Preparing -> return
            SpeechStatus.Unavailable -> if (_state.value.problem != SpeechProblem.Failed) return
            else -> Unit
        }
        if (!ready) {
            playWhenReady = true
            _state.update { it.copy(status = SpeechStatus.Preparing, problem = null) }
            if (tts == null) {
                val main = Handler(Looper.getMainLooper())
                tts = TextToSpeech(appContext) { status -> main.post { onInit(status) } }
            }
            return
        }
        speakFrom(_state.value.sentence)
    }

    /** Okumayı duraklatır; aynı cümleden sürer. */
    fun pause() {
        playWhenReady = false
        if (_state.value.status == SpeechStatus.Playing || _state.value.status == SpeechStatus.Preparing) {
            stopSpeaking(SpeechStatus.Paused, keepFocus = false)
        }
    }

    fun toggle() = if (_state.value.status == SpeechStatus.Playing) pause() else play()

    /** Bir önceki cümleye geçer. */
    fun previous() = jumpTo(_state.value.sentence - 1)

    /** Bir sonraki cümleye geçer. */
    fun next() = jumpTo(_state.value.sentence + 1)

    /** Okuma hızını sıradakiyle değiştirir; seçim sonraki özetlerde de kullanılır. */
    fun cycleSpeed() {
        val speed = SpeechText.nextSpeed(_state.value.speed)
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
        _state.update { it.copy(speed = speed) }
        tts?.setSpeechRate(speed)
        if (_state.value.status == SpeechStatus.Playing) speakFrom(_state.value.sentence)
    }

    /** Ses motorunu kapatır; pencere kapanınca çağrılır. */
    fun release() {
        released = true
        generation++
        quiet()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /** Ses motoru açıldı ya da açılamadı; ana iş parçacığında çağrılır. */
    private fun onInit(status: Int) {
        if (released) return
        val engine = tts
        if (engine == null || status != TextToSpeech.SUCCESS) {
            _state.update { it.copy(status = SpeechStatus.Unavailable, problem = SpeechProblem.NoEngine) }
            return
        }
        val result = engine.setLanguage(language.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            _state.update { it.copy(status = SpeechStatus.Unavailable, problem = SpeechProblem.NoVoice) }
            return
        }
        engine.setAudioAttributes(attributes)
        engine.setSpeechRate(_state.value.speed)
        engine.setOnUtteranceProgressListener(progress)
        ready = true
        _state.update { it.copy(status = SpeechStatus.Idle) }
        if (playWhenReady) {
            playWhenReady = false
            speakFrom(_state.value.sentence)
        }
    }

    private fun jumpTo(index: Int) {
        val target = index.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        when (_state.value.status) {
            SpeechStatus.Playing -> speakFrom(target)
            SpeechStatus.Idle -> _state.update { it.copy(sentence = target, status = SpeechStatus.Paused) }
            else -> _state.update { it.copy(sentence = target) }
        }
    }

    private fun speakFrom(index: Int) {
        val engine = tts ?: return
        if (!requestFocus()) return
        listenForNoisy()
        val run = ++generation
        engine.stop()
        for (i in index until sentences.size) {
            val range = sentences[i]
            val mode = if (i == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text.substring(range.first, range.last + 1), mode, null, "$run:$i")
        }
        _state.update { it.copy(status = SpeechStatus.Playing, sentence = index, problem = null) }
    }

    private fun stopSpeaking(status: SpeechStatus, keepFocus: Boolean) {
        generation++
        tts?.stop()
        if (!keepFocus) quiet()
        _state.update { it.copy(status = status) }
    }

    private fun failed(utteranceId: String) {
        indexOf(utteranceId) ?: return
        generation++
        quiet()
        _state.update { it.copy(status = SpeechStatus.Unavailable, problem = SpeechProblem.Failed) }
    }

    /** Bu okumaya ait parçanın sırası; eski bir okumadansa null. */
    private fun indexOf(utteranceId: String): Int? {
        val run = utteranceId.substringBefore(':').toIntOrNull() ?: return null
        if (run != generation) return null
        return utteranceId.substringAfter(':').toIntOrNull()
    }

    /** Ses odağını ve kulaklık dinleyicisini bırakır. */
    private fun quiet() {
        resumeOnFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audio?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audio?.abandonAudioFocus(focusListener)
        }
        if (noisyRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
    }

    private fun requestFocus(): Boolean {
        val manager = audio ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.requestAudioFocus(requireNotNull(focusRequest))
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun listenForNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private companion object {
        const val PREFS = "summary_speech"
        const val KEY_SPEED = "speed"
    }
}
