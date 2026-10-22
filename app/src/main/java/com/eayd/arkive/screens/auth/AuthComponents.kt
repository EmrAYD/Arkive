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
import com.eayd.arkive.ui.theme.arkiveColors
import com.eayd.arkive.ui.theme.arkiveGradientBackground
import com.eayd.arkive.utils.getGoogleIdToken
import com.eayd.arkive.viewmodel.AuthUiState
import com.eayd.arkive.viewmodel.EmailCheck
import kotlinx.coroutines.launch

// Giriş ve kayıt ekranlarının ortak parçaları.

/** Gradyan arka plan, logo, karşılama metni ve [title] başlığı; altına formun içeriği gelir. */
@Composable
fun AuthScreenLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
    // İçerik kısa ekranlarda ve klavye açıkken kaydırılabilir; yeterli yer varsa eskisi gibi ortalanır.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .arkiveGradientBackground() // status bar / nav bar arkasına kadar uzanır
            .safeDrawingPadding(),      // içerik sistem çubuklarının ve klavyenin altında kalmaz
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
                    contentDescription = "Arkive Logosu",
                    modifier = Modifier.size(192.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.height(12.dp))
                Text("Arkive'e hoşgeldiniz!", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
                Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                content()
            }
        }
    }
}

/** Giriş başarılı olunca [onSuccess] bir kez çağrılır; ardından durum sıfırlanır (geri dönülünce tekrar tetiklenmesin). */
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
        label = { Text("E-posta") },
        isError = check == EmailCheck.Invalid,
        supportingText = { if (check == EmailCheck.Invalid) Text("Geçerli bir e-posta adresi giriniz.") },
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
        label = { Text("Şifre") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) "Şifreyi Gizle" else "Şifreyi Göster",
                )
            }
        },
    )
}

/** Giriş/kayıt hatası varsa kırmızı mesaj. */
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

/** Ana düğme; işlem sürerken yazı yerine dönen gösterge çıkar. */
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

/**
 * "Google ile …" düğmesi. Google hesap penceresi ekranın bağlamıyla açılır; sonuç ([idToken], iptal/hata ise null)
 * ViewModel'e verilir.
 */
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
            contentDescription = "Google Logosu",
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

/** Kayıt şifresinin tek bir kural satırı: karşılanıyorsa yeşil tik, değilse kırmızı çarpı. */
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
