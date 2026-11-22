package com.eayd.arkive.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.screens.auth.PasswordRuleItem
import com.eayd.arkive.ui.components.PasswordInput
import com.eayd.arkive.viewmodel.PasswordRules

/** Şifre değiştirme penceresi. Yeni şifre kayıttaki kurallara uymalı ve eskisinden farklı olmalı. */
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
    val newError = if (new.isNotEmpty() && new == current) Txt.profile.newPasswordSameAsCurrent else null
    val againError = if (again.isNotEmpty() && again != new) Txt.profile.passwordsDontMatch else null
    val canSubmit = !saving && current.isNotEmpty() && again.isNotEmpty() &&
        rules.allMet && newError == null && againError == null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(Txt.profile.changePassword) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasswordInput(Txt.profile.currentPassword, current, { current = it }, enabled = !saving, error = null)
                PasswordInput(
                    label = Txt.profile.newPassword,
                    value = new,
                    onValueChange = { new = it },
                    enabled = !saving,
                    error = newError,
                    modifier = Modifier.onFocusChanged { newFocused = it.isFocused },
                )
                if (newFocused || new.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        PasswordRuleItem(Txt.auth.ruleMinLengthN(PasswordRules.MIN_LENGTH), rules.longEnough)
                        PasswordRuleItem(Txt.auth.ruleSpecialChar, rules.hasSpecialChar)
                        PasswordRuleItem(Txt.auth.ruleUppercase, rules.hasUpperCase)
                        PasswordRuleItem(Txt.auth.ruleLowercase, rules.hasLowerCase)
                    }
                }
                PasswordInput(Txt.profile.newPasswordAgain, again, { again = it }, enabled = !saving, error = againError)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = { onConfirm(current, new) }) { Text(Txt.profile.change) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(Txt.common.cancel) } },
    )
}
