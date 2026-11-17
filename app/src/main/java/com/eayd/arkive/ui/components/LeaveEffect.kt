package com.eayd.arkive.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Ekrandan gerçekten çıkılınca (başka bölüme/sayfaya geçildi, geri dönüldü) [onLeave] çağrılır. Ekran döndürülünce ya da
 * tema değişince Activity yeniden kurulur ve ekran da kapanıp açılır; bu durumda çağrılmaz, böylece seçim gibi durumlar
 * döndürmede korunur. [key] değişince (aynı yerde başka bir ViewModel gösterilmeye başlandı) de eskisi için çağrılır.
 */
@Composable
fun LeaveEffect(key: Any, onLeave: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val leave by rememberUpdatedState(onLeave)
    DisposableEffect(key) {
        onDispose { if (activity?.isChangingConfigurations != true) leave() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
