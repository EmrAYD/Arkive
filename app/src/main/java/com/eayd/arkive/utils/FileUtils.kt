package com.eayd.arkive.utils

import java.io.File

/**
 * Metni önce geçici dosyaya yazıp yerine taşır: yazarken uygulama kapanırsa hedef dosya yarım kalmaz.
 * Geçici dosya hedefin yanında `<ad>.tmp` adıyla durur.
 */
fun File.writeTextAtomically(text: String) {
    val temp = File(parentFile, "$name.tmp")
    temp.writeText(text)
    if (!temp.renameTo(this)) {
        delete()
        temp.renameTo(this)
    }
}
