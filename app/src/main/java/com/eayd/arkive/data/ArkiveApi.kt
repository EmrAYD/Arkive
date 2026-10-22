package com.eayd.arkive.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backend uçları. Yeni bir uç eklemek = buraya bir satır eklemek.
 * Yollar başında "/" olmadan yazılır (taban adres ApiClient'ta tanımlı).
 * Hepsi suspend: coroutine içinden çağrılır, ağ işi ana iş parçacığını bloklamaz.
 */
interface ArkiveApi {

    @GET("me")
    suspend fun getMe(): MeResponse

    /** Çöp kutusunda saklama süresini değiştirir (1-365 gün). Ayarlar ekranı gelince buradan çağrılacak. */
    @PATCH("me")
    suspend fun updateMe(@Body fields: Map<String, @JvmSuppressWildcards Any?>): MeResponse

    /**
     * Bir seviyedeki klasörler. parentId verilirse o klasörün alt klasörleri,
     * verilmezse `location` sayfasının kök klasörleri gelir. null parametreler isteğe eklenmez.
     */
    @GET("folders")
    suspend fun getFolders(
        @Query("parent_id") parentId: String? = null,
        @Query("location") location: String? = null,
        /** true ise derinliğinden bağımsız tüm favori klasörler gelir (Favorilerim). */
        @Query("favorite") favorite: Boolean? = null,
    ): List<FolderDto>

    @GET("folders/{id}")
    suspend fun getFolder(@Path("id") id: String): FolderDto

    @POST("folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): FolderDto

    /**
     * Kısmi güncelleme: yalnızca map'e konan alanlar değişir ("name", "color", "favorite").
     * Sınıf yerine Map kullanmamızın sebebi şu: "color" için `null` göndermek "rengi sil",
     * hiç göndermemek "rengi değiştirme" demektir. Sınıfta bu iki durumu ayırt edemeyiz.
     */
    @PATCH("folders/{id}")
    suspend fun updateFolder(
        @Path("id") id: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any?>,
    ): FolderDto

    @DELETE("folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String)

    // --- Dosyalar ---------------------------------------------------------------------------------
    // Yükleme 3 adımdır: initUpload → (istemci dosyayı RustFS'e PUT eder, bkz. FileUploader) → completeUpload.

    @POST("files/init")
    suspend fun initUpload(@Body body: InitUploadRequest): InitUploadResponse

    @POST("files/{id}/complete")
    suspend fun completeUpload(@Path("id") id: String): FileDto

    /** Bir seviyedeki dosyalar (folderId yoksa klasörsüzler); favorite=true ise tüm favori dosyalar. */
    @GET("files")
    suspend fun getFiles(
        @Query("folder_id") folderId: String? = null,
        @Query("favorite") favorite: Boolean? = null,
    ): List<FileDto>

    /** Tek dosyanın bilgisi (ön izleme ekranı dosyayı yalnızca id'siyle açar). */
    @GET("files/{id}")
    suspend fun getFile(@Path("id") id: String): FileDto

    @GET("files/{id}/download")
    suspend fun getDownloadUrl(@Path("id") id: String): DownloadUrlResponse

    /** Kısmi güncelleme: "name" ve/veya "favorite". */
    @PATCH("files/{id}")
    suspend fun updateFile(
        @Path("id") id: String,
        @Body fields: Map<String, @JvmSuppressWildcards Any?>,
    ): FileDto

    @DELETE("files/{id}")
    suspend fun deleteFile(@Path("id") id: String)

    // --- Çöp kutusu -------------------------------------------------------------------------------
    // DELETE /files|folders öğeyi çöp kutusuna taşır; kalıcı silme aşağıdaki uçlarla yapılır.

    @GET("trash")
    suspend fun getTrash(): TrashResponse

    /** Çöpteki klasörün içi: klasörle birlikte silinen alt klasörler ve dosyalar. */
    @GET("trash/folders/{id}")
    suspend fun getTrashFolder(@Path("id") id: String): TrashFolderContents

    @POST("trash/restore")
    suspend fun restoreTrash(@Body body: TrashIdsRequest): TrashCountResponse

    /** Seçilenleri KALICI siler (geri alınamaz). */
    @POST("trash/delete")
    suspend fun deleteTrash(@Body body: TrashIdsRequest): TrashCountResponse
}
