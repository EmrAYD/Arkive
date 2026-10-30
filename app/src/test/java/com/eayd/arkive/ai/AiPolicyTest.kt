package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPolicyTest {

    private val mb = 1024L * 1024
    private val smallText = DocumentStats(words = 350, pages = 1, images = 0)

    @Test fun cloudModeAlwaysGoesToCloud() {
        assertEquals(Precheck.Cloud, AiPolicy.precheck(AiMode.Cloud, sizeBytes = 10_000))
        assertEquals(Precheck.Cloud, AiPolicy.precheck(AiMode.Cloud, sizeBytes = 50 * mb))
    }

    @Test fun smallFileIsInspectedForLocalAndHybrid() {
        assertEquals(Precheck.Inspect, AiPolicy.precheck(AiMode.Local, 200_000))
        assertEquals(Precheck.Inspect, AiPolicy.precheck(AiMode.Hybrid, mb)) // tam 1 MB hala uygun
    }

    @Test fun overOneMbGoesToCloudInHybridAndIsBlockedInLocal() {
        assertEquals(Precheck.Cloud, AiPolicy.precheck(AiMode.Hybrid, mb + 1))
        assertEquals(Precheck.Blocked(LocalRejection.TooBig), AiPolicy.precheck(AiMode.Local, mb + 1))
    }

    @Test fun smallTextDocumentStaysLocal() {
        assertEquals(Route.Local, AiPolicy.afterInspection(AiMode.Local, smallText))
        assertEquals(Route.Local, AiPolicy.afterInspection(AiMode.Hybrid, smallText))
    }

    @Test fun longTextDocumentStaysLocalNow() {
        // Çıkarıcı algoritma uzun metinde de hızlıdır: kelime sayısı artık yerelden vazgeçirmez (sınır dosya boyutudur).
        val longDoc = DocumentStats(words = 120_000, pages = 3, images = 0)
        assertEquals(Route.Local, AiPolicy.afterInspection(AiMode.Local, longDoc))
        assertEquals(Route.Local, AiPolicy.afterInspection(AiMode.Hybrid, longDoc))
    }

    @Test fun imageHeavyPdfGoesToCloud() {
        val manyImages = DocumentStats(words = 350, pages = 4, images = 12)
        assertTrue(AiPolicy.isImageHeavy(manyImages))
        assertEquals(Route.Cloud, AiPolicy.afterInspection(AiMode.Hybrid, manyImages))
        assertEquals(Route.Blocked(LocalRejection.ImageHeavy), AiPolicy.afterInspection(AiMode.Local, manyImages))
    }

    @Test fun scannedPdfCountsAsImageHeavy() {
        val scanned = DocumentStats(words = 10, pages = 5, images = 5)
        assertEquals(Route.Cloud, AiPolicy.afterInspection(AiMode.Hybrid, scanned))
    }

    @Test fun fewImagesInTextPdfDoNotBlockLocal() {
        assertEquals(false, AiPolicy.isImageHeavy(DocumentStats(words = 450, pages = 8, images = 2)))
        // Çok sayfa, çok görsel ama oran düşük: metin ağırlıklı.
        assertEquals(false, AiPolicy.isImageHeavy(DocumentStats(words = 450, pages = 20, images = 6)))
    }

    @Test fun noTextIsBlockedEverywhere() {
        val empty = DocumentStats(words = 0, pages = null, images = 0)
        assertEquals(Route.Blocked(LocalRejection.NoText), AiPolicy.afterInspection(AiMode.Hybrid, empty))
    }

    @Test fun encryptedDocxIsRecognisedByItsOleContainer() {
        val oleMagic = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        val encrypted = oleMagic + ByteArray(600) + "EncryptedPackage".toByteArray(Charsets.UTF_16LE) + ByteArray(100)
        val oldDoc = oleMagic + ByteArray(600) + "WordDocument".toByteArray(Charsets.UTF_16LE) // eski .doc: OLE ama şifreli değil
        val normalDocx = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(600)
        assertTrue(AiPolicy.isEncryptedOffice(encrypted))
        assertEquals(false, AiPolicy.isEncryptedOffice(oldDoc))
        assertEquals(false, AiPolicy.isEncryptedOffice(normalDocx))
        assertEquals(false, AiPolicy.isEncryptedOffice(ByteArray(0)))
    }

    @Test fun kindOfRecognisesTextPdfAndDocx() {
        assertEquals(SummarizableKind.Pdf, AiPolicy.kindOf("application/pdf", "a.bin"))
        assertEquals(SummarizableKind.Pdf, AiPolicy.kindOf(null, "rapor.PDF"))
        assertEquals(SummarizableKind.Docx, AiPolicy.kindOf("application/octet-stream", "tez.docx"))
        assertEquals(SummarizableKind.Text, AiPolicy.kindOf("text/plain", "not"))
        assertEquals(SummarizableKind.Text, AiPolicy.kindOf(null, "notlar.md"))
        assertNull(AiPolicy.kindOf("image/png", "foto.png"))
        assertNull(AiPolicy.kindOf("video/mp4", "film.mp4"))
        assertNull(AiPolicy.kindOf("application/msword", "eski.doc"))
    }
}
