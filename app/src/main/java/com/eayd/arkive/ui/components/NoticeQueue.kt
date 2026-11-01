package com.eayd.arkive.ui.components

/**
 * Ekranda gösterilen kısa bildirim. [id] her gösterim için farklıdır (aynı metin yeniden gelince süre baştan başlasın
 * diye). [kind] simgeyi ve rengi belirler.
 */
class Notice(val id: Long, val text: String, val kind: NoticeKind)

/**
 * Kısa bildirimlerin sırası ([NoticeHostState] kullanır). Hiçbir Android/Compose sınıfı kullanmaz; bu yüzden tek başına
 * test edilebilir (bkz. NoticeQueueTest). İlk sıradaki bildirim [current]'tır; o kapanınca sıradaki gelir.
 */
class NoticeQueue {
    private val items = ArrayDeque<Notice>()
    private var nextId = 0L

    /** Şu an gösterilecek bildirim; yoksa null. */
    val current: Notice? get() = items.firstOrNull()

    val size: Int get() = items.size

    /**
     * Bildirim ekler. [kind] verilmezse metinden çıkarılır ([NoticeKind.classify]). Boş metin yok sayılır. Gösterilmekte olanla aynı metin yeniden gelirse yeni kart açılmaz, yalnızca
     * süresi baştan başlar; sıradaki son bildirimle aynıysa bir kez daha eklenmez (üst üste basılan düğmeler kartı çoğaltmasın).
     */
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

    /** [id] kapandı (X, dokunma ya da süre doldu). Zaten kapanmışsa bir şey yapmaz. */
    fun dismiss(id: Long) {
        items.removeAll { it.id == id }
    }

    companion object {
        /** Bildirimin ekranda kalma süresi (ms): uzun metin daha uzun kalır. */
        fun durationMillis(text: String): Int = (3_500 + text.length * 45).coerceIn(4_000, 8_000)
    }
}
