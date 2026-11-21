package com.eayd.arkive.data

import com.google.gson.annotations.SerializedName

// Sunucunun döndürdüğü / beklediği JSON'a karşılık gelen sınıflar (DTO = Data Transfer Object).
// Alan adı sunucudakinden farklıysa @SerializedName ile eşlenir (sunucu snake_case, Kotlin camelCase kullanır).
// Sunucunun null dönebileceği alanlar Kotlin'de "?" ile işaretlenir.

data class MeResponse(
    val id: String,
    val email: String?,
    @SerializedName("used_storage") val usedStorage: Long,
    @SerializedName("plan_name") val planName: String,
    @SerializedName("storage_limit") val storageLimit: Long,
    /** Tek bir dosyanın en büyük boyutu (bayt); plana göre değişir. */
    @SerializedName("max_file_size") val maxFileSize: Long,
    /** Çöp kutusunda saklama süresi (gün). */
    @SerializedName("trash_retention_days") val trashRetentionDays: Int = 30,
    /** Kullanıcının tamamlanmış yapay zeka özeti sayısı. */
    @SerializedName("summary_count") val summaryCount: Int = 0,
    /** Plana göre en çok kaç dosya özetlenebilir; null: sınırsız (Plus). */
    @SerializedName("summary_limit") val summaryLimit: Int? = null,
    /** Bulutta (sunucudaki yapay zekayla) özetleyebilir mi (Plus). */
    @SerializedName("cloud_summary") val cloudSummary: Boolean = false,
) {
    fun summaryQuota() = SummaryQuota(planName, summaryCount, summaryLimit, cloudSummary)
}

/** POST /me/plan gövdesi (deneme için plan değiştirme): "Plus" ya da "Free". */
data class ChangePlanRequest(val plan: String)

/** Çöp kutusundaki dosya. [daysLeft]: kalıcı silinmesine kalan gün (sunucu hesaplar). */
data class TrashFileDto(
    val id: String,
    val name: String,
    val size: Long,
    @SerializedName("mime_type") val mimeType: String?,
    @SerializedName("deleted_at") val deletedAt: String,
    @SerializedName("days_left") val daysLeft: Int,
)

/** Çöp kutusundaki klasör; [fileCount]/[size] içindeki hazır dosyaların sayısı ve toplam boyutu. */
data class TrashFolderDto(
    val id: String,
    val name: String,
    val color: String?,
    /** Klasör simgesinin ortasındaki emoji; yoksa null. */
    val emoji: String? = null,
    @SerializedName("file_count") val fileCount: Int,
    val size: Long,
    @SerializedName("deleted_at") val deletedAt: String,
    @SerializedName("days_left") val daysLeft: Int,
)

data class TrashResponse(
    @SerializedName("retention_days") val retentionDays: Int,
    val files: List<TrashFileDto>,
    val folders: List<TrashFolderDto>,
)

/** Çöpteki bir klasörün içi (salt okunur). */
data class TrashFolderContents(
    @SerializedName("retention_days") val retentionDays: Int,
    val folder: TrashFolderDto,
    val folders: List<TrashFolderDto>,
    val files: List<TrashFileDto>,
)

/** Geri yükle / kalıcı sil istekleri için seçilen kimlikler. */
data class TrashIdsRequest(
    @SerializedName("file_ids") val fileIds: List<String>,
    @SerializedName("folder_ids") val folderIds: List<String>,
)

data class TrashCountResponse(val count: Int)

/**
 * Klasörün görüneceği sayfa (sunucudaki Folders.Location). Klasörler yalnızca ana sayfadadır; AI Sonuçlarım sayfası
 * klasör değil, özetlerin listesidir ([SummaryItemDto]).
 */
object FolderLocations {
    const val HOME = "home"
}

data class FolderDto(
    val id: String,
    @SerializedName("parent_id") val parentId: String?,
    val name: String,
    val location: String,
    val color: String?,       // "#RRGGBB" ya da null (renk yok)
    val emoji: String? = null,   // simgenin ortasındaki emoji ya da null (emoji yok)
    val favorite: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class CreateFolderRequest(
    val name: String,
    @SerializedName("parent_id") val parentId: String? = null,
    val location: String? = null,   // yalnızca kök klasörde gönderilir; alt klasör üstünden devralır
    val color: String? = null,
    val emoji: String? = null,
    val favorite: Boolean = false,
)

/** Sunucudaki dosya kaydı. `status`: "uploading" (yükleniyor), "ready" (hazır), "failed" (başarısız). */
data class FileDto(
    val id: String,
    @SerializedName("folder_id") val folderId: String?,
    val name: String,
    val size: Long,
    @SerializedName("mime_type") val mimeType: String?,
    val favorite: Boolean,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    /** Dosyanın (ya da aynı içerikli bir kopyasının) yapay zeka özeti var mı; AI düğmesi buna göre dolu görünür. */
    @SerializedName("has_summary") val hasSummary: Boolean = false,
)

/** Yüklemeyi başlatırken gönderilen bilgi: dosyanın kendisi değil, yalnızca adı/boyutu/türü. */
data class InitUploadRequest(
    val name: String,
    val size: Long,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("folder_id") val folderId: String?,
)

/**
 * Sunucunun yükleme başlatma yanıtı: oluşturulan kayıt + dosyanın doğrudan RustFS'e yükleneceği
 * imzalı adres. [uploadHeaders] yükleme isteğine AYNEN eklenmelidir (imza bunlara göre atıldı).
 */
data class InitUploadResponse(
    val file: FileDto,
    @SerializedName("upload_url") val uploadUrl: String,
    @SerializedName("upload_headers") val uploadHeaders: Map<String, String>,
    @SerializedName("expires_in") val expiresIn: Int,
)

data class DownloadUrlResponse(
    val url: String,
    @SerializedName("expires_in") val expiresIn: Int,
)

/**
 * Dosyanın kayıtlı yapay zeka özeti (GET/POST/PUT /files/{id}/summary): belgenin ne olduğunu ve neleri içerdiğini
 * anlatan tek paragraf. [truncated]: dosya çok uzun olduğu için yalnızca ilk bölümü özetlendi (sunucu bunu özetin
 * sonuna not olarak da yazar).
 */
data class SummaryResponse(
    val summary: String,
    val truncated: Boolean = false,
)

/**
 * AI Sonuçlarım sayfasındaki bir özet (GET /summaries). [file]: özetin kaynak dosyası; öğe bu dosyanın adıyla
 * ("rapor.pdf özeti") gösterilir.
 */
data class SummaryItemDto(
    val id: String,
    val summary: String,
    val truncated: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    val file: FileDto,
)

/**
 * Dosyanın bulutta özetlenmesi (POST /files/{id}/summary). [password]: yalnızca parolalı dosyada; bu istekte gider,
 * sunucu saklamaz. [joinOnly]: yalnızca sunucuda süren özetlemeye katıl (uygulama yeniden açılınca); süren iş ya da
 * kayıtlı özet yoksa sunucu yenisini başlatmaz, 404 döner.
 */
data class SummarizeRequest(
    val password: String? = null,
    @SerializedName("join_only") val joinOnly: Boolean = false,
    /** Özetin yazılacağı dil ("tr" / "en"); bkz. [com.eayd.arkive.data.AppSettings.summaryLanguage]. */
    val language: String? = null,
)

/** Telefonda (cihazdaki yapay zekayla) üretilen özetin sunucuya kaydı (PUT /files/{id}/summary). */
data class SaveSummaryRequest(val summary: String)
