package com.eayd.arkive.i18n

import java.lang.reflect.Method
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {

    @After
    fun resetLanguage() {
        Lang.app = AppLanguage.Turkish
    }

    /** Her grup ([AiStrings], [TrashStrings]…) ve onun metin üyeleri; (grup adı, üye adı, metot). */
    private fun members(): List<Triple<String, String, Method>> =
        Strings::class.java.methods.filter { it.name.startsWith("get") && it.parameterCount == 0 }.flatMap { group ->
            group.returnType.methods.filter { it.returnType == String::class.java }.map { Triple(group.name, it.name, it) }
        }

    private fun textOf(table: Strings, groupGetter: String, member: Method, args: Array<Any?>): String {
        val group = Strings::class.java.getMethod(groupGetter).invoke(table)
        return member.invoke(group, *args) as String
    }

    @Test
    fun `iki dilde de her metin dolu ve parametreleri metinde geciyor`() {
        val all = members()
        assertTrue("metin bulunamadı", all.size > 400)
        for ((group, name, method) in all) {
            val args = Array<Any?>(method.parameterCount) { "‹$it›" }
            for (table in listOf(TrStrings, EnStrings)) {
                val text = textOf(table, group, method, args)
                // Boş olabilen tek metin: Türkçede bağlantıdan önce bir şey yazmaz ("Kullanıcı Sözleşmesi'ni…").
                if (!(table === TrStrings && name == "getConsentPrefix")) {
                    assertTrue("$group.$name boş (${table.javaClass.simpleName})", text.isNotBlank())
                }
                args.forEach { arg -> assertTrue("$group.$name: $arg metinde yok: $text", text.contains(arg as String)) }
            }
        }
    }

    @Test
    fun `ingilizce tabloda turkce harf kalmamis`() {
        val turkish = Regex("[çğıöşüÇĞİÖŞÜ]")
        for ((group, name, method) in members()) {
            val text = textOf(EnStrings, group, method, Array(method.parameterCount) { "x" })
            assertFalse("$group.$name Türkçe harf içeriyor: $text", turkish.containsMatchIn(text))
        }
    }

    @Test
    fun `ingilizcede sayiyla cogul eki`() {
        assertEquals("1 item restored", EnStrings.trash.restored(1))
        assertEquals("3 items restored", EnStrings.trash.restored(3))
        assertEquals("0 items restored", EnStrings.trash.restored(0))
        assertEquals("3 öğe geri yüklendi", TrStrings.trash.restored(3))
        assertEquals("Trash retention period saved as 1 day", EnStrings.profile.retentionSaved(1))
    }

    @Test
    fun `telefonun diline gore varsayilan dil`() {
        assertEquals(AppLanguage.Turkish, AppLanguage.fromSystem(Locale.forLanguageTag("tr-TR")))
        assertEquals(AppLanguage.English, AppLanguage.fromSystem(Locale.forLanguageTag("en-US")))
        assertEquals(AppLanguage.English, AppLanguage.fromSystem(Locale.forLanguageTag("de-DE")))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("EN"))
        assertEquals(null, AppLanguage.fromTag("fr"))
    }

    @Test
    fun `secili dil Txt uzerinden okunur`() {
        assertEquals("Kaydet", Txt.common.save)
        Lang.app = AppLanguage.English
        assertEquals("Save", Txt.common.save)
        assertEquals(Locale.forLanguageTag("en"), Lang.locale)
        // Belirli bir dilin tablosu, seçili dilden bağımsız okunabilir.
        assertEquals("Kaydet", Lang.stringsFor(AppLanguage.Turkish).common.save)
    }

    @Test
    fun `ozet ogesinin adi neyin ozeti oldugunu soyler`() {
        assertEquals("rapor.pdf özeti", TrStrings.ai.summaryOf("rapor.pdf"))
        assertEquals("Summary of rapor.pdf", EnStrings.ai.summaryOf("rapor.pdf"))
    }
}
