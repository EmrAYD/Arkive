package com.eayd.arkive.word

import com.eayd.arkive.ai.OfficeCrypto
import com.eayd.arkive.ai.OfficeCryptoException
import com.eayd.arkive.ai.PasswordException
import com.eayd.arkive.ai.UnreadableDocumentException
import com.eayd.arkive.ai.WrongPasswordException
import com.eayd.arkive.i18n.Txt

/**
 * Word belgesini açar (ön izleme ve özetleme): .docx, eski .doc ve her ikisinin parolalısı. Dosya türü uzantıya/mime
 * türüne değil içeriğe bakılarak anlaşılır (zip → .docx; OLE → .doc ya da şifreli .docx).
 *
 * Ağır iş yapar; arka plan iş parçacığında çağrılmalıdır. Hatalar:
 *  - [PasswordException]: parolalı belge; parola yok (wrong = false) ya da yanlış (wrong = true),
 *  - [UnreadableDocumentException]: dosya Word belgesi değil, bozuk ya da desteklenmeyen bir yöntemle şifreli
 *    (.doc için yalnızca RC4 ve RC4 CryptoAPI desteklenir; XOR gizleme ve Word 6/95 desteklenmez).
 */
object WordLoader {

    fun load(bytes: ByteArray, password: String? = null): WordDoc {
        try {
            return when {
                isZip(bytes) -> DocxReader.read(bytes)
                OleFile.isOle(bytes) -> loadOle(bytes, password)
                else -> throw UnreadableDocumentException(Txt.docs.notWordExpected)
            }
        } catch (e: WordFormatException) {
            throw UnreadableDocumentException(e.message ?: Txt.docs.wordUnreadable, e)
        } catch (e: OutOfMemoryError) {
            throw UnreadableDocumentException(Txt.docs.tooBigForPreview)
        } catch (e: RuntimeException) {
            // Bozuk dosyada yapı okuyucularının fırlattığı her çalışma zamanı hatası (dizin taşması, geçersiz zip adı...)
            throw UnreadableDocumentException(Txt.docs.wordUnreadableMaybeCorrupt, e)
        }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun loadOle(bytes: ByteArray, password: String?): WordDoc {
        val ole = OleFile(bytes)
        if (ole.hasStream("EncryptedPackage")) {
            if (password.isNullOrEmpty()) throw PasswordException(wrong = false)
            val zip = try {
                OfficeCrypto.decrypt(bytes, password)
            } catch (e: WrongPasswordException) {
                throw PasswordException(wrong = true)
            } catch (e: OfficeCryptoException) {
                throw UnreadableDocumentException(e.message ?: Txt.docs.encryptedWordUnreadable, e)
            }
            return DocxReader.read(zip)
        }
        if (ole.hasStream("WordDocument")) return DocReader.read(ole, password)
        throw UnreadableDocumentException(Txt.docs.notWord)
    }
}
