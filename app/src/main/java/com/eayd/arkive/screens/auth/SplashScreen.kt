package com.eayd.arkive.screens.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.R
import com.eayd.arkive.ui.theme.arkiveGradientBackground
import com.eayd.arkive.viewmodel.AuthUiState
import com.eayd.arkive.viewmodel.AuthViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit, // Giriş yapmışsa doğrudan ana ekrana git
    viewModel: AuthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Ekran ilk açıldığında auth kontrolünü başlat
    LaunchedEffect(Unit) {
        // İsteğe bağlı: Kullanıcının logoyu/animasyonu görebilmesi için kısa bir gecikme
        delay(2000.milliseconds)
        viewModel.checkAuthStatus()
    }

    // State değişikliklerini dinle
    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                onNavigateToHome()
                viewModel.resetState() // Navigasyon sonrası state'i sıfırla
            }
            is AuthUiState.Unauthenticated -> {
                onNavigateToLogin()
                viewModel.resetState()
            }
            else -> {
                // Idle, Loading veya Error durumlarında ekranda bekleyebiliriz.
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "floatingAnimation")
    val translationY by infiniteTransition.animateFloat(
        initialValue = -40f, // Yukarı hareket mesafesi
        targetValue = 40f,  // Aşağı hareket mesafesi
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse // Aşağı gidince tekrar yukarı çıksın
        ),
        label = "translationY"
    )

    // Katman (z-sırası) için Box kullanıyoruz: Box içinde ÖNCE eklenen ALTTA,
    // SONRA eklenen ÜSTTE görünür. Column'da görseller alt alta dizilir, üst üste binmez.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .arkiveGradientBackground(),
        contentAlignment = Alignment.Center
    ) {
        // Alt katman: arkaplan (bulutlar) — önce eklendiği için altta kalır
        Image(
            painter = painterResource(id = R.drawable.loading_background),
            contentDescription = "Yüklenme arkaplanı",
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.BottomCenter
        )

        // Üst katman: sandık — sonra eklendiği için üstte görünür, yüzen animasyonlu
        Image(
            painter = painterResource(id = R.drawable.loading_chest),
            contentDescription = "Sandık",
            modifier = Modifier.offset(y = (-40).dp) // boyutu beğeninize göre ayarlayın
                .graphicsLayer {
                    this.translationY = translationY
                }
        )
    }
}