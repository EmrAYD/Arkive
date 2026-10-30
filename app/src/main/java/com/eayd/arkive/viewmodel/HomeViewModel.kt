package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.MeResponse
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Kullanıcının planı ve depolama durumu (bayt cinsinden). [maxFileSize]: tek dosya için üst sınır. */
data class StorageInfo(
    val planName: String,
    val used: Long,
    val limit: Long,
    val maxFileSize: Long,
) {
    /** Ücretsiz plandaysa Plus tanıtımı gösterilir. */
    val isFree: Boolean get() = planName.equals("Free", ignoreCase = true)

    val remaining: Long get() = (limit - used).coerceAtLeast(0)

    /** 0.0 - 1.0 arası doluluk oranı (ilerleme çubuğu için). */
    val fraction: Float get() = if (limit <= 0) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)
}

fun MeResponse.toStorageInfo() = StorageInfo(planName, usedStorage, storageLimit, maxFileSize)

/** Ana ekranın yan menüsü ve profil düğmesi için gereken bilgiler. */
data class HomeUiState(
    /** Oturumdaki kullanıcının e-postası (profil simgesi için); uygulama kullanıcı adı tutmaz. */
    val userEmail: String,
    val isLoading: Boolean = true,
    val storage: StorageInfo? = null,
    val error: String? = null,
)

/**
 * Ana ekranın durumu: giriş yapmış kullanıcının e-postası ve yan menüdeki "kalan kullanım" bilgisi (GET /me).
 * Menü her açıldığında [refresh] çağrılır; böylece dosya yükleyip sildikçe değer güncel kalır.
 */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        FirebaseAuth.getInstance().currentUser.let { user ->
            HomeUiState(userEmail = user?.email.orEmpty())
        }
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        launchCatching(
            // Eski bilgi varsa ekranda kalsın; yalnızca hata mesajını ekle.
            onError = { message -> _state.update { it.copy(isLoading = false, error = message) } },
        ) {
            val me = ApiClient.api.getMe()
            _state.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    storage = me.toStorageInfo(),
                )
            }
        }
    }
}
