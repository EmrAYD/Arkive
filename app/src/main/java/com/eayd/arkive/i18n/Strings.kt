package com.eayd.arkive.i18n

/** Uygulamanın bütün metinleri. Yeni bir metin eklerken bu arayüze ve StringsTr ile StringsEn'e aynı üyeyi ekleyin. */
interface Strings {
    val ai: AiStrings
    val docs: DocsStrings
    val common: CommonStrings
    val files: FilesStrings
    val emoji: EmojiStrings
    val errors: ErrorsStrings
    val browser: BrowserStrings
    val auth: AuthStrings
    val plan: PlanStrings
    val profile: ProfileStrings
    val home: HomeStrings
    val preview: PreviewStrings
    val trash: TrashStrings
    val eula: EulaStrings
    val language: LanguageStrings
    val localAi: LocalAiStrings
    val aiMode: AiModeStrings
    val search: SearchStrings
    val speech: SpeechStrings
}

/** Yapay zeka özetleme metinleri. */
interface AiStrings {
    val rejectTooBig: String
    val rejectTooManyPages: String
    val rejectImageHeavy: String
    val rejectNoText: String
    val preparing: String
    fun stopped(name: Any?): String
    val notUploadedYet: String
    val openingSummary: String
    val shownFromPhoneCopy: String
    val noPhoneCopy: String
    fun summarizeFailed(name: Any?, reason: Any?): String
    fun notOnPhoneAndOffline(name: Any?): String
    val fileOpenFailed: String
    val readingFile: String
    val summarizingOnPhone: String
    fun saveToServerFailed(name: Any?): String
    fun uploadedToServer(fileName: Any?): String
    fun saveFailed(fileName: Any?, message: Any?): String
    val summarizingOnServer: String
    fun serverUnreachableStillRunning(name: Any?): String
    val connectionLostStillRunning: String
    fun serverJobFailed(fileName: Any?): String
    val unsupportedType: String
    val summaryDoneTitle: String
    val tapToView: String
    fun summarizedFile(fileName: Any?): String
    val savedOnPhoneOnly: String
    val alsoSavedToAiResults: String
    fun notificationTextSavedOnPhone(fileName: Any?): String
    fun notificationText(fileName: Any?): String
    val serviceStarting: String
    val serviceSummarizing: String
    fun serviceSummarizingFiles(size: Any?): String
    val serviceSummarizingFile: String
    val serviceChannelName: String
    val confirmTitle: String
    fun confirmBody(name: Any?): String
    val confirmOnPhone: String
    val confirmPhoneThenCloud: String
    val confirmInCloud: String
    val summarize: String
    val showSummary: String
    val passwordTitle: String
    fun passwordBody(name: Any?): String
    val passwordLocalNote: String
    val passwordCloudNote: String
    val passwordHybridNote: String
    val runningTitle: String
    fun runningBody(name: Any?): String
    val keepWaiting: String
    val summaryTitle: String
    val summaryCopied: String
    val summarizing: String
    fun progressPercent(percent: Any?): String
    val showSummaryA11y: String
    val summarizeA11y: String
    fun summaryOf(fileName: Any?): String
    val needsConnection: String
    val openSourceFile: String
    val deleteSummary: String
    fun deleteSummaryBody(name: Any?): String
    fun summaryDeleted(name: Any?): String
    val loadingModel: String
    fun summarizingOnPhoneStep(done: Any?, total: Any?): String
    fun resumedOnPhone(name: Any?): String
    fun resumedInCloud(name: Any?): String
    fun phoneRunInterrupted(name: Any?): String
    val summaryFailedChannel: String
    val failedNotificationTitle: String
    fun failedNotificationText(name: Any?, message: Any?): String
    fun failedPlain(name: Any?): String
    val passwordNeededTitle: String
    fun passwordNeededText(name: Any?): String
}

/** Belge okuyucuların hata mesajları. */
interface DocsStrings {
    val wrongPassword: String
    val passwordProtected: String
    val unreadableMaybeCorrupt: String
    val pdfOpenFailed: String
    val pdfTooBigForPassword: String
    val encryptedWordUnreadable: String
    val wordTooBig: String
    val wordNotDocxOrCorrupt: String
    val encryptionInfoMissing: String
    val encryptedContentMissing: String
    val encryptionInfoCorrupt: String
    val wordEncryptionUnsupported: String
    val encryptedWordUnreadableMaybeCorrupt: String
    val encryptedContentCorrupt: String
    val fileCorrupt: String
    val password: String
    val wordUnreadable: String
    val wordUnreadableCorrupt: String
    val docEncryptionUnsupported: String
    val wordNoTableStream: String
    val noText: String
    val noPieceTable: String
    val pieceTableCorrupt: String
    val pieceTableNotFound: String
    val wordOldVersion: String
    val wordUnreadableMaybeCorrupt: String
    val wordCorrupt: String
    val wordNoBody: String
    val footnotes: String
    val notOle: String
    val oleCorrupt: String
    val oleTooBig: String
    val oleStreamTooBig: String
    val encryptedWordCorrupt: String
    val notWordExpected: String
    val tooBigForPreview: String
    val notWord: String
    val xmlUnsupportedEncoding: String
    val xmlUnsafe: String
    fun xmlUnreadableWith(message: Any?): String
    val xmlUnreadable: String
    val xmlEmpty: String
    val xmlTooDeep: String
    val excelUnreadable: String
    val powerPointUnreadable: String
    val encryptedExcelUnreadable: String
    val encryptedPowerPointUnreadable: String
    val officeTooBig: String
}

/** Birçok ekranda geçen kısa metinler. */
interface CommonStrings {
    val download: String
    val move: String
    val rename: String
    val info: String
    val delete: String
    val edit: String
    val cancel: String
    val stop: String
    val copy: String
    val close: String
    val removeFromFavorites: String
    val addToFavorites: String
    val more: String
    val selected: String
    val notSelected: String
    val loading: String
    val retry: String
    val create: String
    val save: String
    val back: String
    val loadingEllipsis: String
    val open: String
}

/** Dosya türleri, bilgileri, adlandırma ve indirme. */
interface FilesStrings {
    val nameTooLong: String
    val nameInvalidChars: String
    val color: String
    val emoji: String
    val noColor: String
    fun colorHex(hex: Any?): String
    val type: String
    val size: String
    val spaceUsed: String
    val created: String
    fun savedToDownloads(subfolder: Any?): String
    fun downloading(fileName: Any?): String
    fun downloadResult(fileName: Any?, where: Any?): String
    val dateTimePattern: String
    val kindImage: String
    val kindVideo: String
    val kindAudio: String
    val kindText: String
    val kindWord: String
    val kindExcel: String
    val kindPresentation: String
    val kindDocument: String
    val kindArchive: String
    val kindFile: String
    val quotaLessThanTenth: String
    fun quotaPercent(percent: Any?): String
}

/** Emoji seçici. */
interface EmojiStrings {
    val facesAndEmotions: String
    val people: String
    val animalsAndNature: String
    val foodAndDrink: String
    val travelAndPlaces: String
    val activitiesAndSports: String
    val objects: String
    val symbols: String
    val flags: String
    val noEmoji: String
    fun emojiValue(value: Any?): String
    val allEmojis: String
    val chooseEmoji: String
}

/** Ağ, sunucu ve depolama hataları. */
interface ErrorsStrings {
    val storagePermissionNeeded: String
    val notSignedIn: String
    val credentialsUnavailable: String
    val downloadFromStorageFailed: String
    fun storageRefusedFile(code: Any?): String
    val storageEmptyResponse: String
    val tooBigForPreview: String
    val saveToDownloadsFailed: String
    val writeToDownloadsFailed: String
    val writeToDownloadsPermission: String
    val fileOpenFailed: String
    val emptyFileUpload: String
    val sendToStorageFailed: String
    val storageRejectedSignature: String
    fun storageRejectedUpload(code: Any?): String
    val fileChangedDuringUpload: String
    val wifiOnly: String
    val badRequest: String
    val conflict: String
    val tooLargeOrNoSpace: String
    val serviceUnavailable: String
    val sessionInvalid: String
    val forbidden: String
    val notFound: String
    val invalidInput: String
    val serverError: String
    fun requestFailed(c: Any?): String
    val serverTimeout: String
    val serverUnreachable: String
    val connectionError: String
    val unexpected: String
}

/** Klasör ve dosya listeleri, yükleme ve taşıma. */
interface BrowserStrings {
    val parentFolder: String
    val noSubfolders: String
    val alreadyHere: String
    val moveHere: String
    val sortNameAsc: String
    val sortNameDesc: String
    val sortNewest: String
    val sortOldest: String
    val sortLargest: String
    val sortSmallest: String
    val categoryAll: String
    val categoryImages: String
    val categoryVideos: String
    val categoryAudio: String
    val categoryDocuments: String
    val categoryOther: String
    fun batchFailed(failed: Any?, firstError: Any?): String
    fun quoted(label: Any?): String
    fun movedTo(what: Any?, targetName: Any?): String
    val folderFallback: String
    fun itemCount(count: Any?): String
    val offlineBanner: String
    val noCategoryMatch: String
    val emptyAiResults: String
    val emptyFavorites: String
    val emptyHome: String
    val emptyFolders: String
    val newFolder: String
    val folderName: String
    val editFolder: String
    val fileName: String
    val folderToTrashTitle: String
    fun folderToTrashBody(name: Any?): String
    val moveToTrash: String
    val fileToTrashTitle: String
    fun fileToTrashBody(name: Any?): String
    fun itemsToTrashTitle(count: Any?): String
    val itemsToTrashBody: String
    val itemsToTrashFolders: String
    val itemsToTrashRestore: String
    val cancelUpload: String
    val uploadQueued: String
    fun uploadProgress(progress: Any?): String
    val uploadFinishing: String
    val uploadFailed: String
    val add: String
    val uploadFile: String
    val sort: String
    val category: String
    val closeSelection: String
    fun selectedCount(count: Any?): String
    val selectAll: String
}

/** Giriş ve kayıt. */
interface AuthStrings {
    val hidePassword: String
    val showPassword: String
    val fillAllFields: String
    fun passwordMinLength(minLength: Any?): String
    val registerFailed: String
    val userNotCreated: String
    val enterEmailAndPassword: String
    val loginFailed: String
    val userNotFound: String
    val googleCancelledOrFailed: String
    val googleFailedRetry: String
    val googleFailed: String
    val eulaDeclined: String
    val enterEmailFirst: String
    val resetLinkSent: String
    val resetLinkFailed: String
    val noInternet: String
    val tooManyAttempts: String
    val invalidEmail: String
    val wrongCredentials: String
    val accountDisabled: String
    val emailInUse: String
    val weakPassword: String
    val methodUnavailable: String
    val otherProvider: String
    val logoA11y: String
    val welcome: String
    val password: String
    val hidePasswordTitle: String
    val showPasswordTitle: String
    val googleLogoA11y: String
    val signIn: String
    val forgotPassword: String
    val signInWithGoogle: String
    val noAccount: String
    val signUp: String
    val ruleMinLength: String
    val ruleSpecialChar: String
    val ruleUppercase: String
    val ruleLowercase: String
    val signUpWithGoogle: String
    val haveAccount: String
    val splashBackgroundA11y: String
    val chestA11y: String
    fun ruleMinLengthN(minLength: Any?): String
    val email: String
}

/** Planlar ve Plus tanıtımı. */
interface PlanStrings {
    val offerTagline: String
    fun offerStorage(storageText: Any?): String
    fun offerStorageFree(freeStorageText: Any?): String
    fun offerMaxFile(maxFileText: Any?): String
    fun offerMaxFileFree(freeMaxFileText: Any?): String
    val offerUnlimited: String
    fun offerUnlimitedFree(freeSummaryLimit: Any?): String
    val offerCloud: String
    val offerCloudDetail: String
    val switchedToPlus: String
    val onPlus: String
    fun goPlusWithPrice(priceText: Any?): String
    val switchedToFree: String
    val backToFreeTrial: String
    val trialNote: String
    val price: String
    fun freeLimitReached(limit: Any?): String
    fun cantSummarizeOnPhone(fileName: Any?, reason: Any?): String
    val goPlus: String
    val cloudPlusOnly: String
    val localAiUnsupported: String
}

/** Profil ve ayarlar. */
interface ProfileStrings {
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val currentPasswordWrong: String
    val reloginRequired: String
    val passwordChangeFailed: String
    val switchedToFree: String
    val passwordChangeUnavailable: String
    val passwordChanged: String
    fun retentionSaved(trashRetentionDays: Any?): String
    val newPasswordSameAsCurrent: String
    val passwordsDontMatch: String
    val changePassword: String
    val currentPassword: String
    val newPassword: String
    val newPasswordAgain: String
    val change: String
    val title: String
    val notificationSettingsFailed: String
    val signOut: String
    val theme: String
    val trashRetention: String
    val trashRetentionDetail: String
    fun days(it: Any?): String
    val account: String
    val googleManagedPassword: String
    val settings: String
    val notificationSettings: String
    val wifiOnly: String
    val wifiOnlyDetail: String
    val myPlan: String
    val planUnavailable: String
    fun planName(planName: Any?): String
    val maxFileLimit: String
    val totalStorage: String
    fun usedOf(used: Any?, limit: Any?): String
    fun remaining(remaining: Any?): String
    val morePlus: String
    fun plusPerFile(maxFileSize: Any?): String
    fun plusStorage(limit: Any?): String
    fun plusAi(freeSummaryLimit: Any?): String
}

/** Ana sayfa ve yan menü. */
interface HomeStrings {
    val aiResults: String
    val home: String
    val favorites: String
    val trash: String
    val storage: String
    fun storageUsage(limit: Any?, used: Any?): String
    fun remainingAndPlan(remaining: Any?, planName: Any?): String
    fun updateFailed(error: Any?): String
    val getMoreStorage: String
    val usageUnavailable: String
    val openMenu: String
}

/** Dosya ön izleme. */
interface PreviewStrings {
    val offlineNotSaved: String
    val unsupportedType: String
    fun tooBig(size: Any?): String
    val offlineTypeUnsupported: String
    val imageFormatUnsupported: String
    val pdfNoPages: String
    val pdfOpenFailed: String
    val noTextToShow: String
    fun documentOpenFailed(message: Any?): String
    val documentUnreadable: String
    fun movedTo(name: Any?, targetName: Any?): String
    val audioFailed: String
    val pause: String
    val play: String
    val passwordProtected: String
    val enterPassword: String
    fun page(index: Any?): String
    val title: String
    fun passwordBody(name: Any?): String
    val passwordNote: String
    fun percent(progress: Any?): String
    val textTruncated: String
    val videoFailed: String
    val back10: String
    val forward10: String
    val wordTruncated: String
    val imageUnavailable: String
}

/** Çöp kutusu. */
interface TrashStrings {
    val emptied: String
    fun restoredToHome(n: Any?): String
    fun restored(n: Any?): String
    fun deletedPermanently(n: Any?): String
    val emptyAllTitle: String
    fun emptyAllBody(itemCount: Any?): String
    val deletePermanently: String
    fun deleteSelectedTitle(count: Any?): String
    val deleteSelectedBody: String
    val deleteAll: String
    val select: String
    val all: String
    val selectItems: String
    fun selectedCount(count: Any?): String
    val inTrash: String
    val deletedToday: String
    fun daysLeft(it: Any?): String
    val empty: String
    fun itemCount(itemCount: Any?): String
    val folder: String
    fun folderMeta(fileCount: Any?, size: Any?): String
    val folderInTrashNote: String
    fun retentionNote(days: Any?): String
    val today: String
    fun daysShort(daysLeft: Any?): String
    val restore: String
    val folderEmpty: String
    val trashEmpty: String
    val allHandled: String
    fun emptyNote(retentionDays: Any?): String
}

/** Kullanıcı Sözleşmesi penceresi; sözleşmenin metni EulaContent'tedir. */
interface EulaStrings {
    val scrollToAccept: String
    val accept: String
    val linkText: String
    val consentSuffix: String
    val consentPrefix: String
}

/** Dil ayarları. */
interface LanguageStrings {
    val app: String
    val summary: String
    val appDetail: String
    val summaryDetail: String
    val changeA11y: String
    val title: String
    fun mixed(app: Any?, summary: Any?): String
}

/** Telefondaki yapay zeka modeli: indirme, durum ve hata metinleri. */
interface LocalAiStrings {
    val title: String
    fun notDownloaded(size: Any?): String
    fun downloadingShort(percent: Any?): String
    val waitingShort: String
    fun ready(size: Any?): String
    val unsupportedShort: String
    fun intro(model: Any?, size: Any?): String
    fun introHybrid(model: Any?, size: Any?): String
    fun introCloud(model: Any?, size: Any?): String
    val wifiNote: String
    val meteredNote: String
    val download: String
    fun downloadingBody(done: Any?, total: Any?): String
    val waitingBody: String
    val downloadingNote: String
    val cancelDownload: String
    val summarizeInCloud: String
    fun readyBody(model: Any?, size: Any?): String
    fun readyBodyHybrid(model: Any?, size: Any?): String
    fun readyBodyCloud(model: Any?, size: Any?): String
    val deleteModel: String
    fun deleteBody(size: Any?): String
    val deleted: String
    val unsupportedBody: String
    val unsupportedBodyPlus: String
    val unsupportedLocalMode: String
    val downloadTitle: String
    val downloadStarted: String
    val downloaded: String
    val downloadFailed: String
    fun noSpace(size: Any?): String
    val noStorage: String
    val stillDownloading: String
    val notReady: String
    val runFailed: String
}

/** Yapay zeka işlem yeri ayarı. */
interface AiModeStrings {
    val title: String
    val local: String
    val cloud: String
    val hybrid: String
    fun localDetail(size: Any?, pages: Any?): String
    val cloudDetail: String
    fun hybridDetail(size: Any?, pages: Any?): String
    val plusOnly: String
    val cloudPlusOnly: String
    fun localOnlyBlocked(fileName: Any?, reason: Any?): String
}

/** Arama ekranı. */
interface SearchStrings {
    val title: String
    val hint: String
    val clear: String
    val modeAll: String
    val modeName: String
    val modeContent: String
    val prompt: String
    fun noResults(query: Any?): String
    val contentNote: String
}

/** Özetin seslendirilmesi. */
interface SpeechStrings {
    val listen: String
    val pause: String
    fun speed(value: Any?): String
    val preparing: String
    val noEngine: String
    fun noVoice(language: Any?): String
    val installVoice: String
    val failed: String
    val turkish: String
    val english: String
    val channelName: String
    fun notificationText(speed: Any?): String
}
