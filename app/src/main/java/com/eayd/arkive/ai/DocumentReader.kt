package com.eayd.arkive.ai

import android.content.Context
import com.eayd.arkive.data.AppSettings
import com.eayd.arkive.i18n.AppLanguage
import com.eayd.arkive.i18n.Txt
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.eayd.arkive.word.WordLoader
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Telefonda açılan belgenin metni ve ölçüleri. */
class ReadDocument(val text: String, val stats: DocumentStats)

/** Dosya telefonda açılamadı (bozuk ya da desteklenmiyor). Ağ hatası değildir. */
class UnreadableDocumentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Dosya parolalı. [wrong] verilen parolanın yanlış olduğunu söyler. */
class PasswordException(val wrong: Boolean) : Exception(if (wrong) Txt.docs.wrongPassword else Txt.docs.passwordProtected)

/** Küçük dosyalardan metni ve kelime, sayfa, görsel sayısını çıkarır. */
object DocumentReader {

    private const val MAX_XML_BYTES = 8 * 1024 * 1024

    /** Dosyayı okur. Parola gerekiyorsa ya da yanlışsa [PasswordException] atar. */
    suspend fun read(context: Context, file: File, kind: SummarizableKind, password: String? = null): ReadDocument =
        withContext(Dispatchers.IO) {
            try {
                when (kind) {
                    SummarizableKind.Text -> readText(file)
                    SummarizableKind.Pdf -> readPdf(context, file, password)
                    SummarizableKind.Docx -> readDocx(file, password)
                    SummarizableKind.Xlsx, SummarizableKind.Pptx -> readOffice(file, kind, password)
                }
            } catch (e: IOException) {
                throw UnreadableDocumentException(Txt.docs.unreadableMaybeCorrupt, e)
            }
        }

    /** Context gerektirmeyen Word okuma. */
    suspend fun readDocxFile(file: File, password: String? = null): ReadDocument = withContext(Dispatchers.IO) {
        try {
            readDocx(file, password)
        } catch (e: IOException) {
            throw UnreadableDocumentException(Txt.docs.unreadableMaybeCorrupt, e)
        }
    }

    /** Parolalı PDF'in parolasız kopyasını [out]'a yazar; kopyayı silmek çağıranın işidir. */
    suspend fun unlockPdf(context: Context, source: File, password: String, out: File) = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            loadPdf(source, password).use { doc ->
                doc.setAllSecurityToBeRemoved(true)
                doc.save(out)
            }
        } catch (e: IOException) {
            out.delete()
            throw UnreadableDocumentException(Txt.docs.pdfOpenFailed, e)
        } catch (e: OutOfMemoryError) {
            out.delete()
            throw UnreadableDocumentException(Txt.docs.pdfTooBigForPassword, e)
        }
    }

    private fun loadPdf(file: File, password: String?): PDDocument = try {
        PDDocument.load(file, password.orEmpty())
    } catch (e: InvalidPasswordException) {
        throw PasswordException(wrong = !password.isNullOrEmpty())
    }

    fun countWords(text: String): Int = text.split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun readText(file: File): ReadDocument {
        val bytes = file.readBytes()
        val text = decode(bytes).trim()
        return ReadDocument(text, DocumentStats(words = countWords(text), pages = null, images = 0))
    }

    /** UTF-8 değilse windows-1254 olarak okur. */
    private fun decode(bytes: ByteArray): String {
        val strictUtf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            strictUtf8.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1254"))
        }
    }

    private fun readPdf(context: Context, file: File, password: String?): ReadDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val document = try {
            loadPdf(file, password)
        } catch (e: IOException) {
            throw UnreadableDocumentException(Txt.docs.pdfOpenFailed, e)
        }
        document.use { doc ->
            val pages = doc.numberOfPages
            if (pages > AiPolicy.LOCAL_MAX_PAGES) return ReadDocument("", DocumentStats(words = 0, pages = pages, images = 0))
            val text = PDFTextStripper().getText(doc).trim()
            var images = 0
            for (page in doc.pages) {
                val resources = page.resources ?: continue
                for (name in resources.xObjectNames) {
                    if (runCatching { resources.getXObject(name) }.getOrNull() is PDImageXObject) images++
                }
            }
            return ReadDocument(text, DocumentStats(words = countWords(text), pages = pages, images = images))
        }
    }

    /** Eski .doc dosyasını okur. */
    internal fun readLegacyWord(bytes: ByteArray, password: String?): ReadDocument {
        val doc = WordLoader.load(bytes, password)
        val text = doc.summaryText()
        return ReadDocument(text, DocumentStats(words = countWords(text), pages = null, images = doc.imageCount))
    }

    /** Word belgesini okur; türü uzantıdan değil içerikten anlar. */
    private fun readDocx(file: File, password: String?): ReadDocument {
        var bytes = file.readBytes()
        if (AiPolicy.isLegacyOffice(bytes)) return readLegacyWord(bytes, password)
        if (AiPolicy.isEncryptedOffice(bytes)) {
            if (password.isNullOrEmpty()) throw PasswordException(wrong = false)
            bytes = try {
                OfficeCrypto.decrypt(bytes, password)
            } catch (e: WrongPasswordException) {
                throw PasswordException(wrong = true)
            } catch (e: OfficeCryptoException) {
                throw UnreadableDocumentException(e.message ?: Txt.docs.encryptedWordUnreadable, e)
            }
        }
        var xml: String? = null
        var images = 0
        ZipInputStream(bytes.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "word/document.xml" -> {
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            if (out.size() + n > MAX_XML_BYTES) throw UnreadableDocumentException(Txt.docs.wordTooBig)
                            out.write(buffer, 0, n)
                        }
                        xml = out.toString(Charsets.UTF_8.name())
                    }
                    entry.name.startsWith("word/media/") && !entry.isDirectory -> images++
                }
            }
        }
        val raw = xml ?: throw UnreadableDocumentException(Txt.docs.wordNotDocxOrCorrupt)
        val text = docxXmlToText(raw)
        return ReadDocument(text, DocumentStats(words = countWords(text), pages = null, images = images))
    }

    /** Excel ya da PowerPoint dosyasını okur; parolalıysa önce çözer. Eski .xls ve .ppt okunmaz. */
    private fun readOffice(file: File, kind: SummarizableKind, password: String?): ReadDocument {
        val excel = kind == SummarizableKind.Xlsx
        val unreadable = if (excel) Txt.docs.excelUnreadable else Txt.docs.powerPointUnreadable
        var bytes = file.readBytes()
        if (AiPolicy.isLegacyOffice(bytes)) throw UnreadableDocumentException(unreadable)
        if (AiPolicy.isEncryptedOffice(bytes)) {
            if (password.isNullOrEmpty()) throw PasswordException(wrong = false)
            bytes = try {
                OfficeCrypto.decrypt(bytes, password)
            } catch (e: WrongPasswordException) {
                throw PasswordException(wrong = true)
            } catch (e: OfficeCryptoException) {
                val message = if (excel) Txt.docs.encryptedExcelUnreadable else Txt.docs.encryptedPowerPointUnreadable
                throw UnreadableDocumentException(message, e)
            }
        }
        val text = try {
            if (excel) {
                OfficeText.excel(bytes)
            } else {
                OfficeText.powerPoint(bytes, english = AppSettings.summaryLanguage.value == AppLanguage.English)
            }
        } catch (e: OfficeTextException) {
            throw UnreadableDocumentException(if (e.tooBig) Txt.docs.officeTooBig else unreadable, e)
        }
        val clean = text.lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
        return ReadDocument(clean, DocumentStats(words = countWords(clean), pages = null, images = 0))
    }

    /** document.xml'den düz metin çıkarır. */
    internal fun docxXmlToText(xml: String): String = xml
        .replace("</w:p>", "\n")
        .replace(Regex("<w:tab\\s*/>"), " ")
        .replace(Regex("<w:br\\s*/>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
