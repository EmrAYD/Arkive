package com.eayd.arkive.ai

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Telefonda açılan belge: düz metni ve yönlendirme için gereken ölçüleri. */
class ReadDocument(val text: String, val stats: DocumentStats)

/**
 * Dosya telefonda açılamadı (bozuk, desteklenmeyen biçim…). Ağ hatası DEĞİLDİR, bu yüzden IOException değildir:
 * bulut izinliyse sunucuda denenebilir.
 */
class UnreadableDocumentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Dosya parolalı. [wrong] false ise parola henüz verilmemiştir; true ise verilen parola yanlıştır. Kullanıcıdan
 * parola istenir ([AiSummarizer], ön izleme).
 */
class PasswordException(val wrong: Boolean) : Exception(if (wrong) "Parola yanlış." else "Dosya parolalı.")

/**
 * Telefona inmiş küçük bir dosyadan (en çok [AiPolicy.LOCAL_MAX_BYTES]) metni ve ölçüleri çıkarır: kelime sayısı,
 * sayfa sayısı ve görsel sayısı. Yerel özetlemeye uygun olup olmadığına [AiPolicy] bu ölçülerle karar verir.
 */
object DocumentReader {

    // DOCX içindeki document.xml'in açılmış halinin üst sınırı; küçük bir zip'in dev bir dosyaya açılmasına (zip bombası) karşı.
    private const val MAX_XML_BYTES = 8 * 1024 * 1024

    /**
     * [password]: parolalı PDF/DOCX için kullanıcının girdiği parola (yoksa null). Parola gerekiyorsa ya da yanlışsa
     * [PasswordException] atılır; parola yalnızca bu telefonda, dosyayı açmak için kullanılır.
     */
    suspend fun read(context: Context, file: File, kind: SummarizableKind, password: String? = null): ReadDocument =
        withContext(Dispatchers.IO) {
            try {
                when (kind) {
                    SummarizableKind.Text -> readText(file)
                    SummarizableKind.Pdf -> readPdf(context, file, password)
                    SummarizableKind.Docx -> readDocx(file, password)
                }
            } catch (e: IOException) {
                throw UnreadableDocumentException("Dosya okunamadı (bozuk olabilir).", e)
            }
        }

    /** Context gerektirmeyen DOCX okuma (ön izleme için). Parola kuralları [read] ile aynıdır. */
    suspend fun readDocxFile(file: File, password: String? = null): ReadDocument = withContext(Dispatchers.IO) {
        try {
            readDocx(file, password)
        } catch (e: IOException) {
            throw UnreadableDocumentException("Dosya okunamadı (bozuk olabilir).", e)
        }
    }

    /**
     * Parolalı PDF'in parolasız bir kopyasını [out]'a yazar (Android'in PDF çizicisi parolalı dosyayı açamaz). Parola
     * yanlışsa [PasswordException]. Kopya çağıranın sorumluluğundadır: işi bitince silinmelidir.
     */
    suspend fun unlockPdf(context: Context, source: File, password: String, out: File) = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            loadPdf(source, password).use { doc ->
                doc.setAllSecurityToBeRemoved(true)
                doc.save(out)
            }
        } catch (e: IOException) {
            out.delete()
            throw UnreadableDocumentException("PDF açılamadı (bozuk olabilir).", e)
        } catch (e: OutOfMemoryError) {
            out.delete()
            throw UnreadableDocumentException("PDF parolayla açılamayacak kadar büyük.", e)
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

    /** UTF-8 olarak okunur; geçerli değilse eski Türkçe Windows kodlamasına (windows-1254) düşülür. */
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
        PDFBoxResourceLoader.init(context.applicationContext) // birden çok çağrı zararsız
        val document = try {
            loadPdf(file, password)
        } catch (e: IOException) {
            throw UnreadableDocumentException("PDF açılamadı (bozuk olabilir).", e)
        }
        document.use { doc ->
            val pages = doc.numberOfPages
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

    /**
     * DOCX bir zip'tir: metin word/document.xml içindedir, görseller word/media/ altındadır. Parolalı DOCX zip değil,
     * şifreli bir OLE kapsayıcısıdır ([OfficeCrypto]); önce parolayla çözülüp zip'e dönüştürülür.
     */
    private fun readDocx(file: File, password: String?): ReadDocument {
        var bytes = file.readBytes()
        if (AiPolicy.isEncryptedOffice(bytes)) {
            if (password.isNullOrEmpty()) throw PasswordException(wrong = false)
            bytes = try {
                OfficeCrypto.decrypt(bytes, password)
            } catch (e: WrongPasswordException) {
                throw PasswordException(wrong = true)
            } catch (e: OfficeCryptoException) {
                throw UnreadableDocumentException(e.message ?: "Şifreli Word belgesi okunamadı.", e)
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
                            if (out.size() + n > MAX_XML_BYTES) throw UnreadableDocumentException("Word belgesi çok büyük.")
                            out.write(buffer, 0, n)
                        }
                        xml = out.toString(Charsets.UTF_8.name())
                    }
                    entry.name.startsWith("word/media/") && !entry.isDirectory -> images++
                }
            }
        }
        val raw = xml ?: throw UnreadableDocumentException("Word belgesi okunamadı (.docx değil ya da bozuk).")
        val text = docxXmlToText(raw)
        return ReadDocument(text, DocumentStats(words = countWords(text), pages = null, images = images))
    }

    /** document.xml'den düz metin: paragraf sonları satır olur, sekmeler boşluk olur, etiketler atılır. */
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
