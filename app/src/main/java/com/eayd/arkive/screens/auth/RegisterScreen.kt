package com.eayd.arkive.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eayd.arkive.viewmodel.AuthUiState
import com.eayd.arkive.viewmodel.AuthViewModel
import com.eayd.arkive.viewmodel.EmailCheck

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val emailCheck by viewModel.emailCheck.collectAsState()
    val loading = uiState is AuthUiState.Loading
    // Google ile kayıt yeni hesap açtıysa sözleşme onay bekliyor demektir (aşağıdaki pencere).
    val eulaPending = uiState is AuthUiState.EulaRequired
    val context = LocalContext.current
    val rules = viewModel.passwordRules

    // Şifre kutusuna tıklanınca ya da içinde yazı varsa kurallar gösterilir.
    var passwordFocused by remember { mutableStateOf(false) }

    // Kullanıcı Sözleşmesi: kutu yalnızca sözleşme sonuna kadar okunup onaylanınca işaretlenir (EulaDialog).
    var eulaAccepted by rememberSaveable { mutableStateOf(false) }
    var showEula by rememberSaveable { mutableStateOf(false) }

    NavigateOnSuccess(uiState, onRegisterSuccess, viewModel::resetState)

    AuthScreenLayout(title = "Kayıt Ol") {
        EmailField(viewModel.email, emailCheck, viewModel::onEmailChange)
        Spacer(Modifier.height(4.dp))
        PasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.onFocusChanged { passwordFocused = it.isFocused || viewModel.password.isNotEmpty() },
        )

        if (passwordFocused) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp)) {
                PasswordRuleItem("En az 8 karakter", rules.longEnough)
                PasswordRuleItem("Özel karakter (örn: @, #, !)", rules.hasSpecialChar)
                PasswordRuleItem("Büyük harf", rules.hasUpperCase)
                PasswordRuleItem("Küçük harf", rules.hasLowerCase)
            }
        }

        Spacer(Modifier.height(12.dp))
        EulaConsentRow(
            accepted = eulaAccepted,
            onOpen = { showEula = true },
            onWithdraw = { eulaAccepted = false },
        )
        Spacer(Modifier.height(12.dp))
        AuthErrorText(uiState)

        // Kayıt düğmesi sadece kurallar sağlandığında aktif olur.
        AuthSubmitButton(
            label = "Kayıt Ol",
            loading = loading,
            enabled = emailCheck == EmailCheck.Valid && rules.allMet && eulaAccepted,
            onClick = viewModel::register,
        )
        Spacer(Modifier.height(12.dp))
        // Google ile kayıt için kutuyu işaretlemek gerekmez: yeni hesap açılırsa sözleşme o anda gösterilir.
        // Kutu zaten işaretliyse sözleşme tekrar sorulmaz.
        GoogleSignInButton(
            label = "Google ile Kayıt Ol",
            enabled = !loading && !eulaPending,
            onResult = { viewModel.loginWithGoogle(it, eulaAlreadyAccepted = eulaAccepted) },
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToLogin) { Text("Zaten hesabın var mı? Giriş yap") }
    }

    if (showEula) {
        EulaDialog(
            onDismiss = { showEula = false },
            onAccept = {
                eulaAccepted = true
                showEula = false
            },
        )
    }

    if (eulaPending) {
        EulaDialog(
            onDismiss = { viewModel.declineEula(context) },
            onAccept = viewModel::acceptEula,
        )
    }
}
