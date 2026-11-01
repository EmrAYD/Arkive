package com.eayd.arkive.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticeQueueTest {

    @Test
    fun `bildirimler sirayla gosterilir`() {
        val q = NoticeQueue()
        q.add("bir")
        q.add("iki")
        assertEquals("bir", q.current?.text)
        q.dismiss(q.current!!.id)
        assertEquals("iki", q.current?.text)
        q.dismiss(q.current!!.id)
        assertNull(q.current)
    }

    @Test
    fun `bos metin yok sayilir`() {
        val q = NoticeQueue()
        q.add("")
        q.add("   ")
        assertNull(q.current)
    }

    @Test
    fun `gosterilenle ayni metin yeni kart acmaz ama sureyi bastan baslatir`() {
        val q = NoticeQueue()
        q.add("Taşındı")
        val first = q.current!!
        q.add("Taşındı")
        assertEquals(1, q.size)
        assertEquals("Taşındı", q.current?.text)
        assertNotEquals("süre yeniden başlasın diye kimlik değişir", first.id, q.current!!.id)
    }

    @Test
    fun `sirandaki son bildirimle ayni metin tekrar eklenmez`() {
        val q = NoticeQueue()
        q.add("a")
        q.add("b")
        q.add("b")
        assertEquals(2, q.size)
    }

    @Test
    fun `kapanmis bildirimi tekrar kapatmak sirayi bozmaz`() {
        val q = NoticeQueue()
        q.add("a")
        q.add("b")
        val a = q.current!!
        q.dismiss(a.id)
        q.dismiss(a.id)
        assertEquals("b", q.current?.text)
        assertEquals(1, q.size)
    }

    @Test
    fun `sure metne gore uzar ve sinirlidir`() {
        assertEquals(4_000, NoticeQueue.durationMillis("kısa"))
        assertEquals(8_000, NoticeQueue.durationMillis("x".repeat(500)))
        assertEquals(true, NoticeQueue.durationMillis("x".repeat(60)) > NoticeQueue.durationMillis("x".repeat(20)))
    }

    @Test
    fun `tur verilmezse metinden cikarilir, verilirse o kullanilir`() {
        val q = NoticeQueue()
        q.add("Dosya yüklenemedi")
        assertEquals(NoticeKind.Error, q.current?.kind)
        q.dismiss(q.current!!.id)
        q.add("Dosya yüklenemedi", NoticeKind.Info)
        assertEquals(NoticeKind.Info, q.current?.kind)
    }
}
