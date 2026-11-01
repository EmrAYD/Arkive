package com.eayd.arkive.ai

/**
 * Uygulamanın şu an ekranda olup olmadığı. [com.eayd.arkive.MainActivity] görününce/gizlenince günceller. Özetleme
 * bitince haberin uygulama içi mesajla mı, sistem bildirimiyle mi verileceğini belirlemek için kullanılır.
 */
object AppVisibility {
    @Volatile
    var visible: Boolean = false
}
