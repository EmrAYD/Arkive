package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.data.FolderLocations
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * "Taşı" penceresindeki klasör ağacında gezinti. Liste ekranı ve ön izleme ekranı aynı pencereyi kullanır;
 * klasörleri sunucudan bu ViewModel getirir.
 *
 * [path]: kökten şu anki klasöre inen klasörler (boşsa "Ana Sayfa"nın en üstündeyiz). [folders]: şu anki
 * klasörün alt klasörleri; null ise yükleniyor.
 */
data class MoveFolderState(
    val path: List<FolderDto> = emptyList(),
    val folders: List<FolderDto>? = null,
    val error: String? = null,
    /** Gezinilen sayfa: [FolderLocations.HOME] ya da [FolderLocations.AI_RESULTS]. */
    val location: String = FolderLocations.HOME,
) {
    /** Sayfanın en üstünün adı. */
    val rootName: String get() = if (location == FolderLocations.AI_RESULTS) "AI Sonuçlarım" else "Ana Sayfa"

    /** Şu anki klasörün kimliği (null = sayfanın en üstü). */
    val currentId: String? get() = path.lastOrNull()?.id
    val currentName: String get() = path.lastOrNull()?.name ?: rootName
    val breadcrumb: String get() = (listOf(rootName) + path.map { it.name }).joinToString("  ›  ")
}

class MoveFolderViewModel : ViewModel() {

    private val _state = MutableStateFlow(MoveFolderState())
    val state: StateFlow<MoveFolderState> = _state.asStateFlow()

    private var job: Job? = null

    /** Pencere her açıldığında çağrılır: [location] sayfasının en üstünden başlar. */
    fun start(location: String = FolderLocations.HOME) {
        _state.value = MoveFolderState(location = location)
        go(emptyList())
    }

    fun open(folder: FolderDto) = go(_state.value.path + folder)

    fun up() = go(_state.value.path.dropLast(1))

    fun retry() = go(_state.value.path)

    /** Pencere kapandı: bekleyen istek iptal edilir, gezinti sıfırlanır. */
    fun clear() {
        job?.cancel()
        _state.value = MoveFolderState(location = _state.value.location)
    }

    private fun go(path: List<FolderDto>) {
        job?.cancel()
        val location = _state.value.location
        _state.value = MoveFolderState(path = path, location = location)
        val parentId = path.lastOrNull()?.id
        job = launchCatching(onError = { message -> _state.update { it.copy(error = message) } }) {
            // Normal dosyalar ana sayfaya, yapay zeka sonuç dosyaları AI Sonuçlarım'a taşınır: kökte yalnızca o sayfanın klasörleri istenir.
            val children = ApiClient.api.getFolders(
                parentId = parentId,
                location = if (parentId == null) location else null,
            ).sortedBy { it.name.lowercase() }
            _state.update { it.copy(folders = children) }
        }
    }
}
