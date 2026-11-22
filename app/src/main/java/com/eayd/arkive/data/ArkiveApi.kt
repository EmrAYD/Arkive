package com.eayd.arkive.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Backend uçları. */
interface ArkiveApi {

    @GET("me")
    suspend fun getMe(): MeResponse

    /** Profil ayarlarını kısmen günceller (ör. çöp kutusu saklama süresi). */
    @PATCH("me")
    suspend fun updateMe(@Body fields: Map<String, @JvmSuppressWildcards Any?>): MeResponse

    /** Deneme için ödemesiz plan değiştirme. */
    @POST("me/plan")
    suspend fun changePlan(@Body body: ChangePlanRequest): MeResponse

    /** Bir klasörün alt klasörleri; [parentId] yoksa sayfanın kök klasörleri. */
    @GET("folders")
    suspend fun getFolders(
        @Query("parent_id") parentId: String? = null,
        @Query("location") location: String? = null,
        /** true ise tüm favori klasörler gelir. */
        @Query("favorite") favorite: Boolean? = null,
    ): List<FolderDto>

    @GET("folders/{id}")
    suspend fun getFolder(@Path("id") id: String): FolderDto

    @POST("folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): FolderDto

    /**
     * Kısmi güncelleme. "color" için null göndermek rengi siler, hiç göndermemek değiştirmez; bu yüzden Map kullanılır.
     */
    @PATCH("folders/{id}")
    suspend fun updateFolder(
        @Path("id") id: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any?>,
    ): FolderDto

    @DELETE("folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String)

    @POST("files/init")
    suspend fun initUpload(@Body body: InitUploadRequest): InitUploadResponse

    @POST("files/{id}/complete")
    suspend fun completeUpload(@Path("id") id: String): FileDto

    /** Bir klasördeki dosyalar; [favorite] true ise tüm favori dosyalar. */
    @GET("files")
    suspend fun getFiles(
        @Query("folder_id") folderId: String? = null,
        @Query("favorite") favorite: Boolean? = null,
    ): List<FileDto>

    /** Tek dosyanın bilgisi. */
    @GET("files/{id}")
    suspend fun getFile(@Path("id") id: String): FileDto

    @GET("files/{id}/download")
    suspend fun getDownloadUrl(@Path("id") id: String): DownloadUrlResponse

    /** Kısmi güncelleme: "name" ve "favorite". */
    @PATCH("files/{id}")
    suspend fun updateFile(
        @Path("id") id: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any?>,
    ): FileDto

    @DELETE("files/{id}")
    suspend fun deleteFile(@Path("id") id: String)

    /** Dosya ve klasör adlarında, dosyaların içinde arar. [mode]: "all", "name" ya da "content". */
    @GET("search")
    suspend fun search(@Query("q") query: String, @Query("mode") mode: String): List<SearchHitDto>

    /** Tamamlanmış özetler, en yenisi önce. */
    @GET("summaries")
    suspend fun getSummaries(): List<SummaryItemDto>

    /** Kayıtlı özet; yoksa 404. */
    @GET("files/{id}/summary")
    suspend fun getSummary(@Path("id") id: String): SummaryResponse

    /** Dosyayı sunucuda özetler. Uzun sürebildiği için [ApiClient.aiApi] ile çağrılır. */
    @POST("files/{id}/summary")
    suspend fun summarizeFile(@Path("id") id: String, @Body body: SummarizeRequest): SummaryResponse

    /** Sunucuda süren bulut özetini durdurur. */
    @POST("files/{id}/summary/cancel")
    suspend fun cancelSummary(@Path("id") id: String)

    /** Telefonda çıkan özeti kaydeder; özet zaten varsa mevcut olan döner. */
    @PUT("files/{id}/summary")
    suspend fun saveSummary(@Path("id") id: String, @Body body: SaveSummaryRequest): SummaryResponse

    /** Dosyanın ve aynı içerikli kopyalarının özetini siler. */
    @DELETE("files/{id}/summary")
    suspend fun deleteSummary(@Path("id") id: String)

    @GET("trash")
    suspend fun getTrash(): TrashResponse

    /** Çöpteki klasörün içi. */
    @GET("trash/folders/{id}")
    suspend fun getTrashFolder(@Path("id") id: String): TrashFolderContents

    @POST("trash/restore")
    suspend fun restoreTrash(@Body body: TrashIdsRequest): TrashCountResponse

    /** Seçilenleri kalıcı olarak siler. */
    @POST("trash/delete")
    suspend fun deleteTrash(@Body body: TrashIdsRequest): TrashCountResponse

    /** Çöp kutusunu boşaltır. */
    @DELETE("trash")
    suspend fun emptyTrash(): TrashCountResponse
}
