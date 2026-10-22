package com.eayd.arkive.viewmodel

import com.eayd.arkive.data.FileDto
import com.eayd.arkive.data.FolderDto

/** Seçim ve sürükleme için satır anahtarları. Dosya ve klasör kimlikleri karışmasın diye ön ek taşır. */
private const val FILE_PREFIX = "file:"
private const val FOLDER_PREFIX = "folder:"

fun fileKey(id: String) = FILE_PREFIX + id
fun folderKey(id: String) = FOLDER_PREFIX + id

/**
 * Liste ekranlarında çoklu seçimin durumu (ana liste ve çöp kutusu ortak kullanır). [active]: seçim modu açık mı;
 * çöp kutusunda hiçbir şey seçili olmasa da mod açık kalabilir ("Seç" düğmesi).
 */
data class Selection(val active: Boolean = false, val keys: Set<String> = emptySet()) {
    val count: Int get() = keys.size

    fun start(key: String? = null) = Selection(active = true, keys = listOfNotNull(key).toSet())

    fun add(key: String) = Selection(active = true, keys = keys + key)

    fun toggle(key: String) = copy(keys = if (key in keys) keys - key else keys + key)

    fun selectAll(all: Set<String>) = copy(keys = all)

    /** Listeden kalkmış (taşınmış, silinmiş) öğeler seçimden düşer. */
    fun retain(existing: Set<String>) = copy(keys = keys intersect existing)

    /** Seçilen dosyaların / klasörlerin kimlikleri (anahtarların ön eki atılmış hâli). */
    val fileIds: List<String> get() = keys.filter { it.startsWith(FILE_PREFIX) }.map { it.removePrefix(FILE_PREFIX) }
    val folderIds: List<String> get() = keys.filter { it.startsWith(FOLDER_PREFIX) }.map { it.removePrefix(FOLDER_PREFIX) }
}

/** Seçilen (ya da sürüklenen) dosyalar ve klasörler. */
class SelectedItems(val files: List<FileDto>, val folders: List<FolderDto>) {
    val count: Int get() = files.size + folders.size

    /** Satır anahtarları: sürüklenen satırları soluk göstermek için. */
    val keys: Set<String> = files.map { fileKey(it.id) }.toSet() + folders.map { folderKey(it.id) }.toSet()

    val allFavorite: Boolean get() = files.all { it.favorite } && folders.all { it.favorite }

    /** Tek öğeyse adı, değilse "N öğe". */
    val label: String
        get() = if (count == 1) (files.firstOrNull()?.name ?: folders.first().name) else "$count öğe"
}
