package com.eayd.arkive.navigation

/**
 * Uygulamanın ekran adresleri. Rotada yalnızca kimlikler (UUID) taşınır; ad, içerik ve kullanıcı bilgisi
 * ilgili ekranın ViewModel'i tarafından sunucudan / oturumdan alınır.
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"

    const val FOLDER = "folder/{folderId}"
    const val PREVIEW = "preview/{fileId}?readOnly={readOnly}"
    const val TRASH_FOLDER = "trash/folder/{folderId}"

    const val ARG_FOLDER_ID = "folderId"
    const val ARG_FILE_ID = "fileId"
    const val ARG_READ_ONLY = "readOnly"

    fun folder(id: String) = "folder/$id"

    /** [readOnly]: çöpteki dosya; ön izlenir ama değiştirilemez. */
    fun preview(id: String, readOnly: Boolean = false) = "preview/$id?readOnly=$readOnly"

    fun trashFolder(id: String) = "trash/folder/$id"
}
