package com.eayd.arkive.utils

import com.eayd.arkive.i18n.Txt

/** Dosyanın türüne göre kaba sınıfı. Simge, tür adı, ön izleme ve filtre aynı sınıflandırmayı kullanır. */
enum class FileKind { Image, Video, Audio, Pdf, Text, Word, Excel, Presentation, Document, Archive, Other }

private val archiveMarkers = listOf("zip", "rar", "7z", "tar", "gzip", "compressed")

fun fileKind(mimeType: String?): FileKind {
    val mime = mimeType?.lowercase().orEmpty()
    return when {
        mime.startsWith("image/") -> FileKind.Image
        mime.startsWith("video/") -> FileKind.Video
        mime.startsWith("audio/") -> FileKind.Audio
        mime == "application/pdf" -> FileKind.Pdf
        mime.startsWith("text/") -> FileKind.Text
        "word" in mime -> FileKind.Word
        "excel" in mime || "spreadsheet" in mime -> FileKind.Excel
        "powerpoint" in mime || "presentation" in mime -> FileKind.Presentation
        mime == "application/rtf" || "opendocument" in mime -> FileKind.Document
        archiveMarkers.any { it in mime } -> FileKind.Archive
        else -> FileKind.Other
    }
}

/** Dosya türünün okunur adı, ör. "Word belgesi (DOCX)". */
fun fileTypeLabel(mimeType: String?, fileName: String): String {
    val kind = when (fileKind(mimeType)) {
        FileKind.Image -> Txt.files.kindImage
        FileKind.Video -> Txt.files.kindVideo
        FileKind.Audio -> Txt.files.kindAudio
        FileKind.Pdf -> return "PDF"
        FileKind.Text -> Txt.files.kindText
        FileKind.Word -> Txt.files.kindWord
        FileKind.Excel -> Txt.files.kindExcel
        FileKind.Presentation -> Txt.files.kindPresentation
        FileKind.Document -> Txt.files.kindDocument
        FileKind.Archive -> Txt.files.kindArchive
        FileKind.Other -> Txt.files.kindFile
    }
    val extension = fileName.substringAfterLast('.', "").uppercase()
    return if (extension.isNotEmpty() && extension.length <= 5) "$kind ($extension)" else kind
}

private val wordMimeTypes = setOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
    "application/msword",
    "application/vnd.ms-word",
    "application/vnd.ms-word.document.macroenabled.12",
    "application/vnd.ms-word.template.macroenabled.12",
)

private val wordExtensions = setOf("docx", "docm", "dotx", "dotm", "doc", "dot")

/** Uygulamanın içinde gösterilebilen türler. */
enum class PreviewKind { Image, Pdf, Word, Text, Video, Audio, None }

private val textMimeTypes = setOf(
    "application/json", "application/xml", "application/javascript", "application/x-yaml",
    "application/yaml", "application/x-sh", "application/sql", "application/x-httpd-php",
)

private val textExtensions = setOf(
    "txt", "md", "csv", "tsv", "json", "xml", "yaml", "yml", "log", "ini", "conf", "properties",
    "html", "htm", "css", "js", "ts", "kt", "kts", "java", "py", "c", "cpp", "h", "cs", "go", "rs",
    "sql", "sh", "bat", "gradle", "toml",
)

/** Dosyanın hangi ön izlemeyle açılacağı. Tür bilinmiyorsa uzantıya bakılır. */
fun previewKind(mimeType: String?, fileName: String): PreviewKind {
    val mime = mimeType?.lowercase().orEmpty()
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val kind = fileKind(mime)
    return when {
        mime == "image/svg+xml" -> PreviewKind.None
        kind == FileKind.Image -> PreviewKind.Image
        kind == FileKind.Pdf || ext == "pdf" -> PreviewKind.Pdf
        mime in wordMimeTypes || ext in wordExtensions -> PreviewKind.Word
        kind == FileKind.Video -> PreviewKind.Video
        kind == FileKind.Audio -> PreviewKind.Audio
        kind == FileKind.Text || mime in textMimeTypes || ext in textExtensions -> PreviewKind.Text
        else -> PreviewKind.None
    }
}
