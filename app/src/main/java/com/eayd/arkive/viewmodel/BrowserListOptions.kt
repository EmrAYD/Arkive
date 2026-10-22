package com.eayd.arkive.viewmodel

import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.utils.FileKind
import com.eayd.arkive.utils.TurkishLocale
import com.eayd.arkive.utils.fileKind
import java.text.Collator

/** "Sırala" seçenekleri. Sıralama yalnızca ekrandaki liste üzerinde, istemcide yapılır. */
enum class SortOption(val label: String) {
    NameAsc("Ad (A-Z)"),
    NameDesc("Ad (Z-A)"),
    Newest("En yeni"),
    Oldest("En eski"),
    Largest("Boyut (büyükten küçüğe)"),
    Smallest("Boyut (küçükten büyüğe)"),
}

/** "Kategori" seçenekleri: dosyaları türüne göre süzer. */
enum class CategoryOption(val label: String) {
    All("Tümü"),
    Images("Resimler"),
    Videos("Videolar"),
    Audio("Ses"),
    Documents("Belgeler"),
    Other("Diğer");

    fun matches(mimeType: String?): Boolean {
        val kind = fileKind(mimeType)
        return when (this) {
            All -> true
            Images -> kind == FileKind.Image
            Videos -> kind == FileKind.Video
            Audio -> kind == FileKind.Audio
            Documents -> kind in documentKinds
            Other -> kind == FileKind.Archive || kind == FileKind.Other
        }
    }
}

private val documentKinds = setOf(
    FileKind.Pdf, FileKind.Text, FileKind.Word, FileKind.Excel, FileKind.Presentation, FileKind.Document,
)

// Türkçe harfleri (ç, ğ, ı, ö, ş, ü) doğru sıralayan karşılaştırıcı; düz string karşılaştırması bunları sona atardı.
private val turkishCollator: Collator = Collator.getInstance(TurkishLocale)

/** Favoriler her zaman üstte; kendi aralarında seçilen sıralama uygulanır. */
fun List<FolderDto>.sortedFolders(sort: SortOption): List<FolderDto> {
    // created_at sunucudan hep aynı (ISO 8601, UTC) biçimde gelir; metin olarak karşılaştırmak tarih sırasını verir.
    val order = when (sort) {
        SortOption.NameDesc -> Comparator<FolderDto> { a, b -> turkishCollator.compare(b.name, a.name) }
        SortOption.Newest -> compareByDescending<FolderDto> { it.createdAt }
        SortOption.Oldest -> compareBy<FolderDto> { it.createdAt }
        // Klasörün boyutu yok: boyut sıralamasında ada göre.
        else -> Comparator<FolderDto> { a, b -> turkishCollator.compare(a.name, b.name) }
    }
    return sortedWith(compareByDescending<FolderDto> { it.favorite }.then(order))
}

fun List<FileDto>.sortedFiles(sort: SortOption): List<FileDto> {
    val order = when (sort) {
        SortOption.NameAsc -> Comparator<FileDto> { a, b -> turkishCollator.compare(a.name, b.name) }
        SortOption.NameDesc -> Comparator<FileDto> { a, b -> turkishCollator.compare(b.name, a.name) }
        SortOption.Newest -> compareByDescending<FileDto> { it.createdAt }
        SortOption.Oldest -> compareBy<FileDto> { it.createdAt }
        SortOption.Largest -> compareByDescending<FileDto> { it.size }
        SortOption.Smallest -> compareBy<FileDto> { it.size }
    }
    return sortedWith(compareByDescending<FileDto> { it.favorite }.then(order))
}
