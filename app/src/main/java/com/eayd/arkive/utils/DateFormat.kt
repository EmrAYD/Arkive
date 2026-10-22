package com.eayd.arkive.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

// Sunucunun tarih biçimi: "2026-10-12T09:15:30.123456Z", "...+00:00" ya da saat dilimsiz "2026-10-12T09:15:30".
private val isoDateTime = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}(?::?\d{2})?)?""")

/**
 * Sunucudan gelen tarihi, telefonun saat diliminde "12 Ekim 2026, 14:35" biçiminde yazar.
 * (java.time yerine Calendar kullanılır: java.time eski Android sürümlerinde yoktur.)
 * Saat dilimi belirtilmemişse UTC kabul edilir (backend tarihleri UTC saklar). Okunamazsa metin olduğu gibi döner.
 */
fun formatServerDateTime(iso: String): String {
    val match = isoDateTime.matchEntire(iso.trim()) ?: return iso
    val (year, month, day, hour, minute, second, zone) = match.destructured

    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
    }
    var millis = calendar.timeInMillis

    // "+03:00" gibi bir kayma varsa UTC'ye çevir (ör. 12:00+03:00 = 09:00 UTC)
    if (zone.isNotEmpty() && zone != "Z") {
        val sign = if (zone[0] == '-') -1 else 1
        val digits = zone.substring(1).replace(":", "")
        val offsetMinutes = digits.take(2).toInt() * 60 + digits.drop(2).ifEmpty { "0" }.toInt()
        millis -= sign * offsetMinutes * 60_000L
    }

    return SimpleDateFormat("d MMMM yyyy, HH:mm", TurkishLocale).format(Date(millis))
}
