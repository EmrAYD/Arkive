package com.eayd.arkive.navigation

/** Ekran adresleri. Adreslerde yalnızca kimlik taşınır; geri kalanı ekran sunucudan alır. */
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

    /** [readOnly] çöpteki dosya içindir; değiştirilemez. */
    fun preview(id: String, readOnly: Boolean = false) = "preview/$id?readOnly=$readOnly"

    fun trashFolder(id: String) = "trash/folder/$id"
}
