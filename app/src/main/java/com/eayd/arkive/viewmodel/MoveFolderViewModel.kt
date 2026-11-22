package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.data.FolderLocations
import com.eayd.arkive.i18n.Txt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** "Taşı" penceresindeki klasör ağacında gezinti. [path] kökten şu anki klasöre kadar olan klasörler. */
data class MoveFolderState(
    val path: List<FolderDto> = emptyList(),
    val folders: List<FolderDto>? = null,
    val error: String? = null,
    /** Gezinilen sayfa. */
    val location: String = FolderLocations.HOME,
) {
    /** Sayfanın en üstünün adı. */
    val rootName: String get() = Txt.home.home

    /** Şu anki klasör; en üstteyse null. */
    val currentId: String? get() = path.lastOrNull()?.id
    val currentName: String get() = path.lastOrNull()?.name ?: rootName
    val breadcrumb: String get() = (listOf(rootName) + path.map { it.name }).joinToString("  ›  ")
}

class MoveFolderViewModel : ViewModel() {

    private val _state = MutableStateFlow(MoveFolderState())
    val state: StateFlow<MoveFolderState> = _state.asStateFlow()

    private var job: Job? = null

    /** Pencere her açıldığında [location] sayfasının en üstünden başlar. */
    fun start(location: String = FolderLocations.HOME) {
        _state.value = MoveFolderState(location = location)
        go(emptyList())
    }

    fun open(folder: FolderDto) = go(_state.value.path + folder)

    fun up() = go(_state.value.path.dropLast(1))

    fun retry() = go(_state.value.path)

    /** Pencere kapandı; bekleyen istek iptal edilir. */
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
            val children = ApiClient.api.getFolders(
                parentId = parentId,
                location = if (parentId == null) location else null,
            ).sortedBy { it.name.lowercase() }
            _state.update { it.copy(folders = children) }
        }
    }
}
