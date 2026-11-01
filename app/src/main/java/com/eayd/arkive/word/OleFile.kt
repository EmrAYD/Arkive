package com.eayd.arkive.word

/**
 * Eski Office dosyalarının (.doc) kapsayıcısı olan OLE "Compound File" biçimini (MS-CFB) okur: adıyla akış verir.
 * Dosya baytları bellektedir; bozuk/uydurma yapılar [WordFormatException] ile reddedilir (sonsuz döngü ve dev
 * bellek isteği yok).
 */
internal class OleFile(private val data: ByteArray) {
    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Long
    private val fat: LongArray
    private val miniFat: LongArray
    private val directory: ByteArray
    private val rootStart: Long
    private val rootSize: Long
    private var miniStream: ByteArray? = null

    private class Entry(val name: String, val type: Int, val start: Long, val size: Long)

    private val entries: List<Entry>

    init {
        if (data.size < 512 || !isOle(data)) throw WordFormatException("OLE dosyası değil.")
        val sectorShift = u16(0x1E)
        val miniShift = u16(0x20)
        if (sectorShift != 9 && sectorShift != 12) throw WordFormatException("OLE bozuk.")
        if (miniShift > 12 || miniShift < 3) throw WordFormatException("OLE bozuk.")
        sectorSize = 1 shl sectorShift
        miniSectorSize = 1 shl miniShift
        miniCutoff = u32(0x38)

        val fatSectors = ArrayList<Long>()
        for (i in 0 until 109) {
            val s = u32(0x4C + 4 * i)
            if (s < END_OF_CHAIN) fatSectors.add(s)
        }
        var difat = u32(0x44)
        var guard = 0
        while (difat < END_OF_CHAIN && guard++ < 10_000) {
            val base = offset(difat)
            for (i in 0 until sectorSize / 4 - 1) {
                val s = u32(base + 4 * i)
                if (s < END_OF_CHAIN) fatSectors.add(s)
            }
            difat = u32(base + sectorSize - 4)
        }
        val perSector = sectorSize / 4
        if (fatSectors.size.toLong() * perSector > MAX_FAT_ENTRIES) throw WordFormatException("OLE çok büyük.")
        fat = LongArray(fatSectors.size * perSector)
        fatSectors.forEachIndexed { n, s ->
            val base = offset(s)
            for (i in 0 until perSector) fat[n * perSector + i] = u32(base + 4 * i)
        }

        directory = readChain(u32(0x30), MAX_STREAM_BYTES)
        entries = (0 until directory.size / 128).map { i ->
            val o = i * 128
            val nameLen = ((directory[o + 0x40].toInt() and 0xFF) or ((directory[o + 0x41].toInt() and 0xFF) shl 8)).coerceIn(0, 64)
            Entry(
                name = String(directory, o, maxOf(nameLen - 2, 0), Charsets.UTF_16LE),
                type = directory[o + 0x42].toInt(),
                start = u32(directory, o + 0x74),
                size = u32(directory, o + 0x78),
            )
        }
        val root = entries.firstOrNull { it.type == 5 } ?: throw WordFormatException("OLE bozuk.")
        rootStart = root.start
        rootSize = root.size

        val miniFatBytes = readChain(u32(0x3C), MAX_STREAM_BYTES)
        miniFat = LongArray(miniFatBytes.size / 4) { u32(miniFatBytes, it * 4) }
    }

    fun hasStream(name: String): Boolean = entries.any { it.type == 2 && it.name == name }

    /** Adı [name] olan akışın baytları; yoksa null. */
    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type == 2 && it.name == name } ?: return null
        if (entry.size > MAX_STREAM_BYTES) throw WordFormatException("OLE akışı çok büyük.")
        return if (entry.size < miniCutoff) readMiniChain(entry.start, entry.size) else readChain(entry.start, entry.size)
    }

    private fun u16(o: Int) = u16(data, o)
    private fun u32(o: Int) = u32(data, o)

    private fun offset(sector: Long): Int {
        val o = (sector + 1) * sectorSize
        if (o < 0 || o + sectorSize > data.size) throw WordFormatException("OLE bozuk.")
        return o.toInt()
    }

    private fun readChain(start: Long, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s < END_OF_CHAIN && out.size() < limit) {
            if (guard++ > fat.size + 1 || s >= fat.size) throw WordFormatException("OLE bozuk.")
            out.write(data, offset(s), sectorSize)
            s = fat[s.toInt()]
        }
        val bytes = out.toByteArray()
        return if (bytes.size > limit) bytes.copyOf(limit.toInt()) else bytes
    }

    private fun readMiniChain(start: Long, size: Long): ByteArray {
        val mini = miniStream ?: readChain(rootStart, rootSize).also { miniStream = it }
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s < END_OF_CHAIN && out.size() < size) {
            if (guard++ > miniFat.size + 1 || s >= miniFat.size) throw WordFormatException("OLE bozuk.")
            val o = s.toInt() * miniSectorSize
            if (o + miniSectorSize > mini.size) throw WordFormatException("OLE bozuk.")
            out.write(mini, o, miniSectorSize)
            s = miniFat[s.toInt()]
        }
        val bytes = out.toByteArray()
        return if (bytes.size > size) bytes.copyOf(size.toInt()) else bytes
    }

    companion object {
        private const val END_OF_CHAIN = 0xFFFFFFFAL
        private const val MAX_STREAM_BYTES = 64L * 1024 * 1024
        private const val MAX_FAT_ENTRIES = 32L * 1024 * 1024
        private val MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

        fun isOle(bytes: ByteArray): Boolean = bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

        fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        fun u32(b: ByteArray, o: Int): Long = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
    }
}
