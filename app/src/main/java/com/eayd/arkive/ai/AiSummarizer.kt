package com.eayd.arkive.ai

import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.data.FileDownloader
import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.NetworkPolicy
import com.eayd.arkive.data.PlanPolicy
import com.eayd.arkive.data.PlanStatus
import com.eayd.arkive.data.PlusOffer
import com.eayd.arkive.data.SummaryQuota
import com.eayd.arkive.data.WifiRequiredException
import com.eayd.arkive.data.SaveSummaryRequest
import com.eayd.arkive.data.SummarizeRequest
import com.eayd.arkive.data.SummaryResponse
import com.eayd.arkive.utils.toApiErrorMessage
import java.io.IOException
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * Biten özetleme: uygulama içinde kart olarak gösterilir, dokunulunca [file]'ın özeti açılır. [savedOnPhoneOnly]: özet
 * telefonda saklandı, sunucuya henüz yüklenemedi.
 */
class SummaryDone(val file: FileDto, val savedOnPhoneOnly: Boolean)

/**
 * AI düğmesinin akışı:
 *  - Dosyanın özeti varsa: düğme dolu görünür, basınca özetin PDF dosyasının ön izlemesi doğrudan açılır
 *    ([openFile]); yeniden özetlenmez. Dosya yoksa (oluşturulamamışsa) özet küçük bir pencerede gösterilir. Özet dosyası
 *    çöp kutusundan da silinirse özet kaydı silinir ve düğme boşalır: dosya yeniden özetlenebilir.
 *  - Yoksa: basınca önce onay sorulur ("Bu dosyayı özetlemek istiyor musunuz?"). "Özetle" denince pencere kapanır ve
 *    özetleme arka planda sürer; o dosyanın satırında AI düğmesi yerine yükleme simgesi görünür ([running]), kullanıcı
 *    beklerken uygulamayı kullanmaya devam eder. Bitince [completed] ile listeler yenilenir (düğme dolar) ve haber verilir: uygulama açıksa [done] kartıyla, arka plandaysa ya da kapalıysa
 *    sistem bildirimiyle ([SummaryNotifier]). İkisine de dokunulunca özet açılır ([openSummary]).
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

    private val _completed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Özetlemesi biten dosyanın kimliği. Açık liste bunu görünce kendini sessizce yeniler ([FileDto.hasSummary] güncellenir). */
    val completed: SharedFlow<String> = _completed.asSharedFlow()

    private val _openFile = MutableSharedFlow<FileDto>(extraBufferCapacity = 1)

    /** Özet dosyasının ön izlemesi açılsın: açık olan liste ekranı bu dosyaya gider. */
    val openFile: SharedFlow<FileDto> = _openFile.asSharedFlow()

    /** Özet dosyası açılırken (ağ isteği sürerken) tekrar basışları yok saymak için. */
    private val opening = mutableSetOf<String>()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Kısa bildirimler: özetleme başarısız oldu / durduruldu… ("özetleme bitti" için bkz. [done]). */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _done = MutableSharedFlow<SummaryDone>(extraBufferCapacity = 8)

    /**
     * Özetleme başarıyla bitti (telefonda da bulutta da). Açık olan liste ekranı bunu dokunulabilir bir kart olarak
     * gösterir ([com.eayd.arkive.ui.components.SummaryDoneBanner]); dokunulunca [openSummary] ile özet açılır.
     */
    val done: SharedFlow<SummaryDone> = _done.asSharedFlow()

    /**
     * Bitmiş özetin kendisini açar (özetin PDF dosyası varsa onun ön izlemesi, yoksa özet penceresi). "Özetleme bitti"
     * kartına dokunulunca çağrılır. Dosya hala özetleniyorsa ya da özet zaten açılıyorsa bir şey yapmaz.
     */
    fun openSummary(file: FileDto) = openSummary(file.id, file.name)

    /** Aynısı, yalnızca kimlik ve adla (bildirime dokunulunca elde dosyanın kendisi yoktur, bunlar vardır). */
    fun openSummary(fileId: String, fileName: String) {
        if (fileId in opening || fileId in _running.value) return
        openSaved(fileId, fileName)
    }

    private val _pendingOpen = MutableStateFlow<SummaryOpenRequest?>(null)

    /**
     * Bildirime dokunularak gelen, henüz yerine getirilmemiş "şu dosyanın özetini aç" isteği. Uygulama kapalıyken
     * dokunulduysa oturum/açılış ekranları bitmeden gezinilemez; bu yüzden istek burada bekler, ana gezinme
     * ([com.eayd.arkive.navigation.ArkiveNavHost]) hazır olunca alıp yerine getirir.
     */
    val pendingOpen: StateFlow<SummaryOpenRequest?> = _pendingOpen.asStateFlow()

    /** Bildirimden gelen isteği kaydeder ([MainActivity]). */
    fun requestOpenSummary(request: SummaryOpenRequest) {
        _pendingOpen.value = request
    }

    /** Bekleyen isteği alır ve siler (iki yerde birden yerine getirilmesin); yoksa null. */
    fun takePendingOpen(): SummaryOpenRequest? = _pendingOpen.getAndUpdate { null }

    /** AI düğmesine basıldı. [hasSummary]: dosyanın özeti var mı (düğme dolu mu). */
    fun onAiClicked(file: FileDto, hasSummary: Boolean) {
        when {
            file.id in opening -> Unit
            file.id in _running.value -> _ui.value = SummaryUi.StopAsk(file)
            hasSummary -> openSaved(file.id, file.name)
            AiPolicy.kindOf(file.mimeType, file.name) == null -> _notices.tryEmit(UNSUPPORTED_MESSAGE)
            else -> askToSummarize(file)
        }
    }

    /**
     * Özetleme onayından önce planın hakları denetlenir: ücretsiz plandaki özet sınırı ([SummaryQuota.full]) doluysa onay
     * yerine Plus tanıtımı açılır. Haklar sunucudan tazelenir; internet yoksa son bilinen değer kullanılır (sunucu da
     * özeti kaydederken aynı sınırı denetler).
     */
    private fun askToSummarize(file: FileDto) {
        opening += file.id
        scope.launch {
            try {
                val quota = refreshQuota()
                val limit = quota?.limit
                if (quota != null && quota.full && limit != null) {
                    PlusOffer.show(PlanPolicy.limitMessage(limit))
                } else {
                    _ui.value = SummaryUi.Confirm(file)
                }
            } finally {
                opening -= file.id
            }
        }
    }

    /** Planın haklarını sunucudan alır (en çok 5 sn); alınamazsa son bilinen değer (hiç yoksa null). */
    private suspend fun refreshQuota(): SummaryQuota? {
        val fresh = withTimeoutOrNull(5_000) {
            try {
                ApiClient.api.getMe().summaryQuota()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        fresh?.let(PlanStatus::update)
        return fresh ?: PlanStatus.quota.value
    }

    /**
     * Onay penceresinde "Özetle": pencere kapanır, özetleme arka planda başlar. [password]: parolalı PDF/Word için
     * kullanıcının girdiği parola. Önce telefonda doğrulanır; dosya bulutta özetlenecekse (telefona uygun değilse ya da
     * ayar "Bulut" ise) yalnızca o isteğe eklenir. Hiçbir yerde saklanmaz.
     */
    fun confirm(file: FileDto, password: String? = null) {
        _ui.value = null
        if (file.id in _running.value) return
        _running.update { it + file.id }
        setStatus(file.id, "Hazırlanıyor…")
        // Uygulama arka plana alınsa da iş sürsün: ön plan servisi süreci canlı tutar.
        runCatching { SummaryService.start(AppSettings.appContext) }
        jobs[file.id] = scope.launch {
            try {
                summarize(file, password)
                announceDone(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PasswordException) {
                _ui.value = SummaryUi.Password(file, e.wrong) // parola istenir; girilince özetleme yeniden başlar
            } catch (e: PlusRequired) {
                PlusOffer.show(e.message) // planın izin vermediği özetleme: neden yazılı Plus tanıtımı
            } catch (e: AiFailure) {
                _notices.tryEmit(e.message.orEmpty())
            } catch (e: Exception) {
                _notices.tryEmit(e.toApiErrorMessage())
            } finally {
                _running.update { it - file.id }
                _status.update { it - file.id }
                jobs.remove(file.id)
            }
        }
    }

    /** Özetleme bitti: açık liste yenilenir, "Özetleme bitti" kartı ya da (uygulama arka plandaysa) bildirimi çıkar. */
    private fun announceDone(file: FileDto) {
        _completed.tryEmit(file.id)
        // Telefonda üretilip internet yüzünden yüklenemeyen özet de açılabilir (telefondaki kopyadan); kart bunu belirtir.
        val onPhoneOnly = SummaryStore.get(file.id)?.pending == true
        if (SummaryDoneDelivery.useNotification(AppVisibility.visible, _done.subscriptionCount.value)) {
            // Uygulama arka planda/kapalı (ya da mesajı gösterecek ekran yok): sistem bildirimi; dokununca özet açılır.
            SummaryNotifier.notifyDone(AppSettings.appContext, SummaryOpenRequest(file.id, file.name), onPhoneOnly)
        } else {
            _done.tryEmit(SummaryDone(file, savedOnPhoneOnly = onPhoneOnly))
        }
    }

    /**
     * Sürmekte olan özetlemeyi durdurur ("Durdur"). Bulut özetlemesi sunucuda uygulamanın bağlantısından bağımsız sürdüğü
     * için (uygulama kapansa da biter) sunucuya ayrıca "durdur" denir; telefonda süren özetlemede bu istek zararsızdır.
     */
    fun cancel(file: FileDto) {
        _ui.value = null
        val job = jobs[file.id] ?: return
        job.cancel()
        CloudJobs.remove(file.id)
        scope.launch {
            try {
                ApiClient.api.cancelSummary(file.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // İnternet yoksa sunucu özetlemeyi bitirir; özet AI Sonuçlarım'da görünür.
            }
        }
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
    private fun openSaved(fileId: String, fileName: String) {
        val local = SummaryStore.get(fileId)
        if (local != null && local.pending) {
            // Telefonda üretildi ama henüz sunucuya yüklenmedi: özet telefondaki kopyadan gösterilir, yükleme yeniden denenir.
            _ui.value = SummaryUi.Viewing(
                fileName, local.summary, SummarySource.fromKey(local.source),
                note = "Henüz sunucuya yüklenmedi; internet gelince otomatik yüklenecek.",
            )
            scope.launch { flushPending() }
            return
        }
        opening += fileId
        _running.update { it + fileId }
        setStatus(fileId, "Özet açılıyor…")
        scope.launch {
            try {
                val saved = ApiClient.api.getSummary(fileId)
                // Sonraki açılışlar internetsiz de çalışsın diye özet telefonda tutulur (metin + PDF'in bilgisi). PDF'in
                // kendisinin kopyasını ön izleme ekranı indirirken alır.
                val resultFile = keepTogether(fileId, fileName, saved)
                if (resultFile != null) {
                    _openFile.tryEmit(resultFile)
                } else {
                    val text = SummaryStore.get(fileId)?.summary ?: saved.summary
                    _ui.value = SummaryUi.Viewing(fileName, text, SummarySource.fromKey(saved.source))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sunucuya ulaşılamadı (internet yok ya da sunucu yanıt vermiyor): özet telefondaki kopyadan açılır.
                if (e is HttpException && e.code() < 500) {
                    _notices.tryEmit(e.toApiErrorMessage())
                } else {
                    openOffline(fileId, fileName, e)
                }
            } finally {
                opening -= fileId
                _running.update { it - fileId }
                _status.update { it - fileId }
            }
        }
    }

    /**
     * Sunucuya ulaşılamayınca kayıtlı özeti telefondan açar: özetin PDF'inin kopyası varsa PDF'in ön izlemesi, yoksa
     * telefondaki metin gösterilir. İkisi de yoksa nedeni söylenir (boş bir pencere açılmaz).
     */
    private fun openOffline(fileId: String, fileName: String, error: Exception) {
        val cached = SummaryStore.get(fileId)
        val copy = cached?.resultFile?.takeIf { InputCache.get(it.id, it.size) != null }
        when {
            copy != null -> {
                _openFile.tryEmit(copy)
                _notices.tryEmit("Sunucuya bağlanılamıyor; özet telefondaki kopyadan açıldı.")
            }
            cached != null && cached.summary.isNotBlank() -> _ui.value = SummaryUi.Viewing(
                fileName, cached.summary, SummarySource.fromKey(cached.source),
                note = "Sunucuya ulaşılamadı; özet telefondaki kopyadan gösteriliyor.",
            )
            error is IOException || error is HttpException ->
                _notices.tryEmit("Sunucuya ulaşılamadı ve bu özetin telefonda kopyası yok. İnternet gelince yeniden deneyin.")
            else -> _notices.tryEmit(error.toApiErrorMessage())
        }
    }

    /**
     * Sunucudan gelen özeti telefona yazar: metin ve özetin PDF dosyasının bilgisi birlikte saklanır. Sunucunun metni
     * boşsa telefondaki önceki metin korunur ([SummaryCopyPolicy.keptText]). PDF'in bilgisi alınamazsa (internet
     * kesildi) önceki kayıttaki bilgi kullanılır. Özetin PDF dosyası (biliniyorsa) döner.
     */
    private suspend fun keepTogether(fileId: String, fileName: String, saved: SummaryResponse): FileDto? {
        val previous = SummaryStore.get(fileId)
        val resultFile = saved.resultFileId?.let { id ->
            try {
                ApiClient.api.getFile(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                previous?.resultFile?.takeIf { it.id == id }
            }
        }
        SummaryStore.put(
            StoredSummary(
                fileId, fileName, SummaryCopyPolicy.keptText(saved.summary, previous?.summary),
                saved.source, saved.model, pending = false, resultFile = resultFile,
            ),
        )
        return resultFile
    }

    /** Özetin PDF'inin kopyasını telefona indirir (zaten varsa ya da çok büyükse bir şey yapmaz). */
    private suspend fun keepResultCopy(resultFile: FileDto) {
        if (!SummaryCopyPolicy.canKeepCopy(resultFile.size)) return
        if (InputCache.get(resultFile.id, resultFile.size) != null) return
        downloadCopy(resultFile, SummaryCopyPolicy.MAX_RESULT_COPY_BYTES)
    }

    /** Arka planda özetin PDF'inin kopyasını alır; internet yoksa ya da indirilemezse sessizce vazgeçer. */
    private fun keepResultCopyLater(resultFile: FileDto?) {
        resultFile ?: return
        scope.launch {
            try {
                NetworkPolicy.requireAllowed()
                keepResultCopy(resultFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kopya alınamadı: internetsizken özet metinden gösterilir; liste açılınca yeniden denenir.
            }
        }
    }

    /** [file]'ı indirip telefondaki kopyası yapar. İnternet yoksa [IOException] fırlatır. */
    private suspend fun downloadCopy(file: FileDto, maxBytes: Long) {
        val temp = InputCache.tempFile(file.id)
        try {
            val url = ApiClient.api.getDownloadUrl(file.id).url
            FileDownloader.downloadTo(url, temp, maxBytes = maxBytes + 64 * 1024) {}
            withContext(Dispatchers.IO) { InputCache.adopt(temp, file.id, file.size) }
        } finally {
            withContext(Dispatchers.IO) { temp.delete() }
        }
    }

    /** Hata mesajı doğrudan kullanıcıya gösterilir. */
    private class AiFailure(message: String) : Exception(message)

    /** Bu özetleme ücretsiz planda yapılamaz (bulut özeti ya da özet sınırı); mesajla birlikte Plus tanıtımı açılır. */
    private class PlusRequired(message: String) : Exception(message)

    private suspend fun summarize(file: FileDto, password: String?) {
        val kind = AiPolicy.kindOf(file.mimeType, file.name) ?: throw AiFailure(UNSUPPORTED_MESSAGE)
        // Bulut özeti Plus'a özel: ücretsiz planda (ya da plan bilinmiyorsa) ayar ne olursa olsun yalnızca telefonda özetlenir.
        val cloudAllowed = PlanStatus.quota.value?.cloud == true
        val mode = PlanPolicy.effectiveMode(AppSettings.aiMode.value, cloudAllowed)

        when (val pre = AiPolicy.precheck(mode, file.size)) {
            is Precheck.Blocked -> throw blocked(file, pre.reason, cloudAllowed)
            Precheck.Cloud -> summarizeInCloud(file, password)
            Precheck.Inspect -> {
                val doc = try {
                    inspect(file, kind, password)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PasswordException) {
                    throw e // parola gerekiyor ya da yanlış: kullanıcıdan istenir
                } catch (e: UnreadableDocumentException) {
                    // Dosya telefonda açılamadı (bozuk olabilir): bulut izinliyse orada denenir.
                    if (mode.allowsCloud) return summarizeInCloud(file, password)
                    throw AiFailure("“${file.name}” özetlenemedi: ${e.message}")
                } catch (e: Exception) {
                    // Dosyanın telefonda kopyası yok ve internet de yok: bulut da işe yaramaz, nedeni söylenir.
                    if (e is IOException && e !is WifiRequiredException) {
                        throw AiFailure("“${file.name}” telefonda kayıtlı değil ve sunucuya ulaşılamadı. Bağlantı gelince tekrar deneyin.")
                    }
                    // Dosya telefonda açılamadı: bulut izinliyse orada denenir, değilse nedeni söylenir.
                    if (mode.allowsCloud) return summarizeInCloud(file, password)
                    throw AiFailure(e.message ?: "Dosya açılamadı.")
                }
                // Parola (varsa) burada telefonda doğrulanmış demektir; bulut gerekirse yalnızca doğru parola gider.
                when (val route = AiPolicy.afterInspection(mode, doc.stats)) {
                    is Route.Blocked -> throw blocked(file, route.reason, cloudAllowed)
                    Route.Cloud -> summarizeInCloud(file, password)
                    Route.Local -> summarizeOnDevice(file, doc.text, mode, password)
                }
            }
        }
    }

    private fun blocked(file: FileDto, reason: LocalRejection, cloudAllowed: Boolean): Exception {
        // Ücretsiz planda büyük/görselli dosya: bulutta özetlenebilirdi ama bulut Plus'a özel.
        if (!cloudAllowed && reason != LocalRejection.NoText) {
            return PlusRequired(PlanPolicy.needsCloudMessage(file.name, reason.message))
        }
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
        val context = AppSettings.appContext
        setStatus(file.id, "Dosya okunuyor…")
        InputCache.get(file.id, file.size)?.let { return DocumentReader.read(context, it, kind, password) }
        NetworkPolicy.requireAllowed() // "Yalnızca Wi-Fi" açıksa dosya indirilmez
        val temp = InputCache.tempFile(file.id)
        try {
            val url = ApiClient.api.getDownloadUrl(file.id).url
            FileDownloader.downloadTo(url, temp, maxBytes = AiPolicy.LOCAL_MAX_BYTES + 64 * 1024) {}
            val document = try {
                DocumentReader.read(context, temp, kind, password)
            } catch (e: PasswordException) {
                // Parola girilip yeniden denenecek: dosya yeniden indirilmesin.
                withContext(Dispatchers.IO) { InputCache.adopt(temp, file.id, file.size) }
                throw e
            }
            withContext(Dispatchers.IO) { InputCache.adopt(temp, file.id, file.size) }
            return document
        } finally {
            withContext(Dispatchers.IO) { temp.delete() }
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
                try {
                    NetworkPolicy.requireAllowed()
                } catch (e: Exception) {
                    return@launch
                }
                val wanted = files.filter {
                    it.status == "ready" && it.id !in _running.value && it.size in 1..AiPolicy.LOCAL_MAX_BYTES &&
                        AiPolicy.kindOf(it.mimeType, it.name) != null && InputCache.get(it.id, it.size) == null
                }.sortedBy { it.hasSummary || it.inAiResults }.take(InputCache.MAX_PER_PASS)
                for (f in wanted) {
                    try {
                        downloadCopy(f, AiPolicy.LOCAL_MAX_BYTES)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        return@launch // internet yok: kalanları denemenin anlamı yok
                    } catch (e: Exception) {
                        continue
                    }
                }
                prefetchSummaries(files)
            } finally {
                prefetching.unlock()
            }
        }
    }

    /**
     * Özeti olan dosyaların özetini (metin + PDF'in kopyası) telefona alır: dosya ile özeti birlikte saklanır, böylece
     * internetsizken özet hiç açılmamış olsa da açılır. Telefonda tam kopyası olan özetler yeniden alınmaz.
     */
    private suspend fun prefetchSummaries(files: List<FileDto>) {
        val wanted = files.filter { f ->
            f.hasSummary && !f.inAiResults && f.id !in _running.value && f.id !in opening &&
                SummaryStore.get(f.id).let { stored ->
                    val hasCopy = stored?.resultFile?.let { InputCache.get(it.id, it.size) != null } == true
                    SummaryCopyPolicy.needsFetch(stored, hasCopy)
                }
        }.take(InputCache.MAX_PER_PASS)
        for (f in wanted) {
            try {
                val saved = ApiClient.api.getSummary(f.id)
                keepTogether(f.id, f.name, saved)?.let { keepResultCopy(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return // internet yok
            } catch (e: Exception) {
                continue // bu dosyanın özeti alınamadı (silinmiş olabilir); sonraki listede yeniden denenir
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
        when (val result = upload(file.id, summary, ExtractiveSummarizer.NAME)) {
            Upload.Done -> Unit
            // Özet telefonda saklandı, bağlantı gelince yüklenecek; "özetlendi" mesajını [confirm] bunu belirterek verir.
            Upload.Later -> Unit
            Upload.Rejected -> throw AiFailure("“${file.name}” özeti sunucuya kaydedilemedi.")
            is Upload.PlanLimit -> throw PlusRequired(result.message)
        }
    }

    private sealed interface Upload {
        data object Done : Upload

        /** Şimdi yüklenemedi (internet yok / geçici sunucu sorunu); özet telefonda bekler. */
        data object Later : Upload

        /** Sunucu kalıcı olarak reddetti (dosya silinmiş, tür desteklenmiyor…); telefondaki kayıt atılır. */
        data object Rejected : Upload

        /** Planın özet sınırı dolu (402); telefondaki kayıt atılır. [message]: sunucunun açıklaması. */
        data class PlanLimit(val message: String) : Upload
    }

    /** Telefondaki bekleyen özeti sunucuya yükler ve sonucu söyler; telefondaki kayıt buna göre güncellenir/silinir. */
    private suspend fun upload(fileId: String, summary: String, model: String?): Upload =
        try {
            // Telefonda üretilmiş PDF varsa o gider (sunucu olduğu gibi saklar); yoksa sunucu metinden üretir.
            val pdf = SummaryStore.getPdf(fileId)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            val saved = ApiClient.api.saveSummary(
                fileId,
                SaveSummaryRequest(
                    summary,
                    model = model,
                    template = AppSettings.summaryTemplate.value.id,
                    pdfBase64 = pdf,
                ),
            )
            SummaryStore.markUploaded(fileId)
            // Sunucunun kaydettiği PDF'in bilgisi ve kopyası da telefonda tutulur: internetsiz de PDF olarak açılır.
            try {
                SummaryStore.get(fileId)?.let { keepResultCopyLater(keepTogether(fileId, it.fileName, saved)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Özet yüklendi; yalnızca PDF'in bilgisi telefona yazılamadı (bir sonraki açılışta yazılır).
            }
            Upload.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 402) {
                SummaryStore.remove(fileId)
                Upload.PlanLimit(e.planMessage())
            } else if (SummaryQueuePolicy.isRetryable(e.code())) {
                Upload.Later
            } else {
                SummaryStore.remove(fileId)
                Upload.Rejected
            }
        } catch (e: IOException) {
            Upload.Later
        } catch (e: Exception) {
            Upload.Later
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
        try {
            NetworkPolicy.requireAllowed()
        } catch (e: Exception) {
            return@withLock
        }
        for (entry in waiting) {
            when (val result = upload(entry.fileId, entry.summary, entry.model)) {
                Upload.Done -> {
                    _completed.tryEmit(entry.fileId)
                    _notices.tryEmit("“${entry.fileName}” özeti sunucuya yüklendi.")
                }
                Upload.Later -> break // internet yok: kalanları da denemenin anlamı yok
                Upload.Rejected -> Unit
                // İnternetsizken üretilen özet, bağlanınca planın sınırına takıldı: kaydedilemedi.
                is Upload.PlanLimit -> {
                    _completed.tryEmit(entry.fileId) // satırdaki "yüklenmeyi bekliyor" görünümü kalksın
                    _notices.tryEmit("“${entry.fileName}” özeti kaydedilemedi. ${result.message}")
                }
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
        CloudJobs.init(context)
        scope.launch { flushPending() }
        resumeCloudJobs()
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
        // Uygulama özetleme sürerken kapanırsa sunucu işi yine bitirir; uygulama açılınca bu kayıtla sonuca yeniden katılır.
        CloudJobs.add(file.id, file.name)
        var stillRunningOnServer = false
        try {
            val request = SummarizeRequest(
                password = password?.takeIf { it.isNotEmpty() },
                template = AppSettings.summaryTemplate.value.id,
            )
            val saved = requestCloud(file.id, request)
            // Özet telefonda da tutulur (metin + PDF'in kopyası): internet yokken de açılır.
            keepResultCopyLater(keepTogether(file.id, file.name, saved))
        } catch (e: ServerUnreachable) {
            stillRunningOnServer = true
            throw AiFailure(
                "“${file.name}” için sunucuya ulaşılamadı. Özetleme sunucuda sürüyorsa bitince AI Sonuçlarım'da " +
                    "görünür; uygulama yeniden açılınca sonuç kendiliğinden sorulur.",
            )
        } catch (e: HttpException) {
            // 423: dosya parolalı (parola gerekiyor ya da yanlış). Parola penceresi açılır.
            if (e.code() == 423) throw PasswordException(wrong = e.passwordInvalid(sent = !password.isNullOrEmpty()))
            // 402: bulut özeti Plus'a özel (plan bilgisi eskiyse buraya gelinebilir). Plus tanıtımı açılır.
            if (e.code() == 402) throw PlusRequired(e.planMessage())
            throw AiFailure("“${file.name}” özetlenemedi: ${e.cloudMessage()}")
        } finally {
            if (!stillRunningOnServer) CloudJobs.remove(file.id)
        }
    }

    /** Sunucuya uzun süre ulaşılamadı; özetleme sunucuda sürüyor olabilir. */
    private class ServerUnreachable : Exception()

    /**
     * Bulut özet isteğini gönderir ve sonucu bekler. Bağlantı koparsa (uygulama arka planda, ağ değişti, yanıt çok uzun
     * sürdü) aynı istek yeniden gönderilir: sunucu yeni özetleme başlatmaz, süren işe katar ve bitince sonucu döner.
     * Denemeler bitince [ServerUnreachable].
     */
    private suspend fun requestCloud(fileId: String, request: SummarizeRequest): SummaryResponse {
        var attempt = 0
        while (true) {
            try {
                return ApiClient.aiApi.summarizeFile(fileId, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val wait = CloudJobPolicy.retryDelayMs(attempt++) ?: throw ServerUnreachable()
                setStatus(fileId, "Bağlantı koptu; özetleme sunucuda sürüyor…")
                delay(wait)
                setStatus(fileId, "Sunucuda özetleniyor…")
            }
        }
    }

    /**
     * Uygulama açılınca: önceki açılışta başlatılıp sonucu gelmeden kalan bulut özetlemelerine yeniden katılır. Sunucu
     * işi bitirdiyse sonuç hemen gelir ve "Özetleme bitti" gösterilir; hala sürüyorsa satırda yükleme simgesi döner ve
     * bitince gösterilir. Yeni özetleme başlatılmaz ([SummarizeRequest.joinOnly]).
     */
    private fun resumeCloudJobs() {
        val now = System.currentTimeMillis()
        for (job in CloudJobs.all()) {
            if (!CloudJobPolicy.shouldResume(job.startedAtMs, now)) {
                CloudJobs.remove(job.fileId)
                continue
            }
            if (job.fileId in _running.value) continue
            _running.update { it + job.fileId }
            setStatus(job.fileId, "Sunucuda özetleniyor…")
            runCatching { SummaryService.start(AppSettings.appContext) }
            jobs[job.fileId] = scope.launch {
                try {
                    val saved = requestCloud(job.fileId, SummarizeRequest(joinOnly = true))
                    CloudJobs.remove(job.fileId)
                    keepResultCopyLater(keepTogether(job.fileId, job.fileName, saved))
                    val file = try {
                        ApiClient.api.getFile(job.fileId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (file != null) announceDone(file) else _completed.tryEmit(job.fileId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ServerUnreachable) {
                    Unit // kayıt kalır: uygulama bir sonraki açılışta yeniden sorar
                } catch (e: HttpException) {
                    // 404: sunucuda süren iş yok (yarıda kaldı ya da durduruldu), özet de yok. Kayıt silinir.
                    CloudJobs.remove(job.fileId)
                    _completed.tryEmit(job.fileId)
                    if (e.code() == 404) {
                        _notices.tryEmit("“${job.fileName}” özetlemesi sunucuda tamamlanamadı; yeniden deneyebilirsiniz.")
                    }
                } catch (e: Exception) {
                    CloudJobs.remove(job.fileId)
                } finally {
                    _running.update { it - job.fileId }
                    _status.update { it - job.fileId }
                    jobs.remove(job.fileId)
                }
            }
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

    /** 402 yanıtındaki açıklama (`detail.message`); okunamazsa genel Plus mesajı. */
    private fun HttpException.planMessage(): String {
        val message = try {
            response()?.errorBody()?.string()?.let { JSONObject(it).optJSONObject("detail")?.optString("message") }
        } catch (e: Exception) {
            null
        }
        return message?.takeIf { it.isNotBlank() } ?: PlanPolicy.CLOUD_MODE_MESSAGE
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
        "Bu dosya türü özetlenemiyor. Metin, PDF ve Word (DOCX, DOC) dosyaları özetlenebilir."
}
