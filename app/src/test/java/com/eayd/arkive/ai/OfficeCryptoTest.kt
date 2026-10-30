package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Başka yazılımların ürettiği şifreli dosyalarla sınanır (parola hepsinde "parola123"; içinde "Gizli belge" yazan iki paragraf):
 *  - src/test/resources/enc-standard.docx: LibreOffice'in yazdığı "Standard" şifreleme (AES-ECB + SHA-1),
 *  - src/test/resources/enc-agile.docx: msoffcrypto-tool'un yazdığı "Agile" şifreleme (AES-256-CBC + SHA-512).
 */
class OfficeCryptoTest {

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    private fun documentXml(zip: ByteArray): String {
        java.util.zip.ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                if (entry.name == "word/document.xml") return String(z.readBytes(), Charsets.UTF_8)
            }
        }
        fail("document.xml yok")
        return ""
    }

    @Test fun standardEncryptionIsDecrypted() {
        val plain = OfficeCrypto.decrypt(resource("enc-standard.docx"), "parola123")
        assertTrue(String(plain, Charsets.ISO_8859_1).startsWith("PK")) // zip
        assertTrue(documentXml(plain).contains("Gizli belge"))
    }

    @Test fun agileEncryptionIsDecrypted() {
        val plain = OfficeCrypto.decrypt(resource("enc-agile.docx"), "parola123")
        assertTrue(String(plain, Charsets.ISO_8859_1).startsWith("PK"))
        assertTrue(documentXml(plain).contains("Gizli belge"))
    }

    @Test fun turkishCharactersSurvive() {
        for (name in listOf("enc-standard.docx", "enc-agile.docx")) {
            val xml = documentXml(OfficeCrypto.decrypt(resource(name), "parola123"))
            assertTrue(name, xml.contains("ğüşıöç"))
        }
    }

    @Test fun wrongPasswordIsRejected() {
        for (name in listOf("enc-standard.docx", "enc-agile.docx")) {
            try {
                OfficeCrypto.decrypt(resource(name), "yanlis")
                fail("$name: yanlış parola kabul edildi")
            } catch (e: WrongPasswordException) {
                assertEquals("Parola yanlış.", e.message)
            }
        }
    }

    @Test fun emptyPasswordIsRejected() {
        for (name in listOf("enc-standard.docx", "enc-agile.docx")) {
            try {
                OfficeCrypto.decrypt(resource(name), "")
                fail("$name: boş parola kabul edildi")
            } catch (e: WrongPasswordException) {
            }
        }
    }

    @Test fun garbageIsReportedAsUnreadableNotCrash() {
        val garbage = AiPolicyBytes.ole + ByteArray(2000)
        try {
            OfficeCrypto.decrypt(garbage, "x")
            fail("bozuk dosya çözüldü")
        } catch (e: OfficeCryptoException) {
        }
    }

    @Test fun truncatedFileIsReportedAsUnreadable() {
        val data = resource("enc-standard.docx")
        try {
            OfficeCrypto.decrypt(data.copyOf(data.size / 2), "parola123")
            fail("yarım dosya çözüldü")
        } catch (e: OfficeCryptoException) {
        } catch (e: WrongPasswordException) {
            fail("yarım dosya için 'parola yanlış' denmemeli")
        }
    }

    private object AiPolicyBytes {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    }
}
