package com.eayd.arkive.viewmodel

import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FolderDto
import com.eayd.arkive.i18n.Txt
import com.eayd.arkive.utils.FileKind
import com.eayd.arkive.utils.TurkishLocale
import com.eayd.arkive.utils.fileKind
import java.text.Collator

/** Sıralama seçenekleri. */
enum class SortOption {
    NameAsc,
    NameDesc,
    Newest,
    Oldest,
    Largest,
    Smallest,
    ;

    val label: String
        get() = when (this) {
            NameAsc -> Txt.browser.sortNameAsc
            NameDesc -> Txt.browser.sortNameDesc
            Newest -> Txt.browser.sortNewest
            Oldest -> Txt.browser.sortOldest
            Largest -> Txt.browser.sortLargest
            Smallest -> Txt.browser.sortSmallest
        }
}

/** Dosyaları türüne göre süzen seçenekler. */
enum class CategoryOption {
    All,
    Images,
    Videos,
    Audio,
    Documents,
    Other,
    ;

    val label: String
        get() = when (this) {
            All -> Txt.browser.categoryAll
            Images -> Txt.browser.categoryImages
            Videos -> Txt.browser.categoryVideos
            Audio -> Txt.browser.categoryAudio
            Documents -> Txt.browser.categoryDocuments
            Other -> Txt.browser.categoryOther
        }

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

private val turkishCollator: Collator = Collator.getInstance(TurkishLocale)

/** Favoriler hep üstte, kendi aralarında seçilen sırada. */
fun List<FolderDto>.sortedFolders(sort: SortOption): List<FolderDto> {
    val order = when (sort) {
        SortOption.NameDesc -> Comparator<FolderDto> { a, b -> turkishCollator.compare(b.name, a.name) }
        SortOption.Newest -> compareByDescending<FolderDto> { it.createdAt }
        SortOption.Oldest -> compareBy<FolderDto> { it.createdAt }
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
