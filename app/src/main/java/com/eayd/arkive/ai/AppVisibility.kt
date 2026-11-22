package com.eayd.arkive.ai

/** Uygulamanın ekranda olup olmadığı. MainActivity günceller. */
object AppVisibility {
    @Volatile
    var visible: Boolean = false
}
