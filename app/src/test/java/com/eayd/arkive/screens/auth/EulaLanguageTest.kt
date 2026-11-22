package com.eayd.arkive.screens.auth

import com.eayd.arkive.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sözleşmenin iki dili aynı yapıda mı. */
class EulaLanguageTest {

    @Test
    fun `sozlesmenin iki dili ayni yapida`() {
        val tr = EulaContent.forLanguage(AppLanguage.Turkish).blocks
        val en = EulaContent.forLanguage(AppLanguage.English).blocks
        assertEquals(tr.size, en.size)
        tr.zip(en).forEach { (a, b) -> assertEquals(a::class, b::class) }
        assertEquals(7, en.count { it is EulaBlock.Heading })
    }
}
