package com.eayd.arkive.ai

import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.FileDownloader
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.NetworkPolicy
import com.eayd.arkive.data.WifiRequiredException
import com.eayd.arkive.data.SaveSummaryRequest
import com.eayd.arkive.data.SummarizeRequest
import com.eayd.arkive.data.SummaryResponse
import com.eayd.arkive.utils.toApiErrorMessage
import java.io.File
import java.io.IOException
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

/** Özetin nerede üretildiği ([SummaryResponse.source]). */
enum class SummarySource(val key: String, val label: String) {
    Local("local", "Telefonda özetlendi"),
    Cloud("cloud", "Sunucuda özetlendi"),
    ;

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: Cloud
    }
}

/** AI düğmesine basınca açılan pencere. Özetleme sürerken hiçbir pencere açık kalmaz (bkz. [AiSummarizer.running]). */
sealed interface SummaryUi {
    /** "Bu dosyayı özetlemek istiyor musunuz?" onayı. */
    data class Confirm(val file: FileDto) : SummaryUi

    /** Özetleme sürerken satırdaki yükleme simgesine dokunulunca: durdurulsun mu? */
    data class StopAsk(val file: FileDto) : SummaryUi

    /** Dosya parolalı: parola istenir. [wrong]: önceki parola yanlıştı. */
    data class Password(val file: FileDto, val wrong: Boolean) : SummaryUi

    /** Kayıtlı özet. */
    data class Viewing(
        val fileName: String,
        val text: String,
        val source: SummarySource,
        /** Ek açıklama (ör. "Henüz sunucuya yüklenmedi"); yoksa null. */
        val note: String? = null,
    ) : SummaryUi
}

/**
 * AI düğmesinin akışı:
 *  - Dosyanın özeti varsa: düğme dolu görünür, basınca özetin PDF dosyasının ön izlemesi doğrudan açılır
 *    ([openFile]); yeniden özetlenmez. Dosya yoksa (oluşturulamamışsa) özet küçük bir pencerede gösterilir. Özet dosyası
 *    çöp kutusundan da silinirse özet kaydı silinir ve düğme boşalır: dosya yeniden özetlenebilir.
 *  - Yoksa: basınca önce onay sorulur ("Bu dosyayı özetlemek istiyor musunuz?"). "Özetle" denince pencere kapanır ve
 *    özetleme arka planda sürer; o dosyanın satırında AI düğmesi yerine yükleme simgesi görünür ([running]), kullanıcı
 *    beklerken uygulamayı kullanmaya devam eder. Bitince [completed] ile listeler yenilenir (düğme dolar) ve [notices] ile haber verilir.
 *
 * Özetlemenin yeri ([AiPolicy]): ayara ve dosyaya göre telefonda ya da sunucuda. Her iki durumda da özet sunucuya
 * kaydedilir; özet dosyanın içeriğine bağlıdır, bu yüzden aynı içerikle yeniden yüklenen dosyanın özeti hazır gelir.
 *
 * Uygulama genelinde tek bir kapsamda çalışır: ekran değişse de özetleme sürer.
 */
object AiSummarizer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()

    private val _ui = MutableStateFlow<SummaryUi?>(null)
    val ui: StateFlow<SummaryUi?> = _ui.asStateFlow()

    private val _running = MutableStateFlow<Set<String>>(emptySet())

    /** Şu an özetlenen dosyaların kimlikleri; satırlarda AI düğmesi yerine yükleme simgesi çıkar. */
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    private val _status = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Özetlenen dosyaların o anki adımı ("Dosya okunuyor…", "Telefonda özetleniyor…"); satırda ve bildirimde gösterilir. */
    val status: StateFlow<Map<String, String>> = _status.asStateFlow()

    private fun setStatus(fileId: String, text: String) = _status.update { it + (fileId to text) }

    /** Dosya "çalışıyor" olarak işaretlenir: satırda yükleme simgesi çıkar, [text] adım olarak gösterilir. */
    private fun begin(fileId: String, text: String) {
        _running.update { it + fileId }
        setStatus(fileId, text)
    }

    private fun finish(fileId: String) {
        _running.update { it - fileId }
        _status.update { it - fileId }
    }

    /** Sunucudan gelen özetin kopyası telefonda tutulur: internet yokken de okunur. */
    private fun remember(file: FileDto, saved: SummaryResponse) =
        SummaryStore.put(StoredSummary(file.id, file.name, saved.summary, saved.source, saved.model, pending = false))

    private val _completed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Özetlemesi biten dosyanın kimliği. Açık liste bunu görünce kendini sessizce yeniler ([FileDto.hasSummary] güncellenir). */
    val completed: SharedFlow<String> = _completed.asSharedFlow()

    private val _openFile = MutableSharedFlow<FileDto>(extraBufferCapacity = 1)

    /** Özet dosyasının ön izlemesi açılsın: açık olan liste ekranı bu dosyaya gider. */
    val openFile: SharedFlow<FileDto> = _openFile.asSharedFlow()

    /** Özet dosyası açılırken (ağ isteği sürerken) tekrar basışları yok saymak için. */
    private val opening = mutableSetOf<String>()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Kısa bildirimler: özetleme bitti / başarısız oldu. */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /** AI düğmesine basıldı. [hasSummary]: dosyanın özeti var mı (düğme dolu mu). */
    fun onAiClicked(file: FileDto, hasSummary: Boolean) {
        when {
            file.id in opening -> Unit
            file.id in _running.value -> _ui.value = SummaryUi.StopAsk(file)
            hasSummary -> openSaved(file)
            AiPolicy.kindOf(file.mimeType, file.name) == null -> _notices.tryEmit(UNSUPPORTED_MESSAGE)
            else -> _ui.value = SummaryUi.Confirm(file)
        }
    }

    /**
     * Onay penceresinde "Özetle": pencere kapanır, özetleme arka planda başlar. [password]: parolalı PDF/DOCX için
     * kullanıcının girdiği parola. Önce telefonda doğrulanır; dosya bulutta özetlenecekse (telefona uygun değilse ya da
     * ayar "Bulut" ise) yalnızca o isteğe eklenir. Hiçbir yerde saklanmaz.
     */
    fun confirm(file: FileDto, password: String? = null) {
        _ui.value = null
        if (file.id in _running.value) return
        begin(file.id, "Hazırlanıyor…")
        // Uygulama arka plana alınsa da iş sürsün: ön plan servisi süreci canlı tutar.
        runCatching { SummaryService.start(AppSettings.appContext) }
        jobs[file.id] = scope.launch {
            try {
                summarize(file, password)
                _completed.tryEmit(file.id)
                // Telefonda üretilip internet yüzünden yüklenemeyen özet için ayrı bir bildirim zaten gösterildi.
                if (SummaryStore.get(file.id)?.pending != true) {
                    _notices.tryEmit("“${file.name}” özetlendi. Özet, AI Sonuçlarım sayfasına dosya olarak da kaydedildi.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PasswordException) {
                _ui.value = SummaryUi.Password(file, e.wrong) // parola istenir; girilince özetleme yeniden başlar
            } catch (e: AiFailure) {
                _notices.tryEmit(e.message.orEmpty())
            } catch (e: Exception) {
                _notices.tryEmit(e.toApiErrorMessage())
            } finally {
                finish(file.id)
                jobs.remove(file.id)
            }
        }
    }

    /** Sürmekte olan özetlemeyi durdurur (örneğin sunucu özeti uzun sürüyorsa). */
    fun cancel(file: FileDto) {
        _ui.value = null
        val job = jobs[file.id] ?: return
        job.cancel()
        _notices.tryEmit("“${file.name}” için özetleme durduruldu.")
    }

    /** Pencereyi kapatır (özetleme çalışıyorsa sürer). */
    fun dismiss() {
        _ui.value = null
    }

    /**
     * Kayıtlı özeti açar: özetin PDF dosyası varsa doğrudan onun ön izlemesine gidilir ([openFile]); dosya yoksa ya da
     * alınamazsa özet metni küçük bir pencerede gösterilir. Bu sırada satırda yükleme simgesi döner.
     */
    private fun openSaved(file: FileDto) {
        val local = SummaryStore.get(file.id)
        if (local != null && local.pending) {
            // Telefonda üretildi ama henüz sunucuya yüklenmedi: özet telefondaki kopyadan gösterilir, yükleme yeniden denenir.
            _ui.value = SummaryUi.Viewing(
                file.name, local.summary, SummarySource.fromKey(local.source),
                note = "Henüz sunucuya yüklenmedi; internet gelince otomatik yüklenecek.",
            )
            scope.launch { flushPending() }
            return
        }
        opening += file.id
        begin(file.id, "Özet açılıyor…")
        scope.launch {
            try {
                val saved = ApiClient.api.getSummary(file.id)
                remember(file, saved)
                val resultFile = saved.resultFileId?.let { id ->
                    try {
                        ApiClient.api.getFile(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                if (resultFile != null) {
                    _openFile.tryEmit(resultFile)
                } else {
                    _ui.value = SummaryUi.Viewing(file.name, saved.summary, SummarySource.fromKey(saved.source))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // İnternet yok: telefondaki kopya varsa özet oradan gösterilir.
                val cached = SummaryStore.get(file.id)
                if (cached != null && e !is HttpException) {
                    _ui.value = SummaryUi.Viewing(
                        file.name, cached.summary, SummarySource.fromKey(cached.source),
                        note = "Sunucuya ulaşılamadı; özet telefondaki kopyadan gösteriliyor.",
                    )
                } else {
                    _notices.tryEmit(e.toApiErrorMessage())
                }
            } finally {
                opening -= file.id
                finish(file.id)
            }
        }
    }

    /** Hata mesajı doğrudan kullanıcıya gösterilir. */
    private class AiFailure(message: String) : Exception(message)

    private suspend fun summarize(file: FileDto, password: String?) {
        val kind = AiPolicy.kindOf(file.mimeType, file.name) ?: throw AiFailure(UNSUPPORTED_MESSAGE)
        val mode = AppSettings.aiMode.value

        when (val pre = AiPolicy.precheck(mode, file.size)) {
            is Precheck.Blocked -> throw blocked(file, pre.reason)
            Precheck.Cloud -> summarizeInCloud(file, password)
            Precheck.Inspect -> {
                val doc = try {
                    inspect(file, kind, password)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PasswordException) {
                    throw e // parola gerekiyor ya da yanlış: kullanıcıdan istenir
                } catch (e: Exception) {
                    // Dosyanın telefonda kopyası yok ve internet de yok: bulut da işe yaramaz, nedeni söylenir.
                    if (e is IOException && e !is WifiRequiredException) {
                        throw AiFailure("“${file.name}” telefonda kayıtlı değil ve sunucuya ulaşılamadı. Bağlantı gelince tekrar deneyin.")
                    }
                    // Dosya telefonda açılamadı (bozuk, boyut tutmuyor…): bulut izinliyse orada denenir, değilse nedeni söylenir.
                    if (mode.allowsCloud) return summarizeInCloud(file, password)
                    val reason = e.message ?: "Dosya açılamadı."
                    throw AiFailure(if (e is UnreadableDocumentException) "“${file.name}” özetlenemedi: $reason" else reason)
                }
                // Parola (varsa) burada telefonda doğrulanmış demektir; bulut gerekirse yalnızca doğru parola gider.
                when (val route = AiPolicy.afterInspection(mode, doc.stats)) {
                    is Route.Blocked -> throw blocked(file, route.reason)
                    Route.Cloud -> summarizeInCloud(file, password)
                    Route.Local -> summarizeOnDevice(file, doc.text, mode, password)
                }
            }
        }
    }

    private fun blocked(file: FileDto, reason: LocalRejection): AiFailure {
        val hint = when (reason) {
            LocalRejection.NoText -> ""
            else -> " Büyük ya da uzun dosyalar için Profil > Ayarlar bölümünden yapay zeka işlem yerini \"Yerel + Bulut\" yapın."
        }
        return AiFailure("“${file.name}” özetlenemedi: ${reason.message}$hint")
    }

    /**
     * Dosyayı (en çok 1 MB) açar. Telefonda kopyası varsa ([InputCache]) internet gerekmez; yoksa indirilir ve indirilen
     * dosya sonraki sefer için kopya olarak saklanır.
     */
    private suspend fun inspect(file: FileDto, kind: SummarizableKind, password: String?): ReadDocument {
        setStatus(file.id, "Dosya okunuyor…")
        // Kopya önce saklanır, sonra okunur: parola girilip yeniden denenirse dosya yeniden indirilmez.
        val copy = InputCache.get(file.id, file.size) ?: run {
            NetworkPolicy.requireAllowed() // "Yalnızca Wi-Fi" açıksa dosya indirilmez
            downloadCopy(file.id, file.size) ?: throw UnreadableDocumentException("İndirilen dosyanın boyutu beklenenden farklı.")
        }
        return DocumentReader.read(AppSettings.appContext, copy, kind, password)
    }

    /** Dosyayı indirip telefondaki kopya olarak saklar ([InputCache]); kopyayı döner (boyut tutmazsa null). */
    private suspend fun downloadCopy(fileId: String, size: Long): File? {
        val temp = InputCache.tempFile(fileId)
        try {
            val url = ApiClient.api.getDownloadUrl(fileId).url
            FileDownloader.downloadTo(url, temp, maxBytes = AiPolicy.LOCAL_MAX_BYTES + 64 * 1024) {}
            withContext(Dispatchers.IO) { InputCache.adopt(temp, fileId, size) }
            return InputCache.get(fileId, size)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { temp.delete() }
        }
    }

    private val prefetching = Mutex()

    /**
     * Liste görününce çağrılır: özetlenebilir türdeki (en çok [AiPolicy.LOCAL_MAX_BYTES]) dosyaları arka planda telefona
     * kopyalar; böylece internet yokken de ön izlenebilir ve (özeti yoksa) özetlenebilirler. Özeti olmayanlar önce
     * indirilir. "Yalnızca Wi-Fi" açıkken mobil veride indirmez.
     */
    fun prefetch(files: List<FileDto>) {
        scope.launch {
            if (!prefetching.tryLock()) return@launch
            try {
                if (!NetworkPolicy.isAllowed()) return@launch
                val wanted = files.filter {
                    it.status == "ready" && it.id !in _running.value && it.size in 1..AiPolicy.LOCAL_MAX_BYTES &&
                        AiPolicy.kindOf(it.mimeType, it.name) != null && InputCache.get(it.id, it.size) == null
                }.sortedBy { it.hasSummary || it.inAiResults }.take(InputCache.MAX_PER_PASS)
                for (f in wanted) {
                    try {
                        downloadCopy(f.id, f.size)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        break // internet yok: kalanları denemenin anlamı yok
                    } catch (e: Exception) {
                        continue
                    }
                }
            } finally {
                prefetching.unlock()
            }
        }
    }

    private suspend fun summarizeOnDevice(file: FileDto, text: String, mode: AiMode, password: String?) {
        setStatus(file.id, "Telefonda özetleniyor…")
        val summary = withContext(Dispatchers.Default) { ExtractiveSummarizer.summarize(text) }
        if (summary.isBlank()) {
            // Özet çıkarılacak cümle yok (ör. yalnızca sayı/tablo): bulut izinliyse orada denenir.
            if (mode.allowsCloud) return summarizeInCloud(file, password)
            throw AiFailure("“${file.name}” özetlenemedi: ${LocalRejection.NoText.message}")
        }
        // Özetin PDF'i telefonda üretilir (seçili şablonla). Üretilemezse özet yine de kaydedilir; sunucu PDF'i kendisi yazar.
        val template = AppSettings.summaryTemplate.value
        val pdf = withContext(Dispatchers.Default) {
            runCatching { SummaryPdfWriter.render(AppSettings.appContext, file.name, summary, template) }.getOrNull()
        }
        // Önce telefona yazılır: yükleme sırasında internet kesilse ya da uygulama kapansa özet (ve PDF'i) kaybolmaz.
        SummaryStore.put(
            StoredSummary(file.id, file.name, summary, "local", ExtractiveSummarizer.NAME, pending = true),
        )
        if (pdf != null) SummaryStore.putPdf(file.id, pdf)
        // Sonra sunucuya kaydedilir; aynı içerikli dosyalar da böylece "özetlendi" olur.
        when (upload(file.id, summary, ExtractiveSummarizer.NAME)) {
            Upload.Done -> Unit
            Upload.Later -> {
                _notices.tryEmit("“${file.name}” özetlendi ama sunucuya ulaşılamadı. Özet telefonda saklandı, bağlantı gelince yüklenecek.")
            }
            Upload.Rejected -> throw AiFailure("“${file.name}” özeti sunucuya kaydedilemedi.")
        }
    }

    private enum class Upload {
        Done,

        /** Şimdi yüklenemedi (internet yok / geçici sunucu sorunu); özet telefonda bekler. */
        Later,

        /** Sunucu kalıcı olarak reddetti (dosya silinmiş, tür desteklenmiyor…); telefondaki kayıt atılır. */
        Rejected,
    }

    /** Telefondaki bekleyen özeti sunucuya yükler ve sonucu söyler; telefondaki kayıt buna göre güncellenir/silinir. */
    private suspend fun upload(fileId: String, summary: String, model: String?): Upload =
        try {
            // Telefonda üretilmiş PDF varsa o gider (sunucu olduğu gibi saklar); yoksa sunucu metinden üretir.
            val pdf = SummaryStore.getPdf(fileId)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            ApiClient.api.saveSummary(
                fileId,
                SaveSummaryRequest(
                    summary,
                    model = model,
                    template = AppSettings.summaryTemplate.value.id,
                    pdfBase64 = pdf,
                ),
            )
            SummaryStore.markUploaded(fileId)
            Upload.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (SummaryQueuePolicy.isRetryable(e.code())) {
                Upload.Later
            } else {
                SummaryStore.remove(fileId)
                Upload.Rejected
            }
        } catch (e: Exception) {
            Upload.Later // internet yok / beklenmedik hata: özet telefonda bekler
        }

    private val flushLock = Mutex()

    /**
     * Yüklenmeyi bekleyen telefon özetlerini sunucuya gönderir. İnternet geri gelince, uygulama açılınca ve liste her
     * göründüğünde çağrılır; bekleyen yoksa hiçbir şey yapmaz. "Yalnızca Wi-Fi" açıkken mobil veride denemez.
     */
    suspend fun flushPending() = flushLock.withLock {
        SummaryStore.publish() // hesap değişmiş olabilir
        val waiting = SummaryStore.pending().filter { it.fileId !in _running.value }
        if (waiting.isEmpty()) return@withLock
        if (!NetworkPolicy.isAllowed()) return@withLock
        for (entry in waiting) {
            when (upload(entry.fileId, entry.summary, entry.model)) {
                Upload.Done -> {
                    _completed.tryEmit(entry.fileId)
                    _notices.tryEmit("“${entry.fileName}” özeti sunucuya yüklendi.")
                }
                Upload.Later -> break // internet yok: kalanları da denemenin anlamı yok
                Upload.Rejected -> Unit
            }
        }
    }

    private val _networkBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** İnternet bağlantısı (yeniden) geldi: telefondaki kayıttan gösterilen listeler sunucudan tazelenir. */
    val networkBack: SharedFlow<Unit> = _networkBack.asSharedFlow()

    private var started = false

    /** Uygulama açılırken bir kez: bekleyen özetleri dener ve internet geri gelince yeniden dener. */
    fun start(context: Context) {
        if (started) return
        started = true
        SummaryStore.init(context)
        InputCache.init(context)
        scope.launch { flushPending() }
        try {
            context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _networkBack.tryEmit(Unit)
                        scope.launch { flushPending() }
                    }
                },
            )
        } catch (e: Exception) {
            // Ağ dinleyicisi kurulamadıysa yükleme yine uygulama açılınca ve liste görününce denenir.
        }
    }

    private suspend fun summarizeInCloud(file: FileDto, password: String?) {
        setStatus(file.id, "Sunucuda özetleniyor…")
        try {
            val request = SummarizeRequest(
                password = password?.takeIf { it.isNotEmpty() },
                template = AppSettings.summaryTemplate.value.id,
            )
            val saved = ApiClient.aiApi.summarizeFile(file.id, request)
            remember(file, saved)
        } catch (e: HttpException) {
            // 423: dosya parolalı (parola gerekiyor ya da yanlış). Parola penceresi açılır.
            if (e.code() == 423) throw PasswordException(wrong = e.passwordInvalid(sent = !password.isNullOrEmpty()))
            throw AiFailure("“${file.name}” özetlenemedi: ${e.cloudMessage()}")
        }
    }

    /** 423 yanıtı: sunucu `detail.code` ile "password_invalid" (yanlış parola) ya da "password_required" der. */
    private fun HttpException.passwordInvalid(sent: Boolean): Boolean {
        val code = try {
            response()?.errorBody()?.string()?.let { JSONObject(it).optJSONObject("detail")?.optString("code") }
        } catch (e: Exception) {
            null
        }
        return if (code.isNullOrEmpty()) sent else code == "password_invalid"
    }

    /**
     * Sunucunun özetleme hataları: genel hata çevirisi 422/502 gibi kodları kullanıcıya anlamsız bir mesaja çevirir;
     * oysa sunucunun Türkçe açıklaması ("metin çıkarılamadı", "yapılandırılmamış"…) tam olarak neyin yanlış olduğunu söyler.
     */
    private fun HttpException.cloudMessage(): String {
        val detail = try {
            response()?.errorBody()?.string()?.let { JSONObject(it).opt("detail") as? String }
        } catch (e: Exception) {
            null
        }
        return when (code()) {
            413, 415, 422, 502, 503 -> detail ?: toApiErrorMessage()
            else -> toApiErrorMessage()
        }
    }

    private const val UNSUPPORTED_MESSAGE =
        "Bu dosya türü özetlenemiyor. Metin, PDF ve DOCX dosyaları özetlenebilir."
}
