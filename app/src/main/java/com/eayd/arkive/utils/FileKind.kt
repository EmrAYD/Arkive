package com.eayd.arkive.utils


/**
 * Dosyanın mime türüne göre kaba sınıfı. Simge ([com.eayd.arkive.ui.components.fileTypeIcon]), tür adı
 * ([fileTypeLabel]), ön izleme türü ([previewKind]) ve "Kategori" filtresi aynı sınıflandırmayı kullanır;
 * böylece bir dosya bir yerde "belge", başka yerde "diğer" sayılmaz.
 */
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

/** Dosya türünün okunur adı: "Resim (JPG)", "PDF", "Word belgesi (DOCX)"… */
fun fileTypeLabel(mimeType: String?, fileName: String): String {
    val kind = when (fileKind(mimeType)) {
        FileKind.Image -> "Resim"
        FileKind.Video -> "Video"
        FileKind.Audio -> "Ses"
        // PDF'in tek bir adı var: uzantı eklemeye gerek yok.
        FileKind.Pdf -> return "PDF"
        FileKind.Text -> "Metin dosyası"
        FileKind.Word -> "Word belgesi"
        FileKind.Excel -> "Excel tablosu"
        FileKind.Presentation -> "Sunum"
        FileKind.Document -> "Belge"
        FileKind.Archive -> "Arşiv"
        FileKind.Other -> "Dosya"
    }
    // Uzantı çok uzunsa (ör. noktalı bir ad) gösterme.
    val extension = fileName.substringAfterLast('.', "").uppercase()
    return if (extension.isNotEmpty() && extension.length <= 5) "$kind ($extension)" else kind
}

/** Uygulamanın içinde gösterebildiğimiz dosya türleri. */
enum class PreviewKind { Image, Pdf, Text, Video, Audio, None }

private val textMimeTypes = setOf(
    "application/json", "application/xml", "application/javascript", "application/x-yaml",
    "application/yaml", "application/x-sh", "application/sql", "application/x-httpd-php",
)

private val textExtensions = setOf(
    "txt", "md", "csv", "tsv", "json", "xml", "yaml", "yml", "log", "ini", "conf", "properties",
    "html", "htm", "css", "js", "ts", "kt", "kts", "java", "py", "c", "cpp", "h", "cs", "go", "rs",
    "sql", "sh", "bat", "gradle", "toml",
)

/**
 * Dosyanın hangi tür ön izlemeyle açılacağını belirler. Önce sunucudaki mime türüne bakılır; tür genel
 * ("application/octet-stream") ya da bilinmiyorsa dosya uzantısına bakılır.
 */
fun previewKind(mimeType: String?, fileName: String): PreviewKind {
    val mime = mimeType?.lowercase().orEmpty()
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val kind = fileKind(mime)
    return when {
        // SVG bir "image/" türüdür ama BitmapFactory çözemez.
        mime == "image/svg+xml" -> PreviewKind.None
        kind == FileKind.Image -> PreviewKind.Image
        kind == FileKind.Pdf || ext == "pdf" -> PreviewKind.Pdf
        kind == FileKind.Video -> PreviewKind.Video
        kind == FileKind.Audio -> PreviewKind.Audio
        kind == FileKind.Text || mime in textMimeTypes || ext in textExtensions -> PreviewKind.Text
        else -> PreviewKind.None
    }
}
