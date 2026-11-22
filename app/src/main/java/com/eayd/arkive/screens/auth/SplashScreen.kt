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
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.theme.arkiveGradientBackground
import com.eayd.arkive.viewmodel.AuthUiState
import com.eayd.arkive.viewmodel.AuthViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        delay(2000.milliseconds)
        viewModel.checkAuthStatus()
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                onNavigateToHome()
                viewModel.resetState()
            }
            is AuthUiState.Unauthenticated -> {
                onNavigateToLogin()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "floatingAnimation")
    val translationY by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translationY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .arkiveGradientBackground(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.loading_background),
            contentDescription = Txt.auth.splashBackgroundA11y,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.BottomCenter
        )

        Image(
            painter = painterResource(id = R.drawable.loading_chest),
            contentDescription = Txt.auth.chestA11y,
            modifier = Modifier.offset(y = (-40).dp)
                .graphicsLayer {
                    this.translationY = translationY
                }
        )
    }
}