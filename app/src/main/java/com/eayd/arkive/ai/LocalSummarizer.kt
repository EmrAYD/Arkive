package com.eayd.arkive.ai

import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Txt
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingMode
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Dosyaları telefondaki yapay zeka modeliyle özetler.
 *
 * Model ilk özetlemede yüklenir, bir dakika kullanılmazsa bellekten çıkarılır.
 */
object LocalSummarizer {

    /** Modelin bir istekte işleyebileceği en çok belirteç (istem ve yanıt birlikte). */
    const val MAX_TOKENS = 4096

    private const val IDLE_CLOSE_MS = 60_000L

    /** Model telefonda hazır mı. */
    val available: Boolean
        get() = LocalModel.state.value == LocalModelState.Ready

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var closer: Job? = null

    /** GPU bir kez başarısız olduysa bir daha denenmez. */
    @Volatile
    private var gpuFailed = false

    /** Metnin tek paragraflık özetini çıkarır. Özet çıkmazsa boş metin döner, model çalışmazsa hata atar. */
    suspend fun summarize(text: String, language: AppLanguage, onPhase: (SummaryPhase) -> Unit = {}): String =
        lock.withLock {
            closer?.cancel()
            try {
                val loaded = engine ?: run {
                    onPhase(SummaryPhase.LoadingModel)
                    withContext(Dispatchers.IO) { open().also { engine = it } }
                }
                LocalSummaryPipeline(
                    language = language,
                    ask = { prompt, maxTokens -> ask(loaded, language, prompt, maxTokens) },
                    onStep = { done, total -> onPhase(SummaryPhase.OnPhone(done, total)) },
                ).summarize(text)
            } finally {
                closer = scope.launch {
                    delay(IDLE_CLOSE_MS)
                    lock.withLock { closeEngine() }
                }
            }
        }

    /** Modeli bellekten çıkarır. */
    fun release() {
        closer?.cancel()
        scope.launch { lock.withLock { closeEngine() } }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
    }

    /** Modeli yükler. Önce GPU, olmazsa CPU denenir; hızlı tahmin açık değilse en son düz CPU. */
    private fun open(): Engine {
        val model = LocalModel.file() ?: throw IllegalStateException(Txt.localAi.notReady)
        val fast = SpeculativeDecodingConfig(mode = SpeculativeDecodingMode.ADAPTIVE)
        val attempts = buildList {
            if (!gpuFailed) add(Backend.GPU() to fast)
            add(Backend.CPU() to fast)
            add(Backend.CPU() to null)
        }
        var last: Exception? = null
        for ((backend, speculative) in attempts) {
            val candidate = Engine(
                EngineConfig(
                    modelPath = model.path,
                    backend = backend,
                    maxNumTokens = MAX_TOKENS,
                    cacheDir = AppSettings.appContext.cacheDir.path,
                    speculativeDecodingConfig = speculative,
                ),
            )
            try {
                candidate.initialize()
                return candidate
            } catch (e: Exception) {
                runCatching { candidate.close() }
                if (backend is Backend.GPU) gpuFailed = true
                last = e
            }
        }
        throw last ?: IllegalStateException(Txt.localAi.runFailed)
    }

    /** Modele tek bir istek gönderir. İş iptal edilirse modele dur denir. */
    private suspend fun ask(engine: Engine, language: AppLanguage, prompt: String, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(LocalSummaryPipeline.systemPrompt(language)),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                    maxOutputToken = maxTokens,
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                ),
            )
            try {
                coroutineScope {
                    val reply = async {
                        try {
                            conversation.sendMessage(prompt).toString()
                        } catch (e: Exception) {
                            if (!isActive) throw CancellationException("Özetleme durduruldu") else throw e
                        }
                    }
                    try {
                        reply.await()
                    } catch (e: CancellationException) {
                        runCatching { conversation.cancelProcess() }
                        throw e
                    }
                }
            } finally {
                runCatching { conversation.close() }
            }
        }
}
