package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.NetworkPolicy
import com.eayd.arkive.utils.toApiErrorMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Birden fazla ViewModel'in paylaştığı küçük parçalar. Burada ağ çağrısı yazılmaz; yalnızca
// "hata olursa kullanıcıya Türkçe mesaj ver" gibi tekrar eden kalıplar durur.

/**
 * Bir ağ işini ViewModel'in kapsamında başlatır. Hata olursa [onError] Türkçe mesajla çağrılır;
 * iptal (CancellationException) hata sayılmaz ve olduğu gibi yukarı verilir.
 */
fun ViewModel.launchCatching(onError: (String) -> Unit = {}, block: suspend () -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e.toApiErrorMessage())
        }
    }

/** Dosya için kısa süreli indirme adresi ister; gelince [onUrl], olmazsa [onError] çağrılır. */
fun ViewModel.requestDownloadUrl(fileId: String, onUrl: (String) -> Unit, onError: (String) -> Unit) {
    launchCatching(onError) {
        NetworkPolicy.requireAllowed() // "Yalnızca Wi-Fi" açıksa indirme başlatılmaz
        onUrl(ApiClient.api.getDownloadUrl(fileId).url)
    }
}

/** Birden çok öğeye yapılan işlemin bir kısmı başarısız oldu. Mesajı doğrudan kullanıcıya gösterilir. */
class BatchFailure(message: String) : Exception(message)

/**
 * Hesabın toplam depolama kotası. "Bilgi" penceresi dosyanın kotanın ne kadarını kapladığını göstermek için
 * bunu kullanır. Pencere ilk açıldığında bir kez yüklenir; yüklenemezse oran gösterilmez (kritik değil).
 */
class StorageLimitLoader(private val scope: CoroutineScope) {
    private val _limit = MutableStateFlow<Long?>(null)
    val limit: StateFlow<Long?> = _limit.asStateFlow()
    private var loading = false

    fun ensureLoaded() {
        if (_limit.value != null || loading) return
        loading = true
        scope.launch {
            try {
                _limit.value = ApiClient.api.getMe().storageLimit
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Oran gösterilmeden devam edilir.
            } finally {
                loading = false
            }
        }
    }
}
