package com.eayd.arkive.ui.components

import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.EnStrings
import com.eayd.arkive.i18n.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** İngilizce bildirimler de doğru simge ve renkle gösterilir (Türkçe kalıplar NoticeKindTest'te). */
class NoticeKindEnglishTest {

    @Before
    fun english() {
        Lang.app = AppLanguage.English
    }

    @After
    fun reset() {
        Lang.app = AppLanguage.Turkish
    }

    @Test
    fun `hata mesajlari`() {
        assertEquals(NoticeKind.Error, NoticeKind.classify(EnStrings.errors.serverUnreachable))
        assertEquals(NoticeKind.Error, NoticeKind.classify(EnStrings.ai.summarizeFailed("a.pdf", "x")))
        assertEquals(NoticeKind.Error, NoticeKind.classify(EnStrings.errors.badRequest)) // "Invalid" büyük harfle başlar
        assertEquals(NoticeKind.Error, NoticeKind.classify(EnStrings.docs.wrongPassword))
        assertEquals(NoticeKind.Error, NoticeKind.classify(EnStrings.errors.forbidden))
    }

    @Test
    fun `uyari mesajlari`() {
        assertEquals(NoticeKind.Warning, NoticeKind.classify(EnStrings.ai.stopped("a.pdf")))
        assertEquals(NoticeKind.Warning, NoticeKind.classify(EnStrings.errors.storagePermissionNeeded))
        assertEquals(NoticeKind.Warning, NoticeKind.classify(EnStrings.preview.unsupportedType))
        assertEquals(NoticeKind.Warning, NoticeKind.classify(EnStrings.ai.notUploadedYet))
    }

    @Test
    fun `basari ve bilgi mesajlari`() {
        assertEquals(NoticeKind.Success, NoticeKind.classify(EnStrings.trash.restored(2)))
        assertEquals(NoticeKind.Success, NoticeKind.classify(EnStrings.browser.movedTo("“a”", "Docs")))
        assertEquals(NoticeKind.Success, NoticeKind.classify(EnStrings.profile.passwordChanged))
        assertEquals(NoticeKind.Success, NoticeKind.classify(EnStrings.ai.summaryCopied))
        assertEquals(NoticeKind.Info, NoticeKind.classify(EnStrings.files.downloading("a.pdf")))
        assertEquals(NoticeKind.Info, NoticeKind.classify(EnStrings.ai.openingSummary))
    }
}
