package com.eayd.arkive.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PDF'i Android'in PdfRenderer'ıyla açar ve sayfaları çizer. PdfRenderer aynı anda tek sayfa açabildiği için erişim
 * kilitlidir.
 */
class PdfDoc private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Sayfaların genişlik ve yükseklikleri. */
    val pageSizes: List<Pair<Int, Int>>,
) {
    private val lock = Mutex()
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    /** Sayfayı [widthPx] genişliğinde çizer; belge kapandıysa null. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) return@withLock null
            val (w, h) = pageSizes[index]
            val heightPx = (widthPx.toFloat() * h / w).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val page = renderer.openPage(index)
            try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } finally {
                page.close()
            }
            bitmap
        }
    }

    /** Süren çizim bitince belgeyi kapatır. */
    fun closeAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
            }
        }
    }

    companion object {
        /** Dosyayı açar. Şifreli PDF'te [SecurityException] atar. */
        suspend fun open(file: File): PdfDoc = withContext(Dispatchers.IO) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                val sizes = List(renderer.pageCount) { i ->
                    val page = renderer.openPage(i)
                    try { page.width to page.height } finally { page.close() }
                }
                PdfDoc(descriptor, renderer, sizes)
            } catch (e: Throwable) {
                runCatching { descriptor.close() }
                throw e
            }
        }
    }
}
