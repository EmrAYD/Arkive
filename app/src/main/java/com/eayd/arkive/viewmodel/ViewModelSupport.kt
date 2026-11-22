package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.FileSaver
import com.eayd.arkive.data.NetworkPolicy
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.toApiErrorMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Bir işi ViewModel'in kapsamında başlatır; hata olursa [onError]'a mesajı verir. İptal hata sayılmaz. */
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

private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Dosyayı İndirilenler klasörüne kaydeder; ilerleme ve sonuç [notify] ile bildirilir. */
fun saveFileToDevice(fileId: String, fileName: String, mimeType: String?, notify: (String) -> Unit) {
    downloadScope.launch {
        try {
            NetworkPolicy.requireAllowed()
            notify(Txt.files.downloading(fileName))
            val url = ApiClient.api.getDownloadUrl(fileId).url
            val where = FileSaver.saveToDownloads(url, fileName, mimeType)
            notify(Txt.files.downloadResult(fileName, where))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify(e.toApiErrorMessage())
        }
    }
}

/** Toplu işlemin bir kısmı başarısız oldu. */
class BatchFailure(message: String) : Exception(message)

/** Hesabın toplam kotası; "Bilgi" penceresi ilk açıldığında bir kez yüklenir. */
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
            } catch (e: Exception) {} finally {
                loading = false
            }
        }
    }
}
