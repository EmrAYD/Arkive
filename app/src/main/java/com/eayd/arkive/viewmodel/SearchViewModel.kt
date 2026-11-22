package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.SearchHitDto
import com.eayd.arkive.utils.SearchText
import com.eayd.arkive.utils.toApiErrorMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nerede aranacağı. */
enum class SearchMode(val apiValue: String) {
    All("all"),
    Name("name"),
    Content("content"),
}

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.All,
    val results: List<SearchHitDto> = emptyList(),
    /** Sonuçların ait olduğu arama metni. */
    val searched: String = "",
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

/** Arama ekranı. Yazmaya ara verilince arar; yeni harf yarım kalan aramayı iptal eder. */
class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun setQuery(query: String) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query) }
        search(waitMs = TYPING_PAUSE_MS)
    }

    fun setMode(mode: SearchMode) {
        if (mode == _state.value.mode) return
        _state.update { it.copy(mode = mode) }
        search(waitMs = 0)
    }

    /** Klavyedeki "Ara" ya da "Tekrar dene". */
    fun searchNow() = search(waitMs = 0)

    /** Ekrana geri dönülünce sonuçları tazeler; dosya bu arada silinmiş ya da taşınmış olabilir. */
    fun refresh() {
        if (_state.value.results.isNotEmpty()) search(waitMs = 0)
    }

    private fun search(waitMs: Long) {
        job?.cancel()
        val query = _state.value.query.trim()
        if (!SearchText.canSearch(query)) {
            _state.update { it.copy(results = emptyList(), searched = "", loading = false, errorMessage = null) }
            return
        }
        val mode = _state.value.mode
        _state.update { it.copy(loading = true, errorMessage = null) }
        job = viewModelScope.launch {
            delay(waitMs)
            try {
                val hits = ApiClient.api.search(query, mode.apiValue)
                _state.update { it.copy(results = hits, searched = query, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(results = emptyList(), loading = false, errorMessage = e.toApiErrorMessage()) }
            }
        }
    }

    private companion object {
        const val TYPING_PAUSE_MS = 350L
    }
}
