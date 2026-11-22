package com.eayd.arkive.data

import com.google.gson.annotations.SerializedName

data class MeResponse(
    val id: String,
    val email: String?,
    @SerializedName("used_storage") val usedStorage: Long,
    @SerializedName("plan_name") val planName: String,
    @SerializedName("storage_limit") val storageLimit: Long,
    /** Tek dosyanın en büyük boyutu; plana göre değişir. */
    @SerializedName("max_file_size") val maxFileSize: Long,
    /** Çöp kutusunda saklama süresi (gün). */
    @SerializedName("trash_retention_days") val trashRetentionDays: Int = 30,
    /** Kullanıcının özet sayısı. */
    @SerializedName("summary_count") val summaryCount: Int = 0,
    /** En çok kaç dosya özetlenebilir; Plus'ta null. */
    @SerializedName("summary_limit") val summaryLimit: Int? = null,
    /** Bulutta özetleyebilir mi (Plus). */
    @SerializedName("cloud_summary") val cloudSummary: Boolean = false,
) {
    fun summaryQuota() = SummaryQuota(planName, summaryCount, summaryLimit, cloudSummary)
}

/** Deneme için plan değiştirme isteği: "Plus" ya da "Free". */
data class ChangePlanRequest(val plan: String)

/** Çöp kutusundaki dosya. [daysLeft] kalıcı silinmesine kalan gün. */
data class TrashFileDto(
    val id: String,
    val name: String,
    val size: Long,
    @SerializedName("mime_type") val mimeType: String?,
    @SerializedName("deleted_at") val deletedAt: String,
    @SerializedName("days_left") val daysLeft: Int,
)

/** Çöp kutusundaki klasör. */
data class TrashFolderDto(
    val id: String,
    val name: String,
    val color: String?,
    /** Klasör simgesindeki emoji. */
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

/** Çöpteki bir klasörün içi. */
data class TrashFolderContents(
    @SerializedName("retention_days") val retentionDays: Int,
    val folder: TrashFolderDto,
    val folders: List<TrashFolderDto>,
    val files: List<TrashFileDto>,
)

/** Geri yüklenecek ya da silinecek öğelerin kimlikleri. */
data class TrashIdsRequest(
    @SerializedName("file_ids") val fileIds: List<String>,
    @SerializedName("folder_ids") val folderIds: List<String>,
)

data class TrashCountResponse(val count: Int)

/** Klasörün görüneceği sayfa. */
object FolderLocations {
    const val HOME = "home"
}

data class FolderDto(
    val id: String,
    @SerializedName("parent_id") val parentId: String?,
    val name: String,
    val location: String,
    val color: String?,
    val emoji: String? = null,
    val favorite: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class CreateFolderRequest(
    val name: String,
    @SerializedName("parent_id") val parentId: String? = null,
    val location: String? = null,
    val color: String? = null,
    val emoji: String? = null,
    val favorite: Boolean = false,
)

/** Sunucudaki dosya kaydı. [status]: "uploading", "ready" ya da "failed". */
data class FileDto(
    val id: String,
    @SerializedName("folder_id") val folderId: String?,
    val name: String,
    val size: Long,
    @SerializedName("mime_type") val mimeType: String?,
    val favorite: Boolean,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    /** Dosyanın ya da aynı içerikli bir kopyasının özeti var mı. */
    @SerializedName("has_summary") val hasSummary: Boolean = false,
)

/** Yükleme başlatma isteği: dosyanın adı, boyutu ve türü. */
data class InitUploadRequest(
    val name: String,
    val size: Long,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("folder_id") val folderId: String?,
)

/** Yükleme başlatma yanıtı: dosya kaydı ve depolamaya yükleme adresi. [uploadHeaders] isteğe aynen eklenmeli. */
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

/** Dosyanın kayıtlı özeti. [truncated] dosyanın yalnızca ilk bölümünün özetlendiğini söyler. */
data class SummaryResponse(
    val summary: String,
    val truncated: Boolean = false,
)

/** AI Sonuçlarım sayfasındaki bir özet. */
data class SummaryItemDto(
    val id: String,
    val summary: String,
    val truncated: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    val file: FileDto,
)

/** Bulutta özetleme isteği. [password] saklanmaz; [joinOnly] yalnızca süren işe katılır. */
data class SummarizeRequest(
    val password: String? = null,
    @SerializedName("join_only") val joinOnly: Boolean = false,
    /** Özetin dili ("tr" ya da "en"). */
    val language: String? = null,
)

/** Telefonda çıkan özetin sunucuya kaydı. */
data class SaveSummaryRequest(val summary: String)

/**
 * Arama sonucu: bir dosya ya da klasör. [path] bulunduğu klasörün yolu (kökten). [nameRanges] adda,
 * [snippetRanges] metin parçasında aranan kelimelerin yerleri: [başlangıç, bitiş) çiftleri.
 */
data class SearchHitDto(
    val type: String,
    val file: FileDto? = null,
    val folder: FolderDto? = null,
    val path: List<String> = emptyList(),
    @SerializedName("name_ranges") val nameRanges: List<List<Int>> = emptyList(),
    val snippet: String? = null,
    @SerializedName("snippet_ranges") val snippetRanges: List<List<Int>> = emptyList(),
)
