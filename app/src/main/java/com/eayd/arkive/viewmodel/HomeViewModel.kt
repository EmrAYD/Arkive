package com.eayd.arkive.viewmodel

import androidx.lifecycle.ViewModel
import com.eayd.arkive.data.ApiClient
import com.eayd.arkive.data.MeResponse
import com.eayd.arkive.data.PlanStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Kullanıcının planı ve depolama durumu. */
data class StorageInfo(
    val planName: String,
    val used: Long,
    val limit: Long,
    val maxFileSize: Long,
) {
    /** Ücretsiz plan mı. */
    val isFree: Boolean get() = planName.equals("Free", ignoreCase = true)

    val remaining: Long get() = (limit - used).coerceAtLeast(0)

    /** Doluluk oranı, 0 ile 1 arası. */
    val fraction: Float get() = if (limit <= 0) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)
}

fun MeResponse.toStorageInfo() = StorageInfo(planName, usedStorage, storageLimit, maxFileSize)

/** Yan menü ve profil düğmesi için gereken bilgiler. */
data class HomeUiState(
    /** Oturumdaki kullanıcının e-postası. */
    val userEmail: String,
    val isLoading: Boolean = true,
    val storage: StorageInfo? = null,
    val error: String? = null,
)

/** Ana ekranın durumu. Menü her açıldığında [refresh] ile kullanım bilgisi tazelenir. */
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
            onError = { message -> _state.update { it.copy(isLoading = false, error = message) } },
        ) {
            val me = ApiClient.api.getMe()
            PlanStatus.update(me.summaryQuota())
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
