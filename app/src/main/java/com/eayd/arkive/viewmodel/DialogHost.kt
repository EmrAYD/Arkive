package com.eayd.arkive.viewmodel

import com.eayd.arkive.utils.toApiErrorMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Açık pencere, kaydetme durumu ve sunucu hatası. */
data class DialogState<D : Any>(val current: D? = null, val saving: Boolean = false, val error: String? = null)

/**
 * Bir ekranın pencereleri: aç, kaydet, hata göster, kapat. İşlem başarılıysa pencere kapanır, hata olursa açık kalır.
 */
class DialogHost<D : Any>(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(DialogState<D>())
    val state: StateFlow<DialogState<D>> = _state.asStateFlow()

    fun open(dialog: D) {
        _state.value = DialogState(current = dialog)
    }

    fun close() {
        _state.value = DialogState()
    }

    /** [block] başarılı olursa pencereyi kapatır, hata atarsa pencerede gösterir. */
    fun submit(block: suspend () -> Unit) {
        _state.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                block()
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.toApiErrorMessage()) }
            }
        }
    }
}
