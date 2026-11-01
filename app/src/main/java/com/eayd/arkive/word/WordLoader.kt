package com.eayd.arkive.word

import com.eayd.arkive.ai.OfficeCrypto
import com.eayd.arkive.ai.OfficeCryptoException
import com.eayd.arkive.ai.PasswordException
import com.eayd.arkive.ai.UnreadableDocumentException
import com.eayd.arkive.ai.WrongPasswordException

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
                else -> throw UnreadableDocumentException("Bu dosya bir Word belgesi değil (.docx ya da .doc bekleniyordu).")
            }
        } catch (e: WordFormatException) {
            throw UnreadableDocumentException(e.message ?: "Word belgesi okunamadı.", e)
        } catch (e: OutOfMemoryError) {
            throw UnreadableDocumentException("Belge ön izleme için çok büyük ya da karmaşık.")
        } catch (e: RuntimeException) {
            // Bozuk dosyada yapı okuyucularının fırlattığı her çalışma zamanı hatası (dizin taşması, geçersiz zip adı...)
            throw UnreadableDocumentException("Word belgesi okunamadı (bozuk olabilir).", e)
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
                throw UnreadableDocumentException(e.message ?: "Şifreli Word belgesi okunamadı.", e)
            }
            return DocxReader.read(zip)
        }
        if (ole.hasStream("WordDocument")) return DocReader.read(ole, password)
        throw UnreadableDocumentException("Bu dosya bir Word belgesi değil.")
    }
}
