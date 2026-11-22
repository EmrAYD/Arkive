package com.eayd.arkive.ui.components

/** Ekranda gösterilen kısa bildirim. [id] her gösterimde farklıdır. */
class Notice(val id: Long, val text: String, val kind: NoticeKind)

/** Kısa bildirimlerin sırası. */
class NoticeQueue {
    private val items = ArrayDeque<Notice>()
    private var nextId = 0L

    /** Şu an gösterilecek bildirim. */
    val current: Notice? get() = items.firstOrNull()

    val size: Int get() = items.size

    /** Bildirim ekler. Gösterilenle aynı metin gelirse yeni kart açılmaz, süresi baştan başlar. */
    fun add(text: String, kind: NoticeKind? = null) {
        if (text.isBlank()) return
        val resolved = kind ?: NoticeKind.classify(text)
        if (items.firstOrNull()?.text == text) {
            items[0] = Notice(nextId++, text, resolved)
            return
        }
        if (items.lastOrNull()?.text == text) return
        items.addLast(Notice(nextId++, text, resolved))
    }

    /** Bildirimi kapatır. */
    fun dismiss(id: Long) {
        items.removeAll { it.id == id }
    }

    companion object {
        /** Bildirimin ekranda kalma süresi; uzun metin daha uzun kalır. */
        fun durationMillis(text: String): Int = (3_500 + text.length * 45).coerceIn(4_000, 8_000)
    }
}
