package com.eayd.arkive.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** Ekrandan gerçekten çıkılınca [onLeave]'i çağırır; ekran döndürülünce çağırmaz. */
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
