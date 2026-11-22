package com.eayd.arkive.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eayd.arkive.R
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Lang
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.ui.theme.arkiveColors
import com.eayd.arkive.ui.theme.arkiveGradientBackground
import com.eayd.arkive.utils.getGoogleIdToken
import com.eayd.arkive.viewmodel.AuthUiState
import com.eayd.arkive.viewmodel.EmailCheck
import kotlinx.coroutines.launch

/** Giriş ve kayıt ekranlarının ortak düzeni: arka plan, logo ve başlık. */
@Composable
fun AuthScreenLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .arkiveGradientBackground()
            .safeDrawingPadding(),
    ) {
        val minHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = Txt.auth.logoA11y,
                    modifier = Modifier.size(192.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.height(12.dp))
                Text(Txt.auth.welcome, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
                Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                content()
            }
        }
        LanguageSwitch(Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

/** Türkçe ve İngilizce arasında geçiş düğmesi. */
@Composable
private fun LanguageSwitch(modifier: Modifier = Modifier) {
    val other = if (Lang.app == AppLanguage.Turkish) AppLanguage.English else AppLanguage.Turkish
    TextButton(onClick = { AppSettings.setAppLanguage(other) }, modifier = modifier) {
        Icon(Icons.Filled.Language, contentDescription = Txt.language.changeA11y, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(other.nativeName)
    }
}

/** Giriş başarılı olunca [onSuccess]'i bir kez çağırır. */
@Composable
fun NavigateOnSuccess(uiState: AuthUiState, onSuccess: () -> Unit, onConsumed: () -> Unit) {
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onSuccess()
            onConsumed()
        }
    }
}

@Composable
fun EmailField(value: String, check: EmailCheck, onValueChange: (String) -> Unit) {
    val valid = check == EmailCheck.Valid
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(Txt.auth.email) },
        isError = check == EmailCheck.Invalid,
        supportingText = { if (check == EmailCheck.Invalid) Text(Txt.auth.invalidEmail) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (valid) MaterialTheme.arkiveColors.success else MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = if (valid) MaterialTheme.arkiveColors.success else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(Txt.auth.password) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) Txt.auth.hidePasswordTitle else Txt.auth.showPasswordTitle,
                )
            }
        },
    )
}

/** Varsa giriş ya da kayıt hatası. */
@Composable
fun AuthErrorText(uiState: AuthUiState) {
    if (uiState is AuthUiState.Error) {
        Text(
            text = uiState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

/** Ana düğme; işlem sürerken yükleme göstergesi çıkar. */
@Composable
fun AuthSubmitButton(label: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = !loading && enabled) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(label)
        }
    }
}

/** "Google ile" düğmesi. Sonuç token, iptal ya da hata olursa null. */
@Composable
fun GoogleSignInButton(label: String, enabled: Boolean, onResult: (idToken: String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    OutlinedButton(
        onClick = { scope.launch { onResult(getGoogleIdToken(context)) } },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_google),
            contentDescription = Txt.auth.googleLogoA11y,
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

/** Şifre kuralının tek satırı: sağlanıyorsa yeşil tik, değilse kırmızı çarpı. */
@Composable
fun PasswordRuleItem(text: String, isMet: Boolean) {
    val color = if (isMet) MaterialTheme.arkiveColors.success else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isMet) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
