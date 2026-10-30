package com.eayd.arkive.ai

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Parola yanlış. */
class WrongPasswordException : Exception("Parola yanlış.")

/** Dosya çözülemedi: şifreleme türü desteklenmiyor ya da dosya bozuk. [message] kullanıcıya gösterilebilir. */
class OfficeCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parola ile şifrelenmiş Office dosyasını (.docx) çözer. İki şifreleme türünü destekler:
 *  - "Agile" (Word 2010 ve sonrasının varsayılanı; MS-OFFCRYPTO 2.3.4.10: AES-CBC + SHA-2),
 *  - "Standard" (Word 2007 ve LibreOffice'in yazdığı; MS-OFFCRYPTO 2.3.4.5: AES-ECB + SHA-1).
 *
 * Şifreli .docx bir zip değildir: eski OLE (Compound File) kapsayıcısına sarılır ve iki akış taşır:
 *  - "EncryptionInfo": şifreleme ayarları (XML), parolayı doğrulamak için şifreli doğrulama değerleri,
 *  - "EncryptedPackage": gerçek .docx zip'inin 4096 baytlık parçalar hâlinde şifrelenmiş hâli.
 * Parolayı doğrulamak için dosyanın tamamını çözmek gerekmez: önce küçük doğrulama değeri çözülür, tutmuyorsa
 * [WrongPasswordException] atılır. Android sınıfı kullanmaz; tek başına test edilir.
 */
object OfficeCrypto {

    private const val SEGMENT = 4096
    private const val END_OF_CHAIN = 0xFFFFFFFAL // bunun üstündeki sektör numaraları özel değerlerdir

    private val BLOCK_VERIFIER_INPUT = byteArrayOf(0xfe.toByte(), 0xa7.toByte(), 0xd2.toByte(), 0x76, 0x3b, 0x4b, 0x9e.toByte(), 0x79)
    private val BLOCK_VERIFIER_VALUE = byteArrayOf(0xd7.toByte(), 0xaa.toByte(), 0x0f, 0x6d, 0x30, 0x61, 0x34, 0x4e)
    private val BLOCK_KEY_VALUE = byteArrayOf(0x14, 0x6e, 0x0b, 0xe7.toByte(), 0xab.toByte(), 0xac.toByte(), 0xd0.toByte(), 0xd6.toByte())

    /** [file]: şifreli dosyanın baytları. Dönen: şifresiz .docx (zip) baytları. */
    fun decrypt(file: ByteArray, password: String): ByteArray {
        try {
            val container = Container(file)
            val info = container.stream("EncryptionInfo") ?: throw OfficeCryptoException("Şifreleme bilgisi bulunamadı.")
            val package_ = container.stream("EncryptedPackage") ?: throw OfficeCryptoException("Şifreli içerik bulunamadı.")
            if (info.size < 8) throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
            val major = u16(info, 0)
            val minor = u16(info, 2)
            return when {
                major == 4 && minor == 4 -> {
                    val agile = Agile.parse(String(info, 8, info.size - 8, Charsets.UTF_8))
                    agile.decryptPackage(agile.unlock(password), package_)
                }
                minor == 2 && major in 2..4 -> {
                    val standard = Standard.parse(info)
                    standard.decryptPackage(standard.unlock(password), package_)
                }
                else -> throw OfficeCryptoException("Bu Word şifreleme türü desteklenmiyor.")
            }
        } catch (e: WrongPasswordException) {
            throw e
        } catch (e: OfficeCryptoException) {
            throw e
        } catch (e: Exception) {
            // Taşan indeks, geçersiz sayı… hepsi bozuk dosya demektir.
            throw OfficeCryptoException("Şifreli Word belgesi okunamadı (bozuk olabilir).", e)
        }
    }

    // --- Standard şifreleme (Word 2007, LibreOffice) -----------------------------------------------------

    private class Standard(
        val keyBits: Int,
        val salt: ByteArray,
        val encryptedVerifier: ByteArray,
        val verifierHashSize: Int,
        val encryptedVerifierHash: ByteArray,
    ) {
        /** Parolayı doğrular ve AES anahtarını verir. Parola yanlışsa [WrongPasswordException]. */
        fun unlock(password: String): ByteArray {
            var h = hash("SHA-1", salt + password.toByteArray(Charsets.UTF_16LE))
            for (i in 0 until 50_000) h = hash("SHA-1", intLe(i) + h)
            val final = hash("SHA-1", h + intLe(0))
            // Anahtar genişletme: iki sabit dolgu ile karıştırılıp SHA-1'lenir, ikisi yan yana konur (MS-OFFCRYPTO 2.3.4.7).
            val x1 = hash("SHA-1", ByteArray(64) { (0x36 xor (final.getOrElse(it) { 0 }.toInt() and 0xFF)).toByte() })
            val x2 = hash("SHA-1", ByteArray(64) { (0x5C xor (final.getOrElse(it) { 0 }.toInt() and 0xFF)).toByte() })
            val key = (x1 + x2).copyOf(keyBits / 8)

            val verifier = aesEcb(key, encryptedVerifier)
            val verifierHash = aesEcb(key, encryptedVerifierHash).copyOf(verifierHashSize)
            if (!MessageDigest.isEqual(hash("SHA-1", verifier), verifierHash)) throw WrongPasswordException()
            return key
        }

        fun decryptPackage(key: ByteArray, stream: ByteArray): ByteArray {
            if (stream.size < 8) throw OfficeCryptoException("Şifreli içerik bozuk.")
            val size = u64(stream, 0)
            val body = (stream.size - 8) / 16 * 16
            if (size < 0 || size > body) throw OfficeCryptoException("Şifreli içerik bozuk.")
            return aesEcb(key, stream.copyOfRange(8, 8 + body)).copyOf(size.toInt())
        }

        companion object {
            fun parse(info: ByteArray): Standard {
                val headerSize = u32(info, 8)
                if (headerSize < 32 || 12 + headerSize + 4 > info.size) throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
                val header = 12
                val algorithm = u32(info, header + 8)
                val keyBits = u32(info, header + 16).toInt()
                // 0x660E/F, 0x6610: AES-128/192/256. Başka algoritmalar (RC4…) desteklenmez.
                if (algorithm !in 0x660EL..0x6610L || keyBits !in listOf(128, 192, 256)) {
                    throw OfficeCryptoException("Bu Word şifreleme türü desteklenmiyor.")
                }
                var o = header + headerSize.toInt()
                val saltSize = u32(info, o).toInt()
                if (saltSize != 16 || o + 4 + 16 + 16 + 4 + 32 > info.size) throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
                o += 4
                val salt = info.copyOfRange(o, o + 16)
                val encryptedVerifier = info.copyOfRange(o + 16, o + 32)
                val hashSize = u32(info, o + 32).toInt()
                if (hashSize !in 1..32) throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
                return Standard(keyBits, salt, encryptedVerifier, hashSize, info.copyOfRange(o + 36, o + 68))
            }
        }
    }

    private fun aesEcb(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    // --- Agile şifreleme -------------------------------------------------------------------------------

    private class Agile(
        val keyDataSalt: ByteArray,
        val keyDataBlock: Int,
        val keyDataBits: Int,
        val keyDataHash: String,
        val spinCount: Int,
        val encSalt: ByteArray,
        val encBlock: Int,
        val encBits: Int,
        val encHash: String,
        val verifierInput: ByteArray,
        val verifierValue: ByteArray,
        val keyValue: ByteArray,
    ) {
        /** Parolayı doğrular ve dosya şifresini çözen asıl anahtarı verir. Parola yanlışsa [WrongPasswordException]. */
        fun unlock(password: String): ByteArray {
            var h = hash(encHash, encSalt + password.toByteArray(Charsets.UTF_16LE))
            for (i in 0 until spinCount) h = hash(encHash, intLe(i) + h)

            fun derived(blockKey: ByteArray): ByteArray = fit(hash(encHash, h + blockKey), encBits / 8)
            fun aes(blockKey: ByteArray, data: ByteArray) = aesCbc(derived(blockKey), iv(encSalt, encBlock), data)

            val expected = hash(encHash, aes(BLOCK_VERIFIER_INPUT, verifierInput).copyOf(encSalt.size))
            val actual = aes(BLOCK_VERIFIER_VALUE, verifierValue).copyOf(expected.size)
            if (!MessageDigest.isEqual(expected, actual)) throw WrongPasswordException()
            return aes(BLOCK_KEY_VALUE, keyValue).copyOf(keyDataBits / 8)
        }

        fun decryptPackage(key: ByteArray, stream: ByteArray): ByteArray {
            if (stream.size < 8) throw OfficeCryptoException("Şifreli içerik bozuk.")
            val size = u64(stream, 0)
            val body = stream.size - 8
            if (size < 0 || size > body) throw OfficeCryptoException("Şifreli içerik bozuk.")
            val out = ByteArray(size.toInt())
            var written = 0
            var segment = 0
            var pos = 8
            while (written < out.size) {
                val len = minOf(SEGMENT, stream.size - pos) / 16 * 16 // AES blokları 16'şar bayttır
                if (len <= 0) throw OfficeCryptoException("Şifreli içerik bozuk.")
                val iv = iv(hash(keyDataHash, keyDataSalt + intLe(segment)), keyDataBlock)
                val plain = aesCbc(key, iv, stream.copyOfRange(pos, pos + len))
                val n = minOf(plain.size, out.size - written)
                System.arraycopy(plain, 0, out, written, n)
                written += n
                pos += len
                segment++
            }
            return out
        }

        companion object {
            fun parse(xml: String): Agile {
                val keyData = tag(xml, "keyData") ?: throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
                val enc = tag(xml, "encryptedKey") ?: throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
                for (t in listOf(keyData, enc)) {
                    if (t["cipherAlgorithm"] != "AES" || t["cipherChaining"] != "ChainingModeCBC") {
                        throw OfficeCryptoException("Bu Word şifreleme türü desteklenmiyor.")
                    }
                }
                fun bytes(m: Map<String, String>, name: String) =
                    base64(m[name] ?: throw OfficeCryptoException("Şifreleme bilgisi bozuk."))
                fun int(m: Map<String, String>, name: String) =
                    m[name]?.toIntOrNull() ?: throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
                val spin = int(enc, "spinCount")
                if (spin < 0 || spin > 10_000_000) throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
                return Agile(
                    keyDataSalt = bytes(keyData, "saltValue"),
                    keyDataBlock = int(keyData, "blockSize"),
                    keyDataBits = int(keyData, "keyBits"),
                    keyDataHash = hashName(keyData["hashAlgorithm"]),
                    spinCount = spin,
                    encSalt = bytes(enc, "saltValue"),
                    encBlock = int(enc, "blockSize"),
                    encBits = int(enc, "keyBits"),
                    encHash = hashName(enc["hashAlgorithm"]),
                    verifierInput = bytes(enc, "encryptedVerifierHashInput"),
                    verifierValue = bytes(enc, "encryptedVerifierHashValue"),
                    keyValue = bytes(enc, "encryptedKeyValue"),
                )
            }

            /** `<keyData .../>` gibi bir etiketin özelliklerini verir (ad alanı öneki ne olursa olsun). */
            private fun tag(xml: String, name: String): Map<String, String>? {
                val m = Regex("<(?:\\w+:)?$name\\b([^>]*)>").find(xml) ?: return null
                return Regex("([\\w:]+)=\"([^\"]*)\"").findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            }

            private fun hashName(name: String?): String = when (name) {
                "SHA1" -> "SHA-1"
                "SHA256" -> "SHA-256"
                "SHA384" -> "SHA-384"
                "SHA512" -> "SHA-512"
                else -> throw OfficeCryptoException("Bu Word şifreleme türü desteklenmiyor.")
            }
        }
    }

    /** Base64 çözücü (java.util.Base64 yalnızca Android 8+ ve bu dosya Android sınıfı kullanmadan test edilsin diye kendimiz yazdık). */
    private fun base64(text: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=' || c.isWhitespace()) continue
            val v = alphabet.indexOf(c)
            if (v < 0) throw OfficeCryptoException("Şifreleme bilgisi bozuk.")
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private fun hash(algorithm: String, data: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(data)

    /** Anahtarı [size] bayta getirir: uzunsa keser, kısaysa 0x36 ile doldurur (MS-OFFCRYPTO). */
    private fun fit(data: ByteArray, size: Int): ByteArray =
        if (data.size >= size) data.copyOf(size) else data + ByteArray(size - data.size) { 0x36 }

    private fun iv(salt: ByteArray, blockSize: Int): ByteArray = fit(salt, blockSize)

    private fun aesCbc(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun intLe(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, o: Int): Long = (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)

    private fun u64(b: ByteArray, o: Int): Long = u32(b, o) or (u32(b, o + 4) shl 32)

    // --- OLE (Compound File) kapsayıcısı ---------------------------------------------------------------

    /** Yalnızca adıyla bir akışı okumaya yetecek kadar OLE okuyucusu (MS-CFB). */
    private class Container(private val data: ByteArray) {
        private val sectorSize: Int
        private val miniSectorSize: Int
        private val miniCutoff: Long
        private val fat: LongArray
        private val miniFat: LongArray
        private val directory: ByteArray
        private val rootStart: Long
        private val rootSize: Long

        init {
            if (data.size < 512) throw OfficeCryptoException("Dosya bozuk.")
            sectorSize = 1 shl u16(data, 0x1E)
            miniSectorSize = 1 shl u16(data, 0x20)
            if (sectorSize != 512 && sectorSize != 4096) throw OfficeCryptoException("Dosya bozuk.")
            miniCutoff = u32(data, 0x38)

            // FAT sektörlerinin listesi: başlıktaki 109 girdi + (varsa) DIFAT zinciri.
            val fatSectors = ArrayList<Long>()
            for (i in 0 until 109) {
                val s = u32(data, 0x4C + 4 * i)
                if (s < END_OF_CHAIN) fatSectors.add(s)
            }
            var difat = u32(data, 0x44)
            var guard = 0
            while (difat < END_OF_CHAIN && guard++ < 10_000) {
                val base = offset(difat)
                for (i in 0 until sectorSize / 4 - 1) {
                    val s = u32(data, base + 4 * i)
                    if (s < END_OF_CHAIN) fatSectors.add(s)
                }
                difat = u32(data, base + sectorSize - 4)
            }
            val perSector = sectorSize / 4
            fat = LongArray(fatSectors.size * perSector)
            fatSectors.forEachIndexed { n, s ->
                val base = offset(s)
                for (i in 0 until perSector) fat[n * perSector + i] = u32(data, base + 4 * i)
            }

            directory = readChain(u32(data, 0x30), Long.MAX_VALUE)
            val root = entries().firstOrNull { it.type == 5 } ?: throw OfficeCryptoException("Dosya bozuk.")
            rootStart = root.start
            rootSize = root.size

            val miniFatBytes = readChain(u32(data, 0x3C), Long.MAX_VALUE)
            miniFat = LongArray(miniFatBytes.size / 4) { u32(miniFatBytes, it * 4) }
        }

        private class Entry(val name: String, val type: Int, val start: Long, val size: Long)

        private fun entries(): List<Entry> = (0 until directory.size / 128).map { i ->
            val o = i * 128
            val nameLen = u16(directory, o + 0x40).coerceIn(0, 64)
            Entry(
                name = String(directory, o, maxOf(nameLen - 2, 0), Charsets.UTF_16LE),
                type = directory[o + 0x42].toInt(),
                start = u32(directory, o + 0x74),
                size = u32(directory, o + 0x78),
            )
        }

        private fun offset(sector: Long): Int {
            val o = (sector + 1) * sectorSize
            if (o < 0 || o + sectorSize > data.size) throw OfficeCryptoException("Dosya bozuk.")
            return o.toInt()
        }

        /** FAT zincirini izleyerek sektörleri birleştirir; en çok [limit] bayt verir. */
        private fun readChain(start: Long, limit: Long): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var s = start
            var guard = 0
            while (s < END_OF_CHAIN && out.size() < limit) {
                if (guard++ > fat.size + 1 || s >= fat.size) throw OfficeCryptoException("Dosya bozuk.")
                out.write(data, offset(s), sectorSize)
                s = fat[s.toInt()]
            }
            val bytes = out.toByteArray()
            return if (bytes.size > limit) bytes.copyOf(limit.toInt()) else bytes
        }

        private fun readMiniChain(start: Long, size: Long): ByteArray {
            val mini = readChain(rootStart, rootSize)
            val out = java.io.ByteArrayOutputStream()
            var s = start
            var guard = 0
            while (s < END_OF_CHAIN && out.size() < size) {
                if (guard++ > miniFat.size + 1 || s >= miniFat.size) throw OfficeCryptoException("Dosya bozuk.")
                val o = s.toInt() * miniSectorSize
                if (o + miniSectorSize > mini.size) throw OfficeCryptoException("Dosya bozuk.")
                out.write(mini, o, miniSectorSize)
                s = miniFat[s.toInt()]
            }
            return out.toByteArray().copyOf(size.toInt())
        }

        fun stream(name: String): ByteArray? {
            val entry = entries().firstOrNull { it.type == 2 && it.name == name } ?: return null
            return if (entry.size < miniCutoff) readMiniChain(entry.start, entry.size) else readChain(entry.start, entry.size)
        }
    }
}
