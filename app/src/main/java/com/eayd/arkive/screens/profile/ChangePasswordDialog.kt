package com.eayd.arkive.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eayd.arkive.screens.auth.PasswordRuleItem
import com.eayd.arkive.viewmodel.PasswordRules

/**
 * Şifre değiştirme penceresi: mevcut şifre, yeni şifre ve yeni şifre tekrarı. Yeni şifre kayıt ekranındakiyle aynı
 * kuralları sağlamalı (en az [PasswordRules.MIN_LENGTH] karakter, özel karakter, büyük ve küçük harf), mevcut
 * şifreden farklı olmalı ve tekrarıyla aynı olmalı. Kurallar yazarken tik/çarpı olarak gösterilir. [error]: Firebase'in verdiği hata (ör. "Mevcut şifre hatalı."), pencerede gösterilir.
 */
@Composable
internal fun ChangePasswordDialog(
    saving: Boolean,
    error: String?,
    onConfirm: (current: String, new: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }

    var newFocused by remember { mutableStateOf(false) }

    val rules = PasswordRules.of(new)
    val newError = if (new.isNotEmpty() && new == current) "Yeni şifre mevcut şifreyle aynı olamaz" else null
    val againError = if (again.isNotEmpty() && again != new) "Şifreler eşleşmiyor" else null
    val canSubmit = !saving && current.isNotEmpty() && again.isNotEmpty() &&
        rules.allMet && newError == null && againError == null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Şifreyi değiştir") },
        text = {
            // Küçük ekranda ya da klavye açıkken üç alan sığmayabilir: kaydırılabilir.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasswordInput("Mevcut şifre", current, { current = it }, enabled = !saving, error = null)
                PasswordInput(
                    label = "Yeni şifre",
                    value = new,
                    onValueChange = { new = it },
                    enabled = !saving,
                    error = newError,
                    modifier = Modifier.onFocusChanged { newFocused = it.isFocused },
                )
                // Kayıt ekranındaki gibi: alana odaklanınca ya da bir şey yazılınca kurallar görünür.
                if (newFocused || new.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        PasswordRuleItem("En az ${PasswordRules.MIN_LENGTH} karakter", rules.longEnough)
                        PasswordRuleItem("Özel karakter (örn: @, #, !)", rules.hasSpecialChar)
                        PasswordRuleItem("Büyük harf", rules.hasUpperCase)
                        PasswordRuleItem("Küçük harf", rules.hasLowerCase)
                    }
                }
                PasswordInput("Yeni şifre (tekrar)", again, { again = it }, enabled = !saving, error = againError)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = { onConfirm(current, new) }) { Text("Değiştir") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Göster/gizle düğmeli şifre alanı. */
@Composable
private fun PasswordInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) "Şifreyi gizle" else "Şifreyi göster",
                )
            }
        }
    )
}
