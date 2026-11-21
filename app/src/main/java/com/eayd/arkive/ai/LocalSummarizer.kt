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
 * Telefonda, cihazdaki yapay zeka modeliyle özetleme ([LocalModel]: Gemma 4 E2B). Yapay zeka işlem yeri "Yerel"ken
 * (ücretsiz planda her zaman) tüm özetler, "Yerel + Bulut"ta küçük dosyaların özetleri burada çıkarılır ([AiPolicy.route];
 * ücretsiz plan sınırı: en çok [com.eayd.arkive.data.PlusPlan.FREE_SUMMARY_LIMIT] özet, sunucu da kaydederken denetler).
 * Dosya internete gönderilmez; yalnızca çıkan özet sunucuya kaydedilir.
 *
 * Modeli Google'ın LiteRT-LM çalıştırıcısı çalıştırır. Önce telefonun grafik işlemcisi (GPU) denenir: hem daha hızlıdır
 * hem ana bellekten daha az yer kaplar. GPU desteklenmiyorsa ana işlemciye (CPU) geçilir. Modeli belleğe yüklemek
 * birkaç saniye sürer ve ~1-2 GB bellek ister; bu yüzden model ilk özetlemede yüklenir, art arda özetlemelerde açık
 * kalır ve bir dakika kullanılmayınca kapatılır.
 *
 * Özetleme adımları (bölümlere ayırma, istemler, tek paragraf) [LocalSummaryPipeline]'dadır.
 */
object LocalSummarizer {

    /** Modelin bir istekte işleyebileceği en çok belirteç (istem + yanıt). Bölüm boyutları buna göre seçilmiştir. */
    const val MAX_TOKENS = 4096

    private const val IDLE_CLOSE_MS = 60_000L

    /** Model telefonda hazır mı. */
    val available: Boolean
        get() = LocalModel.state.value == LocalModelState.Ready

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var closer: Job? = null

    /** GPU bu telefonda bir kez başarısız olduysa bir daha denenmez (her seferinde beklememek için). */
    @Volatile
    private var gpuFailed = false

    /**
     * Belgenin ne olduğunu ve neleri içerdiğini anlatan tek paragraflık özet ([language] dilinde; bulut özetiyle aynı
     * biçim). [onStep]: o anki adım ("Model yükleniyor…", "Telefonda özetleniyor… (2/5)"). Özet çıkarılamazsa boş metin
     * döner. Model çalıştırılamazsa hata fırlatır. Coroutine iptal edilirse model o anki işi bırakır.
     */
    suspend fun summarize(text: String, language: AppLanguage, onStep: (String) -> Unit = {}): String = lock.withLock {
        closer?.cancel()
        try {
            val loaded = engine ?: run {
                onStep(Txt.ai.loadingModel)
                // Model yüklenirken özetleme durdurulsa da yüklenen model kaydedilir (sonra kapatılsın, sızmasın).
                withContext(Dispatchers.IO) { open().also { engine = it } }
            }
            onStep(Txt.ai.summarizingOnPhone)
            LocalSummaryPipeline(
                language = language,
                ask = { prompt, maxTokens -> ask(loaded, language, prompt, maxTokens) },
                onStep = { done, total -> onStep(Txt.ai.summarizingOnPhoneStep(done, total)) },
            ).summarize(text)
        } finally {
            closer = scope.launch {
                delay(IDLE_CLOSE_MS)
                lock.withLock { closeEngine() }
            }
        }
    }

    /** Modeli bellekten çıkarır (ör. model silinirken). */
    fun release() {
        closer?.cancel()
        scope.launch { lock.withLock { closeEngine() } }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
    }

    /**
     * Modeli yükler. Sırayla denenir: GPU (hızlı tahmin açık), CPU (hızlı tahmin açık), CPU (düz). "Hızlı tahmin"
     * (speculative decoding / MTP), modelin birkaç belirteci birden tahmin edip doğrulamasıdır; özetlemede yazmayı
     * belirgin hızlandırır. ADAPTIVE: yalnızca gerçekten hızlandırdığı sürece kullanılır.
     */
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
                    // Derlenmiş model parçaları burada tutulur: ikinci yükleme daha hızlıdır.
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

    /**
     * Tek bir istek: yeni bir konuşma açılır, istem gönderilir, yanıt beklenir. İstek sürerken coroutine iptal edilirse
     * modele "dur" denir ([com.google.ai.edge.litertlm.Conversation.cancelProcess]) ve model durunca konuşma kapatılır.
     */
    private suspend fun ask(engine: Engine, language: AppLanguage, prompt: String, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(LocalSummaryPipeline.systemPrompt(language)),
                    // Düşük sıcaklık: özet belgeye sadık kalsın, uydurmasın.
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                    maxOutputToken = maxTokens,
                    // Özet için "düşünme" adımı gerekmez; yalnızca yavaşlatır.
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
