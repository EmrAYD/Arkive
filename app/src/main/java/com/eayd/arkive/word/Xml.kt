package com.eayd.arkive.word

import com.eayd.arkive.i18n.Txt
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Küçük bir XML ağacı. Eleman adları kısa önekle tutulur ("w:p"). */
internal class XNode(val name: String, val attrs: Map<String, String>) {
    val kids = ArrayList<XNode>()
    private var chars: StringBuilder? = null

    val text: String get() = chars?.toString() ?: ""

    fun appendText(buffer: CharArray, start: Int, length: Int) {
        val sb = chars ?: StringBuilder().also { chars = it }
        sb.append(buffer, start, length)
    }

    fun attr(key: String): String? = attrs[key]

    fun child(childName: String): XNode? {
        for (k in kids) if (k.name == childName) return k
        return null
    }

    fun children(childName: String): List<XNode> = kids.filter { it.name == childName }

    /** "w:b" gibi açık/kapalı elemanlar için: var ve kapalı değilse true. */
    fun isOn(): Boolean = when (attrs["val"]?.lowercase()) {
        null, "1", "true", "on" -> true
        else -> false
    }
}

internal object Xml {
    private const val MAX_DEPTH = 200

    /** Ad alanı adresinden kısa önek. */
    private fun prefixOf(uri: String): String = when {
        uri.endsWith("/wordprocessingml/2006/main") || uri.endsWith("/ooxml/wordprocessingml/main") -> "w"
        uri.endsWith("/drawingml/2006/main") || uri.endsWith("/ooxml/drawingml/main") -> "a"
        uri.endsWith("/drawingml/2006/wordprocessingDrawing") || uri.endsWith("/ooxml/drawingml/wordprocessingDrawing") -> "wp"
        uri.endsWith("/drawingml/2006/picture") || uri.endsWith("/ooxml/drawingml/picture") -> "pic"
        uri.endsWith("/2010/wordprocessingShape") || uri.endsWith("/2006/wordprocessingShape") -> "wps"
        uri.endsWith("/officeDocument/2006/math") || uri.endsWith("/ooxml/math") -> "m"
        uri == "urn:schemas-microsoft-com:vml" -> "v"
        uri.endsWith("/markup-compatibility/2006") -> "mc"
        else -> ""
    }

    /** XML'i ayrıştırır. Güvenlik için DOCTYPE içeren dosyalar reddedilir. */
    fun parse(bytes: ByteArray): XNode {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) throw WordFormatException(Txt.docs.xmlUnsupportedEncoding)
        }
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        if (head.contains("<!DOCTYPE", ignoreCase = true) || head.contains("<!ENTITY", ignoreCase = true)) {
            throw WordFormatException(Txt.docs.xmlUnsafe)
        }

        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isValidating = false
        for ((feature, value) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )) {
            try { factory.setFeature(feature, value) } catch (e: Exception) { }
        }

        val handler = TreeHandler()
        try {
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(java.io.StringReader("")) }
            reader.parse(InputSource(ByteArrayInputStream(bytes)))
        } catch (e: SAXException) {
            throw WordFormatException(Txt.docs.xmlUnreadableWith(e.message))
        } catch (e: java.io.IOException) {
            throw WordFormatException(Txt.docs.xmlUnreadable)
        }
        return handler.root ?: throw WordFormatException(Txt.docs.xmlEmpty)
    }

    private class TreeHandler : DefaultHandler() {
        var root: XNode? = null
        private val stack = ArrayList<XNode>()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            if (stack.size >= MAX_DEPTH) throw SAXException(Txt.docs.xmlTooDeep)
            val local = localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfter(':').orEmpty()
            val prefix = prefixOf(uri.orEmpty())
            val name = if (prefix.isEmpty()) local else "$prefix:$local"
            val map = HashMap<String, String>(4)
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val key = attributes.getLocalName(i)?.takeIf { it.isNotEmpty() } ?: attributes.getQName(i).substringAfter(':')
                    map[key] = attributes.getValue(i)
                }
            }
            val node = XNode(name, map)
            if (stack.isEmpty()) root = node else stack[stack.size - 1].kids.add(node)
            stack.add(node)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (stack.isNotEmpty()) stack[stack.size - 1].appendText(ch, start, length)
        }
    }
}
