package com.eayd.arkive.screens.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.viewmodel.AuthUiState
import com.eayd.arkive.viewmodel.AuthViewModel
import com.eayd.arkive.viewmodel.EmailCheck
import com.eayd.arkive.viewmodel.PasswordResetUiState

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val emailCheck by viewModel.emailCheck.collectAsState()
    val passwordResetState by viewModel.passwordResetState.collectAsState()
    val loading = uiState is AuthUiState.Loading

    NavigateOnSuccess(uiState, onLoginSuccess, viewModel::resetState)

    AuthScreenLayout(title = "Giriş Yap") {
        EmailField(viewModel.email, emailCheck, viewModel::onEmailChange)
        Spacer(Modifier.height(4.dp))
        PasswordField(viewModel.password, viewModel::onPasswordChange)
        Spacer(Modifier.height(4.dp))

        TextButton(
            onClick = viewModel::sendPasswordReset,
            enabled = passwordResetState !is PasswordResetUiState.Loading,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Şifrenizi mi unuttunuz?", style = MaterialTheme.typography.bodySmall)
        }

        when (val reset = passwordResetState) {
            is PasswordResetUiState.Success -> Text(
                text = reset.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            is PasswordResetUiState.Error -> Text(
                text = reset.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            else -> Unit
        }

        AuthErrorText(uiState)

        AuthSubmitButton(
            label = "Giriş Yap",
            loading = loading,
            enabled = emailCheck == EmailCheck.Valid && viewModel.password.isNotBlank(),
            onClick = viewModel::login,
        )
        Spacer(Modifier.height(12.dp))
        GoogleSignInButton("Google ile Giriş Yap", enabled = !loading, onResult = viewModel::loginWithGoogle)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToRegister) { Text("Hesabın yok mu? Kayıt ol") }
    }
}
