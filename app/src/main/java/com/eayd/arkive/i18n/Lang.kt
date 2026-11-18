package com.eayd.arkive.i18n

import java.util.Locale

/** Uygulamanın desteklediği diller. [nativeName]: dilin kendi dilindeki adı (seçim listesinde böyle gösterilir). */
enum class AppLanguage(val tag: String, val nativeName: String) {
    Turkish("tr", "Türkçe"),
    English("en", "English"),
    ;

    /** Biçimlendirme (tarih, sayı) ve Android kaynakları için yerel ayar. */
    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

        /** Kullanıcı henüz seçmemişse telefonun dili: Türkçe telefonda Türkçe, diğer bütün dillerde İngilizce. */
        fun fromSystem(locale: Locale): AppLanguage =
            if (locale.language.equals("tr", ignoreCase = true)) Turkish else English
    }
}

/**
 * Seçili dil ve metin tabloları. Android'e bağlı değildir: belge okuyucular, özetleyici gibi saf Kotlin kodu da metinlerini
 * buradan alır ve birim testlerinde çalışır (testlerde dil Türkçedir).
 *
 * [app] ayarlardan ([com.eayd.arkive.data.AppSettings]) yüklenir. Dil değişince Activity yeniden kurulur; ekranlar metinleri
 * yeniden okur. ViewModel'lerde daha önce üretilmiş bir mesaj (ör. açık bir hata) bir sonraki güncellemeye kadar eski dilde
 * kalabilir.
 */
object Lang {

    /** Uygulamanın (ekranlar, bildirimler, hata mesajları) dili. */
    @Volatile
    var app: AppLanguage = AppLanguage.Turkish

    /** Uygulamanın diline göre metin tablosu. */
    val strings: Strings get() = stringsFor(app)

    /** Tarih ve sayı biçimleri için yerel ayar (uygulamanın dili). */
    val locale: Locale get() = app.locale

    /** Belirli bir dilin metin tablosu; ör. özetin içeriği uygulamanın değil özet dilinin tablosundan yazılır. */
    fun stringsFor(language: AppLanguage): Strings = when (language) {
        AppLanguage.Turkish -> TrStrings
        AppLanguage.English -> EnStrings
    }
}

/** Uygulamanın dilindeki metinler: `Txt.common.save`, `Txt.trash.restored(3)`. */
val Txt: Strings get() = Lang.strings

/**
 * İngilizce çoğul eki için: [count] 1 ise [one], değilse [other] ("1 file", "3 files"). Türkçe tabloda sayıdan sonra ad
 * tekil kaldığı için kullanılmaz.
 */
internal fun plural(count: Any?, one: String, other: String): String {
    val n = when (count) {
        is Number -> count.toLong()
        is String -> count.trim().toLongOrNull()
        else -> null
    }
    return if (n == 1L) one else other
}
