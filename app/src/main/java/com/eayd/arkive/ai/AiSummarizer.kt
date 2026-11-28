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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import retrofit2.HttpException

/** AI düğmesinin açtığı pencereler. */
sealed interface SummaryUi {
    /** Özetleme onayı. */
    data class Confirm(val file: FileDto) : SummaryUi

    /** Süren özetlemeyi durdurma sorusu. */
    data class StopAsk(val file: FileDto) : SummaryUi

    /** Parola isteği. [wrong] önceki parolanın yanlış olduğunu söyler. */
    data class Password(val file: FileDto, val wrong: Boolean) : SummaryUi

    /** Özet penceresi. */
    data class Viewing(
        val fileId: String,
        val fileName: String,
        val text: String,
        /** Özetin altında gösterilen ek not. */
        val note: String? = null,
    ) : SummaryUi

    /** Özet silme onayı. */
    data class DeleteAsk(val fileId: String, val fileName: String) : SummaryUi

    /** Model indirme penceresi. [cloudAlternative] varsa dosya beklemeden bulutta da özetlenebilir. */
    data class ModelSetup(val cloudAlternative: FileDto? = null) : SummaryUi
}

/** Bitmiş ama penceresi açılamamış özet; listede kart olarak gösterilir. */
class SummaryDone(val file: FileDto, val savedOnPhoneOnly: Boolean)

/**
 * Dosya özetleme akışını yönetir: onay, telefonda ya da bulutta özetleme, sonucu gösterme ve bildirimler.
 *
 * Özetin nerede çıkarılacağına profildeki işlem yeri ayarı ve plan karar verir.
 */
object AiSummarizer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()

    private val _ui = MutableStateFlow<SummaryUi?>(null)
    val ui: StateFlow<SummaryUi?> = _ui.asStateFlow()

    private val _running = MutableStateFlow<Set<String>>(emptySet())

    /** Şu an özetlenen dosyalar. */
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    private val _status = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Her dosyanın o anki adımı; bildirimde gösterilir. */
    val status: StateFlow<Map<String, String>> = _status.asStateFlow()

    private fun setStatus(fileId: String, text: String) = _status.update { it + (fileId to text) }

    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Her dosyanın tahmini yüzdesi; satırda gösterilir. */
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    private val phases = java.util.concurrent.ConcurrentHashMap<String, PhaseRun>()
    private var ticker: Job? = null

    private fun setPhase(fileId: String, phase: SummaryPhase, startedAtMs: Long = System.currentTimeMillis()) {
        phases[fileId] = SummaryProgress.start(phase, currentPercent = _progress.value[fileId] ?: 0, nowMs = startedAtMs)
        tick()
        if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (phases.isNotEmpty()) {
                    delay(500)
                    tick()
                }
            }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        _progress.value = phases.mapValues { (_, run) -> SummaryProgress.percent(run, now) }
    }

    private fun clearProgress(fileId: String) {
        phases.remove(fileId)
        _progress.update { it - fileId }
    }

    private val _completed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Özeti oluşan ya da silinen dosyalar; açık listeler bunu görünce yenilenir. */
    val completed: SharedFlow<String> = _completed.asSharedFlow()

    private val opening = mutableSetOf<String>()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Uygulama içinde gösterilecek kısa mesajlar. */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _done = MutableSharedFlow<SummaryDone>(extraBufferCapacity = 8)

    /** Penceresi açılamayan bitmiş özetler. */
    val done: SharedFlow<SummaryDone> = _done.asSharedFlow()

    /** Bitmiş bir özeti açar. */
    fun openSummary(file: FileDto) = openSummary(file.id, file.name)

    /** Bildirimden gelince kullanılır; elde yalnızca kimlik ve ad vardır. Özet o an okunuyorsa sunucuya sorulmaz. */
    fun openSummary(fileId: String, fileName: String) {
        if (fileId in opening || fileId in _running.value) return
        val playing = SummaryPlayback.session
        if (playing != null && playing.fileId == fileId) {
            _ui.value = SummaryUi.Viewing(fileId, playing.fileName, playing.text, playing.note)
            return
        }
        openSaved(fileId, fileName)
    }

    /** Elde olan bir özeti sunucuya sormadan gösterir. */
    fun showSummary(fileId: String, fileName: String, text: String) {
        SummaryStore.put(StoredSummary(fileId, fileName, text, pending = false))
        _ui.value = SummaryUi.Viewing(fileId, fileName, text)
    }

    private val _pendingOpen = MutableStateFlow<SummaryOpenRequest?>(null)

    /** Bildirimden gelen, henüz açılmamış özet isteği. Giriş ekranları bitene kadar burada bekler. */
    val pendingOpen: StateFlow<SummaryOpenRequest?> = _pendingOpen.asStateFlow()

    fun requestOpenSummary(request: SummaryOpenRequest) {
        _pendingOpen.value = request
    }

    /** Bekleyen isteği alır ve siler. */
    fun takePendingOpen(): SummaryOpenRequest? = _pendingOpen.getAndUpdate { null }

    /** AI düğmesine basılınca çağrılır. */
    fun onAiClicked(file: FileDto, hasSummary: Boolean) {
        when {
            file.id in opening -> Unit
            file.id in _running.value -> _ui.value = SummaryUi.StopAsk(file)
            hasSummary -> openSaved(file.id, file.name)
            AiPolicy.kindOf(file.mimeType, file.name) == null -> _notices.tryEmit(UNSUPPORTED_MESSAGE)
            else -> askToSummarize(file)
        }
    }

    /** Özetlemeden önce planı ve modeli denetler; gerekirse onay, model indirme ya da Plus penceresini açar. */
    private fun askToSummarize(file: FileDto) {
        opening += file.id
        scope.launch {
            try {
                val quota = refreshQuota()
                val limit = quota?.limit
                val mode = PlanPolicy.effectiveMode(AppSettings.aiMode.value, cloudAllowed = quota?.cloud == true)
                when {
                    quota == null -> _notices.tryEmit(Txt.ai.needsConnection)
                    quota.full && limit != null -> PlusOffer.show(PlanPolicy.limitMessage(limit))
                    !AiPolicy.wantsModel(mode, file.size) -> {
                        val tooBig = AiPolicy.localPrecheck(file.size)
                        if (mode == AiMode.Local && tooBig != null) {
                            report(blocked(file, tooBig))
                        } else {
                            _ui.value = SummaryUi.Confirm(file)
                        }
                    }
                    else -> when (LocalModel.state.value) {
                        LocalModelState.Ready -> _ui.value = SummaryUi.Confirm(file)
                        LocalModelState.NotDownloaded ->
                            _ui.value = SummaryUi.ModelSetup(cloudAlternative = file.takeIf { mode.allowsCloud })
                        is LocalModelState.Downloading -> if (mode.allowsCloud) {
                            _ui.value = SummaryUi.ModelSetup(cloudAlternative = file)
                        } else {
                            _notices.tryEmit(Txt.localAi.stillDownloading)
                        }
                        LocalModelState.Unsupported -> when {
                            mode.allowsCloud -> _ui.value = SummaryUi.Confirm(file)
                            quota.cloud -> _notices.tryEmit(Txt.localAi.unsupportedLocalMode)
                            else -> PlusOffer.show(PlanPolicy.LOCAL_AI_UNSUPPORTED)
                        }
                    }
                }
            } finally {
                opening -= file.id
            }
        }
    }

    private fun report(error: Exception) {
        if (error is PlusRequired) PlusOffer.show(error.message) else _notices.tryEmit(error.message.orEmpty())
    }

    /** Planın haklarını sunucudan tazeler; olmazsa son bilineni döner. */
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

    /** Özetlemeyi başlatır. Parola hiçbir yerde saklanmaz. */
    fun confirm(file: FileDto, password: String? = null) = startJob(file, password, preferCloud = false)

    /** [preferCloud] true ise "Yerel + Bulut"ta dosya doğrudan bulutta özetlenir. */
    private fun startJob(file: FileDto, password: String?, preferCloud: Boolean) {
        _ui.value = null
        if (file.id in _running.value) return
        _running.update { it + file.id }
        setStatus(file.id, Txt.ai.preparing)
        setPhase(file.id, SummaryPhase.Reading)
        runCatching { SummaryService.start(AppSettings.appContext) }
        jobs[file.id] = scope.launch {
            try {
                val summary = summarize(file, password, preferCloud)
                announceDone(file, summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PasswordException) {
                _ui.value = SummaryUi.Password(file, e.wrong)
                if (!AppVisibility.visible) {
                    notifyFailure(file.id, SummaryFailedMessage.PASSWORD_TITLE, SummaryFailedMessage.passwordText(file.name))
                }
            } catch (e: PlusRequired) {
                PlusOffer.show(e.message)
                if (!AppVisibility.visible) {
                    notifyFailure(file.id, SummaryFailedMessage.TITLE, SummaryFailedMessage.text(file.name, e.message.orEmpty()))
                }
            } catch (e: AiFailure) {
                reportFailure(file.id, file.name, e.message.orEmpty())
            } catch (e: Exception) {
                reportFailure(file.id, file.name, e.toApiErrorMessage())
            } finally {
                _running.update { it - file.id }
                _status.update { it - file.id }
                clearProgress(file.id)
                jobs.remove(file.id)
                LocalJobs.remove(file.id)
            }
        }
    }

    /** Hatayı uygulama ekrandaysa içeride, değilse bildirimle gösterir. */
    private fun reportFailure(fileId: String, fileName: String, message: String) {
        if (SummaryDoneDelivery.useNotification(AppVisibility.visible, _notices.subscriptionCount.value)) {
            notifyFailure(fileId, SummaryFailedMessage.TITLE, SummaryFailedMessage.text(fileName, message))
        } else {
            _notices.tryEmit(message)
        }
    }

    private fun notifyFailure(fileId: String, title: String, text: String) =
        runCatching { SummaryNotifier.notifyFailed(AppSettings.appContext, fileId, title, text) }

    /** Biten özeti gösterir: ekrandaysa pencerede, değilse kartla ya da bildirimle. */
    private fun announceDone(file: FileDto, summary: String) {
        _completed.tryEmit(file.id)
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

    /** Süren özetlemeyi durdurur. Bulut işi için sunucuya da haber verilir. */
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
            } catch (e: Exception) {}
        }
        _notices.tryEmit(Txt.ai.stopped(file.name))
    }

    fun dismiss() {
        _ui.value = null
    }

    fun downloadModel() {
        _notices.tryEmit(LocalModel.startDownload() ?: Txt.localAi.downloadStarted)
    }

    fun askToDelete(fileId: String, fileName: String) {
        _ui.value = SummaryUi.DeleteAsk(fileId, fileName)
    }

    /** Özeti sunucudan ve telefondan siler; dosyanın kendisine dokunmaz. */
    fun deleteSummary(fileId: String, fileName: String) {
        _ui.value = null
        scope.launch {
            try {
                try {
                    ApiClient.api.deleteSummary(fileId)
                } catch (e: HttpException) {
                    if (e.code() != 404) throw e
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

    /** Kayıtlı özeti sunucudan alıp gösterir; ulaşılamazsa telefondaki kopyayı açar. */
    private fun openSaved(fileId: String, fileName: String) {
        val local = SummaryStore.get(fileId)
        if (local != null && local.pending) {
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

    /** Sunucudan gelen özeti telefona da yazar ve gösterilecek metni döner. */
    private fun keep(fileId: String, fileName: String, saved: SummaryResponse): String {
        val text = saved.summary.takeIf { it.isNotBlank() } ?: SummaryStore.get(fileId)?.summary.orEmpty()
        SummaryStore.put(StoredSummary(fileId, fileName, text, pending = false))
        return text
    }

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

    /** Mesajı doğrudan kullanıcıya gösterilen hata. */
    private class AiFailure(message: String) : Exception(message)

    /** Plus gerektiren durum; mesajıyla birlikte Plus tanıtımı açılır. */
    private class PlusRequired(message: String) : Exception(message)

    /** Dosyayı işlem yerine göre telefonda ya da bulutta özetler. */
    private suspend fun summarize(file: FileDto, password: String?, preferCloud: Boolean = false): String {
        val kind = AiPolicy.kindOf(file.mimeType, file.name) ?: throw AiFailure(UNSUPPORTED_MESSAGE)
        val mode = PlanPolicy.effectiveMode(AppSettings.aiMode.value, cloudAllowed = PlanStatus.quota.value?.cloud == true)
        val route = AiPolicy.route(mode, modelReady = LocalSummarizer.available, sizeBytes = file.size)
        return when (if (preferCloud && route == SummaryRoute.PhoneThenCloud) SummaryRoute.Cloud else route) {
            SummaryRoute.Cloud -> summarizeInCloud(file, password)
            SummaryRoute.PhoneThenCloud ->
                summarizeOnPhone(file, kind, password, cloudFallback = true) ?: summarizeInCloud(file, password)
            SummaryRoute.PhoneOnly -> {
                if (!LocalSummarizer.available) throw AiFailure(Txt.localAi.notReady)
                AiPolicy.localPrecheck(file.size)?.let { throw blocked(file, it) }
                summarizeOnPhone(file, kind, password, cloudFallback = false)
                    ?: throw AiFailure(Txt.ai.summarizeFailed(file.name, Txt.localAi.runFailed))
            }
        }
    }

    /**
     * Dosyayı telefonda özetler.
     *
     * [cloudFallback] true ise telefonda olmayınca hata yerine null döner ve dosya buluta gider. Uygulama bu sırada
     * kapanırsa iş [LocalJobs] sayesinde sonra sürdürülür.
     */
    private suspend fun summarizeOnPhone(
        file: FileDto,
        kind: SummarizableKind,
        password: String?,
        cloudFallback: Boolean,
    ): String? {
        LocalJobs.begin(file)
        try {
            return summarizeOnPhoneNow(file, kind, password, cloudFallback)
        } finally {
            LocalJobs.remove(file.id)
        }
    }

    private suspend fun summarizeOnPhoneNow(
        file: FileDto,
        kind: SummarizableKind,
        password: String?,
        cloudFallback: Boolean,
    ): String? {
        fun unsuitable(error: Exception): String? = if (cloudFallback) null else throw error

        val doc = try {
            inspect(file, kind, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PasswordException) {
            throw e
        } catch (e: UnreadableDocumentException) {
            return unsuitable(AiFailure(Txt.ai.summarizeFailed(file.name, e.message)))
        } catch (e: Exception) {
            if (e is IOException && e !is WifiRequiredException) {
                throw AiFailure(Txt.ai.notOnPhoneAndOffline(file.name))
            }
            return unsuitable(AiFailure(e.message ?: Txt.ai.fileOpenFailed))
        }
        AiPolicy.localRejection(doc.stats)?.let { reason ->
            if (reason == LocalRejection.NoText) throw blocked(file, reason)
            return unsuitable(blocked(file, reason))
        }
        val summary = runLocalModel(file, doc.text) ?: return unsuitable(
            AiFailure(Txt.ai.summarizeFailed(file.name, Txt.localAi.runFailed)),
        )
        if (summary.isBlank()) return unsuitable(AiFailure(Txt.ai.summarizeFailed(file.name, LocalRejection.NoText.message)))
        return saveLocalSummary(file, summary)
    }

    /** Dosyanın telefonda neden özetlenemediğini plana uygun bir hatayla anlatır. */
    private fun blocked(file: FileDto, reason: LocalRejection): Exception = when {
        reason == LocalRejection.NoText -> AiFailure(Txt.ai.summarizeFailed(file.name, reason.message))
        PlanStatus.quota.value?.cloud == true -> AiFailure(Txt.aiMode.localOnlyBlocked(file.name, reason.message))
        else -> PlusRequired(PlanPolicy.needsCloudMessage(file.name, reason.message))
    }

    /** Dosyayı açar; telefonda kopyası yoksa indirir ve saklar. */
    private suspend fun inspect(file: FileDto, kind: SummarizableKind, password: String?): ReadDocument {
        val context = AppSettings.appContext
        setStatus(file.id, Txt.ai.readingFile)
        InputCache.get(file.id, file.size)?.let { return DocumentReader.read(context, it, kind, password) }
        NetworkPolicy.requireAllowed()
        val temp = InputCache.tempFile(file.id)
        try {
            val url = ApiClient.api.getDownloadUrl(file.id).url
            FileDownloader.downloadTo(url, temp, maxBytes = AiPolicy.LOCAL_MAX_BYTES + 64 * 1024) {}
            val document = try {
                DocumentReader.read(context, temp, kind, password)
            } catch (e: PasswordException) {
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

    /** Listedeki küçük dosyaları ve özetleri arka planda telefona alır, internetsiz de açılabilsinler. */
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
                        return@launch
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
                return
            } catch (e: Exception) {
                continue
            }
        }
    }

    /** Metni telefondaki modelle özetler; model çalışmazsa null döner. */
    private suspend fun runLocalModel(file: FileDto, text: String): String? {
        setStatus(file.id, Txt.ai.summarizingOnPhone)
        return try {
            LocalSummarizer.summarize(text, AppSettings.summaryLanguage.value) { phase -> showLocalPhase(file.id, phase) }
                .trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun showLocalPhase(fileId: String, phase: SummaryPhase) {
        val text = when (phase) {
            SummaryPhase.LoadingModel -> Txt.ai.loadingModel
            is SummaryPhase.OnPhone ->
                if (phase.total > 1) Txt.ai.summarizingOnPhoneStep(phase.step, phase.total) else Txt.ai.summarizingOnPhone
            else -> Txt.ai.summarizingOnPhone
        }
        setStatus(fileId, text)
        setPhase(fileId, phase)
    }

    /** Telefonda çıkan özeti önce telefona, sonra sunucuya yazar. */
    private suspend fun saveLocalSummary(file: FileDto, summary: String): String {
        SummaryStore.put(StoredSummary(file.id, file.name, summary, pending = true))
        return when (val result = upload(file.id, file.name, summary)) {
            is Upload.Done -> result.summary
            Upload.Later -> summary
            Upload.Rejected -> throw AiFailure(Txt.ai.saveToServerFailed(file.name))
            is Upload.PlanLimit -> throw PlusRequired(result.message)
        }
    }

    private sealed interface Upload {
        /** Kaydedildi. */
        data class Done(val summary: String) : Upload

        /** İnternet yok; sonra yeniden denenecek. */
        data object Later : Upload

        /** Sunucu kalıcı olarak reddetti. */
        data object Rejected : Upload

        /** Planın özet sınırı dolu. */
        data class PlanLimit(val message: String) : Upload
    }

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

    /** Yüklenmeyi bekleyen telefon özetlerini sunucuya gönderir. */
    suspend fun flushPending() = flushLock.withLock {
        SummaryStore.publish()
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
                Upload.Later -> break
                Upload.Rejected -> Unit
                is Upload.PlanLimit -> {
                    _completed.tryEmit(entry.fileId)
                    reportFailure(entry.fileId, entry.fileName, Txt.ai.saveFailed(entry.fileName, result.message))
                }
            }
        }
    }

    private val _networkBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** İnternet geri gelince haber verir. */
    val networkBack: SharedFlow<Unit> = _networkBack.asSharedFlow()

    private var started = false

    /** Uygulama açılırken bir kez çağrılır. */
    fun start(context: Context) {
        if (started) return
        started = true
        SummaryStore.init(context)
        InputCache.init(context)
        CloudJobs.init(context)
        LocalJobs.init(context)
        LocalModel.init(context)
        scope.launch { LocalModel.messages.collect { _notices.tryEmit(it) } }
        scope.launch { flushPending() }
        resumeCloudJobs()
        resumeLocalJobs()
        try {
            context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _networkBack.tryEmit(Unit)
                        scope.launch { flushPending() }
                    }
                },
            )
        } catch (e: Exception) {}
    }

    /** Dosyayı sunucuda özetler. */
    private suspend fun summarizeInCloud(file: FileDto, password: String?): String {
        setStatus(file.id, Txt.ai.summarizingOnServer)
        setPhase(file.id, SummaryPhase.InCloud(file.size))
        CloudJobs.add(file.id, file.name)
        var stillRunningOnServer = false
        try {
            val request = SummarizeRequest(
                password = password?.takeIf { it.isNotEmpty() },
                language = AppSettings.summaryLanguage.value.tag,
            )
            return keep(file.id, file.name, requestCloud(file.id, request))
        } catch (e: ServerUnreachable) {
            stillRunningOnServer = true
            throw AiFailure(Txt.ai.serverUnreachableStillRunning(file.name))
        } catch (e: HttpException) {
            if (e.code() == 423) throw PasswordException(wrong = e.passwordInvalid(sent = !password.isNullOrEmpty()))
            if (e.code() == 402) throw PlusRequired(e.planMessage())
            throw AiFailure(Txt.ai.summarizeFailed(file.name, e.cloudMessage()))
        } finally {
            if (!stillRunningOnServer) CloudJobs.remove(file.id)
        }
    }

    private class ServerUnreachable : Exception()

    /** Bulut isteğini gönderir. Bağlantı koparsa aynı istek yeniden gönderilir; sunucu süren işe katar. */
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
     * Telefonda özetlenirken uygulamanın kapandığı dosyaları sürdürür.
     *
     * Bulut kullanılabiliyorsa bulutta, değilse telefonda bir kez daha dener.
     */
    private fun resumeLocalJobs() {
        val pending = LocalJobs.all()
        if (pending.isEmpty()) return
        scope.launch {
            withTimeoutOrNull(10_000) { while (!AppVisibility.visible) delay(100) }
            withTimeoutOrNull(5_000) { _notices.subscriptionCount.first { it > 0 } }
            val quota = refreshQuota()
            val mode = PlanPolicy.effectiveMode(AppSettings.aiMode.value, cloudAllowed = quota?.cloud == true)
            val now = System.currentTimeMillis()
            for (job in pending) {
                val id = job.file.id
                if (id in _running.value) continue
                if (SummaryStore.get(id) != null) {
                    LocalJobs.remove(id)
                    continue
                }
                val route = AiPolicy.route(mode, modelReady = LocalSummarizer.available, sizeBytes = job.file.size)
                val decision = LocalJobPolicy.decide(route, job.attempts, job.startedAtMs, now)
                if (decision == LocalResume.Drop) {
                    LocalJobs.remove(id)
                    continue
                }
                if (decision == LocalResume.GiveUp) {
                    LocalJobs.remove(id)
                    reportFailure(id, job.file.name, Txt.ai.phoneRunInterrupted(job.file.name))
                    continue
                }
                val file = currentFile(job.file)
                if (file == null) {
                    LocalJobs.remove(id)
                    continue
                }
                if (decision == LocalResume.InCloud) {
                    LocalJobs.remove(id)
                    _notices.tryEmit(Txt.ai.resumedInCloud(file.name))
                    startJob(file, password = null, preferCloud = true)
                } else {
                    _notices.tryEmit(Txt.ai.resumedOnPhone(file.name))
                    startJob(file, password = null, preferCloud = false)
                }
            }
        }
    }

    /** Dosyanın sunucudaki güncel hali; silinmişse ya da özeti çıkmışsa null. */
    private suspend fun currentFile(stored: FileDto): FileDto? = try {
        ApiClient.api.getFile(stored.id).takeUnless { it.hasSummary }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 404) null else stored
    } catch (e: Exception) {
        stored
    }

    /** Uygulama kapanmadan önce başlamış bulut özetlerine yeniden katılır. */
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
            setPhase(job.fileId, SummaryPhase.InCloud(sizeBytes = 0), startedAtMs = job.startedAtMs)
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
                    Unit
                } catch (e: HttpException) {
                    CloudJobs.remove(job.fileId)
                    _completed.tryEmit(job.fileId)
                    if (e.code() == 404) {
                        reportFailure(job.fileId, job.fileName, Txt.ai.serverJobFailed(job.fileName))
                    }
                } catch (e: Exception) {
                    CloudJobs.remove(job.fileId)
                } finally {
                    _running.update { it - job.fileId }
                    _status.update { it - job.fileId }
                    clearProgress(job.fileId)
                    jobs.remove(job.fileId)
                }
            }
        }
    }

    /** 423 yanıtında parolanın yanlış mı, hiç verilmemiş mi olduğu. */
    private fun HttpException.passwordInvalid(sent: Boolean): Boolean {
        val code = try {
            response()?.errorBody()?.string()?.let { JSONObject(it).optJSONObject("detail")?.optString("code") }
        } catch (e: Exception) {
            null
        }
        return if (code.isNullOrEmpty()) sent else code == "password_invalid"
    }

    /** 402 yanıtındaki açıklama. */
    private fun HttpException.planMessage(): String {
        val message = try {
            response()?.errorBody()?.string()?.let { JSONObject(it).optJSONObject("detail")?.optString("message") }
        } catch (e: Exception) {
            null
        }
        return message?.takeIf { it.isNotBlank() } ?: PlanPolicy.CLOUD_PLUS_ONLY_MESSAGE
    }

    /** Sunucunun özetleme hatasındaki açıklama; genel hata metninden daha açıklayıcıdır. */
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
