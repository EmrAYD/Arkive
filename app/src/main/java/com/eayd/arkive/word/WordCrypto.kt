package com.eayd.arkive.word

import com.eayd.arkive.ai.PasswordException
import com.eayd.arkive.i18n.Txt
import java.security.MessageDigest

/** Parolalı eski .doc dosyalarının şifresini çözer (RC4 ve RC4 CryptoAPI). */
internal object WordCrypto {

    /** FIB'in şifrelenmemiş kısmının uzunluğu. */
    private const val PLAIN_FIB_BYTES = 68
    private const val BLOCK = 512

    /** Çözülmüş akışlar; normal bir belge gibi okunur. */
    class Decrypted(val wordDocument: ByteArray, val table: ByteArray, val data: ByteArray?)

    private interface Scheme {
        fun verify(password: ByteArray): Boolean
        fun key(password: ByteArray, block: Int): ByteArray
    }

    /** Akışları çözer. Parola yoksa ya da yanlışsa [PasswordException] atar. */
    fun decrypt(wd: ByteArray, table: ByteArray, data: ByteArray?, password: String?): Decrypted {
        val headerSize = OleFile.u32(wd, 0x0E)
        if (headerSize < 4 || headerSize > table.size) throw WordFormatException(Txt.docs.encryptedWordCorrupt)
        val scheme = parseScheme(table, headerSize.toInt())
        if (password.isNullOrEmpty()) throw PasswordException(wrong = false)
        val pw = password.toByteArray(Charsets.UTF_16LE)
        if (!scheme.verify(pw)) throw PasswordException(wrong = true)

        val clearWd = wd.copyOf()
        crypt(scheme, pw, clearWd, from = PLAIN_FIB_BYTES)
        val flags = OleFile.u16(clearWd, 0x0A) and 0x0100.inv() and 0x8000.inv()
        clearWd[0x0A] = flags.toByte()
        clearWd[0x0B] = (flags shr 8).toByte()
        for (i in 0x0E until 0x12) clearWd[i] = 0

        val clearTable = table.copyOf()
        crypt(scheme, pw, clearTable, from = headerSize.toInt())
        val clearData = data?.copyOf()?.also { crypt(scheme, pw, it, from = 0) }
        return Decrypted(clearWd, clearTable, clearData)
    }

    /** Akışı [from] baytından sonra yerinde çözer. */
    private fun crypt(scheme: Scheme, password: ByteArray, buf: ByteArray, from: Int) {
        var block = from / BLOCK
        var start = block * BLOCK
        while (start < buf.size) {
            val end = minOf(start + BLOCK, buf.size)
            val rc4 = Rc4(scheme.key(password, block))
            for (i in start until end) {
                val k = rc4.next()
                if (i >= from) buf[i] = (buf[i].toInt() xor k).toByte()
            }
            block++
            start = end
        }
    }

    private fun parseScheme(table: ByteArray, headerSize: Int): Scheme {
        val major = OleFile.u16(table, 0)
        val minor = OleFile.u16(table, 2)
        return when {
            major == 1 && minor == 1 -> {
                if (headerSize < 4 + 48) throw WordFormatException(Txt.docs.encryptedWordCorrupt)
                Rc4Scheme(
                    salt = table.copyOfRange(4, 20),
                    encryptedVerifier = table.copyOfRange(20, 36),
                    encryptedVerifierHash = table.copyOfRange(36, 52),
                )
            }
            major in 2..4 && minor == 2 -> parseCryptoApi(table, headerSize)
            else -> throw WordFormatException(Txt.docs.docEncryptionUnsupported)
        }
    }

    private fun parseCryptoApi(table: ByteArray, headerSize: Int): Scheme {
        val limit = minOf(headerSize, table.size)
        if (limit < 12 + 32) throw WordFormatException(Txt.docs.encryptedWordCorrupt)
        val flags = OleFile.u32(table, 4)
        val infoSize = OleFile.u32(table, 8)
        if (infoSize < 32 || 12 + infoSize > limit) throw WordFormatException(Txt.docs.encryptedWordCorrupt)
        val info = 12
        val algId = OleFile.u32(table, info + 8)
        var keyBits = OleFile.u32(table, info + 16).toInt()
        if (keyBits == 0) keyBits = 40
        if (flags and 0x04L == 0L || flags and 0x20L != 0L || (algId != 0L && algId != 0x6801L)) {
            throw WordFormatException(Txt.docs.docEncryptionUnsupported)
        }
        if (keyBits < 40 || keyBits > 128 || keyBits % 8 != 0) throw WordFormatException(Txt.docs.encryptedWordCorrupt)

        val v = (info + infoSize).toInt()
        if (v + 4 + 16 + 16 + 4 + 20 > limit) throw WordFormatException(Txt.docs.encryptedWordCorrupt)
        val saltSize = OleFile.u32(table, v)
        if (saltSize != 16L) throw WordFormatException(Txt.docs.encryptedWordCorrupt)
        val salt = table.copyOfRange(v + 4, v + 20)
        val encVerifier = table.copyOfRange(v + 20, v + 36)
        val hashSize = OleFile.u32(table, v + 36)
        if (hashSize != 20L) throw WordFormatException(Txt.docs.encryptedWordCorrupt)
        val encHash = table.copyOfRange(v + 40, v + 60)
        return CryptoApiScheme(salt, keyBits, encVerifier, encHash)
    }

    /** Office 97/2000 RC4 (MD5). */
    private class Rc4Scheme(
        private val salt: ByteArray,
        private val encryptedVerifier: ByteArray,
        private val encryptedVerifierHash: ByteArray,
    ) : Scheme {
        override fun key(password: ByteArray, block: Int): ByteArray {
            val md5 = MessageDigest.getInstance("MD5")
            val h0 = md5.digest(password).copyOf(5)
            val buffer = ByteArray((5 + salt.size) * 16)
            for (r in 0 until 16) {
                h0.copyInto(buffer, r * (5 + salt.size))
                salt.copyInto(buffer, r * (5 + salt.size) + 5)
            }
            val h1 = md5.digest(buffer).copyOf(5)
            md5.update(h1)
            md5.update(le32(block))
            return md5.digest()
        }

        override fun verify(password: ByteArray): Boolean {
            val rc4 = Rc4(key(password, 0))
            val verifier = encryptedVerifier.copyOf().also { rc4.xor(it) }
            val hash = encryptedVerifierHash.copyOf().also { rc4.xor(it) }
            return MessageDigest.isEqual(MessageDigest.getInstance("MD5").digest(verifier), hash)
        }
    }

    /** RC4 CryptoAPI (SHA-1). */
    private class CryptoApiScheme(
        private val salt: ByteArray,
        private val keyBits: Int,
        private val encryptedVerifier: ByteArray,
        private val encryptedVerifierHash: ByteArray,
    ) : Scheme {
        override fun key(password: ByteArray, block: Int): ByteArray {
            val sha1 = MessageDigest.getInstance("SHA-1")
            sha1.update(salt)
            sha1.update(password)
            val h0 = sha1.digest()
            sha1.update(h0)
            sha1.update(le32(block))
            val hfinal = sha1.digest()
            return if (keyBits == 40) hfinal.copyOf(16).also { for (i in 5 until 16) it[i] = 0 } else hfinal.copyOf(keyBits / 8)
        }

        override fun verify(password: ByteArray): Boolean {
            val rc4 = Rc4(key(password, 0))
            val verifier = encryptedVerifier.copyOf().also { rc4.xor(it) }
            val hash = encryptedVerifierHash.copyOf().also { rc4.xor(it) }
            return MessageDigest.isEqual(MessageDigest.getInstance("SHA-1").digest(verifier), hash)
        }
    }

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /** Kendi RC4 gerçeklemesi; her Android sürümünde aynı çalışsın. */
    private class Rc4(key: ByteArray) {
        private val s = IntArray(256) { it }
        private var i = 0
        private var j = 0

        init {
            var k = 0
            for (n in 0 until 256) {
                k = (k + s[n] + (key[n % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[n]; s[n] = s[k]; s[k] = t
            }
        }

        fun next(): Int {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val t = s[i]; s[i] = s[j]; s[j] = t
            return s[(s[i] + s[j]) and 0xFF]
        }

        fun xor(buf: ByteArray) {
            for (n in buf.indices) buf[n] = (buf[n].toInt() xor next()).toByte()
        }
    }
}
