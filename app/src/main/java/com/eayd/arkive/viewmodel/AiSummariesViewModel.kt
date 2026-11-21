package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.ai.StoredSummary
import com.eayd.arkive.ai.SummaryStore
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.ListCache
import com.eayd.arkive.data.SummaryItemDto
import com.eayd.arkive.utils.toApiErrorMessage
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiSummariesUiState(
    /** Özetler, en yenisi önce. */
    val items: List<SummaryItemDto> = emptyList(),
    val isLoading: Boolean = true,
    /** Aşağı çekerek yenileme sürüyor. */
    val refreshing: Boolean = false,
    /** Sunucuya ulaşılamadı; liste telefondaki son görüntüden gösteriliyor. */
    val offline: Boolean = false,
    /** Liste hiç yüklenemedi: ekranın ortasında "Tekrar dene" ile gösterilir. */
    val errorMessage: String? = null,
    /** Kısa bildirim. */
    val notice: String? = null,
)

/**
 * "AI Sonuçlarım" sayfası: kullanıcının yapay zeka özetleri (GET /summaries). Her öğe kaynak dosyasının adıyla
 * ("rapor.pdf özeti") gösterilir; dokununca özet penceresi açılır. Özetler ve kaynak dosyaların bilgisi telefonda da
 * tutulur ([SummaryStore], [ListCache]): internet yokken son görülen liste gösterilir ve özetler açılır.
 */
class AiSummariesViewModel : ViewModel() {

    private val _state = MutableStateFlow(AiSummariesUiState())
    val state: StateFlow<AiSummariesUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Listeyi yükler (sayfa her görününce ve "Tekrar dene"de). Liste ekranda varsa yükleme çarkı çıkmaz. */
    fun load() = fetch(refresh = false)

    /** Sessizce yeniler: ör. bir dosya özetlenince ya da özet silinince. */
    fun reloadQuietly() = fetch(refresh = false)

    /** Aşağı çekerek yenileme. Zaten yenileniyorsa ikinci bir istek başlatılmaz. */
    fun refresh() {
        if (_state.value.refreshing) return
        fetch(refresh = true)
    }

    fun showNotice(message: String) = _state.update { it.copy(notice = message) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    private fun fetch(refresh: Boolean) {
        job?.cancel()
        _state.update { it.copy(isLoading = it.items.isEmpty() && !refresh, refreshing = refresh, errorMessage = null) }
        job = viewModelScope.launch {
            try {
                val items = ApiClient.api.getSummaries()
                withContext(Dispatchers.IO) { keepOnPhone(items) }
                _state.update {
                    it.copy(items = items, isLoading = false, refreshing = false, offline = false, errorMessage = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cached = if (e is IOException) withContext(Dispatchers.IO) { fromPhone() } else null
                _state.update {
                    when {
                        cached != null -> it.copy(items = cached, isLoading = false, refreshing = false, offline = true)
                        it.items.isNotEmpty() -> it.copy(isLoading = false, refreshing = false, notice = e.toApiErrorMessage())
                        else -> it.copy(isLoading = false, refreshing = false, errorMessage = e.toApiErrorMessage())
                    }
                }
            }
        }
    }

    /** Özetlerin metni ve kaynak dosyaların bilgisi telefona yazılır (internetsiz görünüm ve ön izleme için). */
    private fun keepOnPhone(items: List<SummaryItemDto>) {
        for (item in items) {
            if (SummaryStore.get(item.file.id)?.summary == item.summary) continue // zaten telefonda
            SummaryStore.put(StoredSummary(item.file.id, item.file.name, item.summary, pending = false))
        }
        ListCache.save(CACHE_KEY, ListCache.Snapshot(folder = null, folders = emptyList(), files = items.map { it.file }))
    }

    /** İnternetsiz: son görülen liste (yalnızca metni telefonda olan özetler); hiç kaydedilmemişse null. */
    private fun fromPhone(): List<SummaryItemDto>? {
        val snapshot = ListCache.load(CACHE_KEY) ?: return null
        return snapshot.files.mapNotNull { file ->
            SummaryStore.get(file.id)?.summary?.takeIf { it.isNotBlank() }?.let { text ->
                SummaryItemDto(id = file.id, summary = text, createdAt = file.createdAt, file = file)
            }
        }
    }

    private companion object {
        const val CACHE_KEY = "summaries"
    }
}
