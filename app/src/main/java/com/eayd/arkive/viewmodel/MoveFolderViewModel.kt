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
) {
    /** Şu anki klasörün kimliği (null = ana sayfanın en üstü). */
    val currentId: String? get() = path.lastOrNull()?.id
    val currentName: String get() = path.lastOrNull()?.name ?: "Ana Sayfa"
    val breadcrumb: String get() = (listOf("Ana Sayfa") + path.map { it.name }).joinToString("  ›  ")
}

class MoveFolderViewModel : ViewModel() {

    private val _state = MutableStateFlow(MoveFolderState())
    val state: StateFlow<MoveFolderState> = _state.asStateFlow()

    private var job: Job? = null

    /** Pencere her açıldığında çağrılır: en üstten başlar. */
    fun start() = go(emptyList())

    fun open(folder: FolderDto) = go(_state.value.path + folder)

    fun up() = go(_state.value.path.dropLast(1))

    fun retry() = go(_state.value.path)

    /** Pencere kapandı: bekleyen istek iptal edilir, gezinti sıfırlanır. */
    fun clear() {
        job?.cancel()
        _state.value = MoveFolderState()
    }

    private fun go(path: List<FolderDto>) {
        job?.cancel()
        _state.value = MoveFolderState(path = path)
        val parentId = path.lastOrNull()?.id
        job = launchCatching(onError = { message -> _state.update { it.copy(error = message) } }) {
            // Dosyalar yalnızca "home" konumuna taşınabildiği için kökte yalnızca onun klasörleri istenir.
            val children = ApiClient.api.getFolders(
                parentId = parentId,
                location = if (parentId == null) FolderLocations.HOME else null,
            ).sortedBy { it.name.lowercase() }
            _state.update { it.copy(folders = children) }
        }
    }
}
