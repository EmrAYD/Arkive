package com.eayd.arkive.utils

/** Bayt sayısını okunur biçime çevirir: 1536 → "1,5 KB", 104857600 → "100 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    // Tam sayıysa ondalık göstermeyelim ("100 MB"), değilse bir basamak ("7,6 MB").
    val text = if (value % 1.0 == 0.0) String.format(TurkishLocale, "%.0f", value) else String.format(TurkishLocale, "%.1f", value)
    return "$text ${units[unit]}"
}
