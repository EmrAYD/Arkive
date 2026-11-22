package com.eayd.arkive.i18n

import java.util.Locale

/** Uygulamanın dilleri. [nativeName] dilin kendi dilindeki adı. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    Turkish("tr", "Türkçe"),
    English("en", "English"),
    ;

    /** Tarih, sayı ve Android kaynakları için yerel ayar. */
    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

        /** Telefonun diline göre varsayılan: Türkçe telefonda Türkçe, diğerlerinde İngilizce. */
        fun fromSystem(locale: Locale): AppLanguage =
            if (locale.language.equals("tr", ignoreCase = true)) Turkish else English
    }
}

/** Seçili dil ve metin tabloları. Android'e bağlı değil; testlerde dil Türkçedir. */
object Lang {

    /** Uygulamanın dili. */
    @Volatile
    var app: AppLanguage = AppLanguage.Turkish

    /** Uygulamanın diline göre metin tablosu. */
    val strings: Strings get() = stringsFor(app)

    val locale: Locale get() = app.locale

    /** Belirli bir dilin metin tablosu. */
    fun stringsFor(language: AppLanguage): Strings = when (language) {
        AppLanguage.Turkish -> TrStrings
        AppLanguage.English -> EnStrings
    }
}

/** Uygulamanın dilindeki metinler, ör. `Txt.common.save`. */
val Txt: Strings get() = Lang.strings

/** İngilizce çoğul eki: [count] 1 ise [one], değilse [other]. */
internal fun plural(count: Any?, one: String, other: String): String {
    val n = when (count) {
        is Number -> count.toLong()
        is String -> count.trim().toLongOrNull()
        else -> null
    }
    return if (n == 1L) one else other
}
