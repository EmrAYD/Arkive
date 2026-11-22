package com.eayd.arkive.utils

import com.eayd.arkive.i18n.Lang

/** Bayt sayısını okunur yazar, ör. 1536 için "1,5 KB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val text = if (value % 1.0 == 0.0) String.format(Lang.locale, "%.0f", value) else String.format(Lang.locale, "%.1f", value)
    return "$text ${units[unit]}"
}
