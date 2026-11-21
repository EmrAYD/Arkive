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
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.toApiErrorMessage
import java.io.IOException
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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

/** AI düğmesine basınca ya da özetleme bitince açılan pencere. Özetleme sürerken hiçbir pencere açık kalmaz. */
sealed interface SummaryUi {
    /** "Bu dosyayı özetlemek istiyor musunuz?" onayı. */
    data class Confirm(val file: FileDto) : SummaryUi

    /** Özetleme sürerken satırdaki yükleme simgesine dokunulunca: durdurulsun mu? */
    data class StopAsk(val file: FileDto) : SummaryUi

    /** Dosya parolalı: parola istenir. [wrong]: önceki parola yanlıştı. */
    data class Password(val file: FileDto, val wrong: Boolean) : SummaryUi

    /** Özet penceresi: belgenin ne olduğunu ve neleri içerdiğini anlatan paragraf. */
    data class Viewing(
        val fileId: String,
        val fileName: String,
        val text: String,
        /** Ek açıklama (ör. "Henüz sunucuya yüklenmedi"); yoksa null. */
        val note: String? = null,
    ) : SummaryUi

    /** "Özet silinsin mi?" onayı (AI Sonuçlarım sayfasından). */
    data class DeleteAsk(val fileId: String, val fileName: String) : SummaryUi
}

/**
 * Biten özetleme; özet penceresi açılamadığında (o sırada başka bir pencere açıksa) uygulama içinde kart olarak
 * gösterilir, dokunulunca [file]'ın özeti açılır. [savedOnPhoneOnly]: özet telefonda saklandı, sunucuya henüz yüklenemedi.
 */
class SummaryDone(val file: FileDto, val savedOnPhoneOnly: Boolean)

/**
 * AI düğmesinin akışı:
 *  - Dosyanın özeti varsa: düğme dolu görünür, basınca özet bir pencerede açılır ([openSummary]); yeniden özetlenmez.
 *  - Yoksa: basınca önce onay sorulur ("Bu dosyayı özetlemek istiyor musunuz?"). "Özetle" denince pencere kapanır ve
 *    özetleme arka planda sürer; o dosyanın satırında AI düğmesi yerine yükleme simgesi görünür ([running]), kullanıcı
 *    beklerken uygulamayı kullanmaya devam eder. Bitince [completed] ile listeler yenilenir (düğme dolar) ve özet
 *    penceresi açılır; uygulama arka plandaysa ya da kapalıysa sistem bildirimi çıkar ([SummaryNotifier]), dokununca
 *    özet açılır.
 *
 * Özet, belgenin ne olduğunu ve neleri içerdiğini anlatan tek paragraftır; sunucuda saklanır ve dosyanın içeriğine
 * bağlıdır (aynı içerikle yeniden yüklenen dosyanın özeti hazır gelir). "AI Sonuçlarım" sayfası bu özetlerin listesidir.
 *
 * Özetin nerede çıkarılacağı plana bağlıdır: Plus'ta sunucudaki yapay zekayla (bulut); ücretsiz planda telefonda,
 * cihazdaki yapay zekayla ([LocalSummarizer], henüz yok: o zamana kadar ücretsiz planda Plus tanıtılır).
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

    /** Özetlenen dosyaların o anki adımı ("Dosya okunuyor…", "Sunucuda özetleniyor…"); satırda ve bildirimde gösterilir. */
    val status: StateFlow<Map<String, String>> = _status.asStateFlow()

    private fun setStatus(fileId: String, text: String) = _status.update { it + (fileId to text) }

    private val _completed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * Özeti oluşan ya da silinen dosyanın kimliği. Açık liste bunu görünce kendini sessizce yeniler ([FileDto.hasSummary]
     * güncellenir; AI Sonuçlarım listesi değişir).
     */
    val completed: SharedFlow<String> = _completed.asSharedFlow()

    /** Özet açılırken (ağ isteği sürerken) tekrar basışları yok saymak için. */
    private val opening = mutableSetOf<String>()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Kısa bildirimler: özetleme başarısız oldu / durduruldu, özet silindi… */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _done = MutableSharedFlow<SummaryDone>(extraBufferCapacity = 8)

    /**
     * Özetleme bitti ama özet penceresi açılamadı (o sırada başka bir pencere açıktı). Açık olan liste ekranı bunu
     * dokunulabilir bir kart olarak gösterir ([com.eayd.arkive.ui.components.SummaryDoneBanner]); dokunulunca
     * [openSummary] ile özet açılır.
     */
    val done: SharedFlow<SummaryDone> = _done.asSharedFlow()

    /** Bitmiş özeti açar. "Özetleme bitti" kartına dokunulunca çağrılır. Dosya hala özetleniyorsa bir şey yapmaz. */
    fun openSummary(file: FileDto) = openSummary(file.id, file.name)

    /** Aynısı, yalnızca kimlik ve adla (bildirime dokunulunca elde dosyanın kendisi yoktur, bunlar vardır). */
    fun openSummary(fileId: String, fileName: String) {
        if (fileId in opening || fileId in _running.value) return
        openSaved(fileId, fileName)
    }

    /** Elde olan bir özeti (AI Sonuçlarım listesinden) doğrudan pencerede gösterir; sunucuya yeniden sorulmaz. */
    fun showSummary(fileId: String, fileName: String, text: String) {
        SummaryStore.put(StoredSummary(fileId, fileName, text, pending = false))
        _ui.value = SummaryUi.Viewing(fileId, fileName, text)
    }

    private val _pendingOpen = MutableStateFlow<SummaryOpenRequest?>(null)

    /**
     * Bildirime dokunularak gelen, henüz yerine getirilmemiş "şu dosyanın özetini aç" isteği. Uygulama kapalıyken
     * dokunulduysa oturum/açılış ekranları bitmeden gezinilemez; bu yüzden istek burada bekler, ana gezinme
     * ([com.eayd.arkive.navigation.ArkiveNavHost]) hazır olunca alıp yerine getirir.
     */
    val pendingOpen: StateFlow<SummaryOpenRequest?> = _pendingOpen.asStateFlow()

    /** Bildirimden gelen isteği kaydeder ([com.eayd.arkive.MainActivity]). */
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
     * Özetleme onayından önce planın hakları denetlenir (sunucudan tazelenir; internet yoksa son bilinen değer):
     *  - Plus (bulut özeti açık): onay sorulur.
     *  - Ücretsiz plan: telefonda özetlenir. Cihazdaki model henüz yoksa ([LocalSummarizer.available]) ya da özet sınırı
     *    ([SummaryQuota.full]) doluysa onay yerine nedeni yazılı Plus tanıtımı açılır.
     *  - Plan hiç bilinmiyorsa (internet yok): özetleme sunucu olmadan yapılamaz, bu söylenir.
     */
    private fun askToSummarize(file: FileDto) {
        opening += file.id
        scope.launch {
            try {
                val quota = refreshQuota()
                val limit = quota?.limit
                when {
                    quota == null -> _notices.tryEmit(Txt.ai.needsConnection)
                    quota.cloud -> _ui.value = SummaryUi.Confirm(file)
                    !LocalSummarizer.available -> PlusOffer.show(PlanPolicy.LOCAL_AI_COMING_SOON)
                    quota.full && limit != null -> PlusOffer.show(PlanPolicy.limitMessage(limit))
                    else -> _ui.value = SummaryUi.Confirm(file)
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
     * kullanıcının girdiği parola; bulutta özetlenecekse yalnızca o isteğe eklenir, telefonda özetlenecekse dosyayı
     * açmak için kullanılır. Hiçbir yerde saklanmaz.
     */
    fun confirm(file: FileDto, password: String? = null) {
        _ui.value = null
        if (file.id in _running.value) return
        _running.update { it + file.id }
        setStatus(file.id, Txt.ai.preparing)
        // Uygulama arka plana alınsa da iş sürsün: ön plan servisi süreci canlı tutar.
        runCatching { SummaryService.start(AppSettings.appContext) }
        jobs[file.id] = scope.launch {
            try {
                val summary = summarize(file, password)
                announceDone(file, summary)
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

    /**
     * Özetleme bitti: açık liste yenilenir ve özet penceresi açılır. Uygulama arka plandaysa (ya da kapalıysa) sistem
     * bildirimi çıkar; o sırada başka bir pencere açıksa "Özetleme bitti" kartı (kartı gösterecek ekran yoksa bildirim).
     */
    private fun announceDone(file: FileDto, summary: String) {
        _completed.tryEmit(file.id)
        // Telefonda üretilip internet yüzünden yüklenemeyen özet de açılır (telefondaki kopyadan); pencere bunu belirtir.
        val onPhoneOnly = SummaryStore.get(file.id)?.pending == true
        when {
            AppVisibility.visible && _ui.value == null -> _ui.value = SummaryUi.Viewing(
                file.id, file.name, summary, note = if (onPhoneOnly) Txt.ai.notUploadedYet else null,
            )
            SummaryDoneDelivery.useNotification(AppVisibility.visible, _done.subscriptionCount.value) ->
                SummaryNotifier.notifyDone(AppSettings.appContext, SummaryOpenRequest(file.id, file.name), onPhoneOnly)
            else -> _done.tryEmit(SummaryDone(file, savedOnPhoneOnly = onPhoneOnly))
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
        _notices.tryEmit(Txt.ai.stopped(file.name))
    }

    /** Pencereyi kapatır (özetleme çalışıyorsa sürer). */
    fun dismiss() {
        _ui.value = null
    }

    /** "Özeti sil" onayını açar. */
    fun askToDelete(fileId: String, fileName: String) {
        _ui.value = SummaryUi.DeleteAsk(fileId, fileName)
    }

    /**
     * Dosyanın özetini siler (sunucuda ve telefonda). Dosyanın kendisi silinmez; düğmesi boşalır, yeniden özetlenebilir
     * ve ücretsiz plandaki özet hakkı geri gelir.
     */
    fun deleteSummary(fileId: String, fileName: String) {
        _ui.value = null
        scope.launch {
            try {
                try {
                    ApiClient.api.deleteSummary(fileId)
                } catch (e: HttpException) {
                    if (e.code() != 404) throw e // 404: özet zaten yok
                }
                SummaryStore.remove(fileId)
                _completed.tryEmit(fileId)
                _notices.tryEmit(Txt.ai.summaryDeleted(fileName))
                refreshQuota()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notices.tryEmit(e.toApiErrorMessage())
            }
        }
    }

    /**
     * Kayıtlı özeti açar: sunucudan alınır ve pencerede gösterilir (bu sırada satırda yükleme simgesi döner). Sunucuya
     * ulaşılamazsa telefondaki kopyadan gösterilir.
     */
    private fun openSaved(fileId: String, fileName: String) {
        val local = SummaryStore.get(fileId)
        if (local != null && local.pending) {
            // Telefonda üretildi ama henüz sunucuya yüklenmedi: özet telefondaki kopyadan gösterilir, yükleme yeniden denenir.
            _ui.value = SummaryUi.Viewing(fileId, fileName, local.summary, note = Txt.ai.notUploadedYet)
            scope.launch { flushPending() }
            return
        }
        opening += fileId
        _running.update { it + fileId }
        setStatus(fileId, Txt.ai.openingSummary)
        scope.launch {
            try {
                val saved = ApiClient.api.getSummary(fileId)
                val text = keep(fileId, fileName, saved)
                _ui.value = SummaryUi.Viewing(fileId, fileName, text)
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

    /** Sunucuya ulaşılamayınca kayıtlı özeti telefondaki kopyadan gösterir; kopya yoksa nedeni söylenir. */
    private fun openOffline(fileId: String, fileName: String, error: Exception) {
        val cached = SummaryStore.get(fileId)
        when {
            cached != null && cached.summary.isNotBlank() -> _ui.value = SummaryUi.Viewing(
                fileId, fileName, cached.summary, note = Txt.ai.shownFromPhoneCopy,
            )
            error is IOException || error is HttpException -> _notices.tryEmit(Txt.ai.noPhoneCopy)
            else -> _notices.tryEmit(error.toApiErrorMessage())
        }
    }

    /**
     * Sunucudan gelen özeti telefona yazar (internetsizken de açılsın diye) ve gösterilecek metni döndürür. Sunucunun
     * metni boşsa telefondaki önceki metin korunur.
     */
    private fun keep(fileId: String, fileName: String, saved: SummaryResponse): String {
        val text = saved.summary.takeIf { it.isNotBlank() } ?: SummaryStore.get(fileId)?.summary.orEmpty()
        SummaryStore.put(StoredSummary(fileId, fileName, text, pending = false))
        return text
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

    /** Dosyayı planın izin verdiği yerde özetler ve özetin metnini döndürür. */
    private suspend fun summarize(file: FileDto, password: String?): String {
        val kind = AiPolicy.kindOf(file.mimeType, file.name) ?: throw AiFailure(UNSUPPORTED_MESSAGE)
        // Plus: her zaman bulutta. Ücretsiz plan (ya da plan bilinmiyor): yalnızca telefonda, cihazdaki yapay zekayla.
        if (PlanStatus.quota.value?.cloud == true) return summarizeInCloud(file, password)
        if (!LocalSummarizer.available) throw PlusRequired(PlanPolicy.LOCAL_AI_COMING_SOON)
        AiPolicy.localPrecheck(file.size)?.let { throw blocked(file, it) }
        val doc = try {
            inspect(file, kind, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PasswordException) {
            throw e // parola gerekiyor ya da yanlış: kullanıcıdan istenir
        } catch (e: UnreadableDocumentException) {
            throw AiFailure(Txt.ai.summarizeFailed(file.name, e.message))
        } catch (e: Exception) {
            // Dosyanın telefonda kopyası yok ve internet de yok: nedeni söylenir.
            if (e is IOException && e !is WifiRequiredException) {
                throw AiFailure(Txt.ai.notOnPhoneAndOffline(file.name))
            }
            throw AiFailure(e.message ?: Txt.ai.fileOpenFailed)
        }
        AiPolicy.localRejection(doc.stats)?.let { throw blocked(file, it) }
        return summarizeOnDevice(file, doc.text)
    }

    /** Dosya telefonda özetlenemiyor: metni yoksa nedeni söylenir; büyük/uzun/görselliyse Plus'ta bulutta özetlenebilir. */
    private fun blocked(file: FileDto, reason: LocalRejection): Exception =
        if (reason == LocalRejection.NoText) {
            AiFailure(Txt.ai.summarizeFailed(file.name, reason.message))
        } else {
            PlusRequired(PlanPolicy.needsCloudMessage(file.name, reason.message))
        }

    /**
     * Dosyayı (en çok 1 MB) açar. Telefonda kopyası varsa ([InputCache]) internet gerekmez; yoksa indirilir ve indirilen
     * dosya sonraki sefer için kopya olarak saklanır.
     */
    private suspend fun inspect(file: FileDto, kind: SummarizableKind, password: String?): ReadDocument {
        val context = AppSettings.appContext
        setStatus(file.id, Txt.ai.readingFile)
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
     * kopyalar (internet yokken de ön izlenebilsinler) ve özeti olan dosyaların özetini telefona alır (internet yokken
     * de açılsın). "Yalnızca Wi-Fi" açıkken mobil veride indirmez.
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
                }.sortedBy { it.hasSummary }.take(InputCache.MAX_PER_PASS)
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

    /** Özeti olan ama telefonda kopyası olmayan dosyaların özetini sunucudan alıp telefona yazar. */
    private suspend fun prefetchSummaries(files: List<FileDto>) {
        val wanted = files.filter { f ->
            f.hasSummary && f.id !in _running.value && f.id !in opening &&
                SummaryStore.get(f.id).let { stored -> stored == null || (!stored.pending && stored.summary.isBlank()) }
        }.take(InputCache.MAX_PER_PASS)
        for (f in wanted) {
            try {
                keep(f.id, f.name, ApiClient.api.getSummary(f.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return // internet yok
            } catch (e: Exception) {
                continue // bu dosyanın özeti alınamadı (silinmiş olabilir); sonraki listede yeniden denenir
            }
        }
    }

    /**
     * Telefonda, cihazdaki yapay zekayla özetler ([LocalSummarizer]). Özet önce telefona, sonra sunucuya yazılır;
     * internet yoksa telefonda bekler ve bağlantı gelince yüklenir.
     */
    private suspend fun summarizeOnDevice(file: FileDto, text: String): String {
        setStatus(file.id, Txt.ai.summarizingOnPhone)
        val summary = LocalSummarizer.summarize(text, AppSettings.summaryLanguage.value).trim()
        if (summary.isBlank()) throw AiFailure(Txt.ai.summarizeFailed(file.name, LocalRejection.NoText.message))
        // Önce telefona yazılır: yükleme sırasında internet kesilse ya da uygulama kapansa özet kaybolmaz.
        SummaryStore.put(StoredSummary(file.id, file.name, summary, pending = true))
        // Sonra sunucuya kaydedilir; aynı içerikli dosyalar da böylece "özetlendi" olur.
        return when (val result = upload(file.id, file.name, summary)) {
            is Upload.Done -> result.summary
            // Özet telefonda saklandı, bağlantı gelince yüklenecek; özet penceresi bunu belirtir.
            Upload.Later -> summary
            Upload.Rejected -> throw AiFailure(Txt.ai.saveToServerFailed(file.name))
            is Upload.PlanLimit -> throw PlusRequired(result.message)
        }
    }

    private sealed interface Upload {
        /** Kaydedildi. [summary]: sunucudaki özet (dosyanın zaten özeti varsa o döner). */
        data class Done(val summary: String) : Upload

        /** Şimdi yüklenemedi (internet yok / geçici sunucu sorunu); özet telefonda bekler. */
        data object Later : Upload

        /** Sunucu kalıcı olarak reddetti (dosya silinmiş, tür desteklenmiyor…); telefondaki kayıt atılır. */
        data object Rejected : Upload

        /** Planın özet sınırı dolu (402); telefondaki kayıt atılır. [message]: sunucunun açıklaması. */
        data class PlanLimit(val message: String) : Upload
    }

    /** Telefondaki bekleyen özeti sunucuya yükler ve sonucu söyler; telefondaki kayıt buna göre güncellenir/silinir. */
    private suspend fun upload(fileId: String, fileName: String, summary: String): Upload =
        try {
            val saved = ApiClient.api.saveSummary(fileId, SaveSummaryRequest(summary))
            Upload.Done(keep(fileId, fileName, saved))
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
            when (val result = upload(entry.fileId, entry.fileName, entry.summary)) {
                is Upload.Done -> {
                    _completed.tryEmit(entry.fileId)
                    _notices.tryEmit(Txt.ai.uploadedToServer(entry.fileName))
                }
                Upload.Later -> break // internet yok: kalanları da denemenin anlamı yok
                Upload.Rejected -> Unit
                // İnternetsizken üretilen özet, bağlanınca planın sınırına takıldı: kaydedilemedi.
                is Upload.PlanLimit -> {
                    _completed.tryEmit(entry.fileId) // satırdaki "yüklenmeyi bekliyor" görünümü kalksın
                    _notices.tryEmit(Txt.ai.saveFailed(entry.fileName, result.message))
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

    /** Sunucudaki yapay zekayla özetler (Plus) ve özetin metnini döndürür. */
    private suspend fun summarizeInCloud(file: FileDto, password: String?): String {
        setStatus(file.id, Txt.ai.summarizingOnServer)
        // Uygulama özetleme sürerken kapanırsa sunucu işi yine bitirir; uygulama açılınca bu kayıtla sonuca yeniden katılır.
        CloudJobs.add(file.id, file.name)
        var stillRunningOnServer = false
        try {
            val request = SummarizeRequest(
                password = password?.takeIf { it.isNotEmpty() },
                language = AppSettings.summaryLanguage.value.tag,
            )
            // Özet telefonda da tutulur: internet yokken de açılır.
            return keep(file.id, file.name, requestCloud(file.id, request))
        } catch (e: ServerUnreachable) {
            stillRunningOnServer = true
            throw AiFailure(Txt.ai.serverUnreachableStillRunning(file.name))
        } catch (e: HttpException) {
            // 423: dosya parolalı (parola gerekiyor ya da yanlış). Parola penceresi açılır.
            if (e.code() == 423) throw PasswordException(wrong = e.passwordInvalid(sent = !password.isNullOrEmpty()))
            // 402: bulut özeti Plus'a özel (plan bilgisi eskiyse buraya gelinebilir). Plus tanıtımı açılır.
            if (e.code() == 402) throw PlusRequired(e.planMessage())
            throw AiFailure(Txt.ai.summarizeFailed(file.name, e.cloudMessage()))
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
                setStatus(fileId, Txt.ai.connectionLostStillRunning)
                delay(wait)
                setStatus(fileId, Txt.ai.summarizingOnServer)
            }
        }
    }

    /**
     * Uygulama açılınca: önceki açılışta başlatılıp sonucu gelmeden kalan bulut özetlemelerine yeniden katılır. Sunucu
     * işi bitirdiyse sonuç hemen gelir ve özet gösterilir; hala sürüyorsa satırda yükleme simgesi döner ve bitince
     * gösterilir. Yeni özetleme başlatılmaz ([SummarizeRequest.joinOnly]).
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
            setStatus(job.fileId, Txt.ai.summarizingOnServer)
            runCatching { SummaryService.start(AppSettings.appContext) }
            jobs[job.fileId] = scope.launch {
                try {
                    val saved = requestCloud(job.fileId, SummarizeRequest(joinOnly = true))
                    CloudJobs.remove(job.fileId)
                    val summary = keep(job.fileId, job.fileName, saved)
                    val file = try {
                        ApiClient.api.getFile(job.fileId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (file != null) announceDone(file, summary) else _completed.tryEmit(job.fileId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ServerUnreachable) {
                    Unit // kayıt kalır: uygulama bir sonraki açılışta yeniden sorar
                } catch (e: HttpException) {
                    // 404: sunucuda süren iş yok (yarıda kaldı ya da durduruldu), özet de yok. Kayıt silinir.
                    CloudJobs.remove(job.fileId)
                    _completed.tryEmit(job.fileId)
                    if (e.code() == 404) {
                        _notices.tryEmit(Txt.ai.serverJobFailed(job.fileName))
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
        return message?.takeIf { it.isNotBlank() } ?: PlanPolicy.CLOUD_PLUS_ONLY_MESSAGE
    }

    /**
     * Sunucunun özetleme hataları: genel hata çevirisi 422/502 gibi kodları kullanıcıya anlamsız bir mesaja çevirir;
     * oysa sunucunun açıklaması ("metin çıkarılamadı", "yapılandırılmamış"…) tam olarak neyin yanlış olduğunu söyler.
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

    private val UNSUPPORTED_MESSAGE: String
        get() = Txt.ai.unsupportedType
}
