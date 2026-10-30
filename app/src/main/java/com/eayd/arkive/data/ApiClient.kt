package com.eayd.arkive.data

import com.eayd.arkive.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.GsonBuilder
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiConfig {
    // Adres, projenin kök klasöründeki local.properties dosyasındaki `api.base.url` satırından gelir
    // (app/build.gradle.kts bunu BuildConfig.API_BASE_URL'e çevirir). Satır yoksa emülatör adresi
    // kullanılır. local.properties git'e girmez; herkes kendi adresini yazar.
    val BASE_URL: String = BuildConfig.API_BASE_URL.trimEnd('/')
}

/** Firebase kimlik token'ını (ID token) alır. Ağ iş parçacığında çağrılır, bu yüzden bloklayıcıdır. */
private fun currentIdToken(forceRefresh: Boolean): String {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IOException("Oturum açık değil")
    return try {
        Tasks.await(user.getIdToken(forceRefresh)).token
            ?: throw IOException("Kimlik bilgisi alınamadı")
    } catch (e: ExecutionException) {
        throw IOException("Kimlik bilgisi alınamadı", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("Kimlik bilgisi alınamadı", e)
    }
}

/** Her isteğe `Authorization: Bearer <Firebase token>` başlığını ekler. */
private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentIdToken(forceRefresh = false)
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

/** Sunucu 401 dönerse token'ı zorla yenileyip isteği bir kez daha dener. */
private class TokenRefreshAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Zaten bir kez yeniden denendiyse döngüye girmemek için vazgeç.
        if (response.priorResponse != null) return null
        val token = try {
            currentIdToken(forceRefresh = true)
        } catch (e: IOException) {
            return null
        }
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}

object ApiClient {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .authenticator(TokenRefreshAuthenticator())
        .build()

    /**
     * Dosya YÜKLEMEK ve İNDİRMEK (ön izleme) için ayrı istemci. İki nedenle `http`'ten farklıdır:
     *  1. Bu adresler backend'e değil RustFS'e gider ve adresin kendi imzası vardır. Buraya
     *     `Authorization: Bearer ...` başlığı eklenirse imza bozulur / depolama isteği reddeder,
     *     bu yüzden AuthInterceptor kullanılmaz.
     *  2. Büyük dosya uzun sürer: toplam süre sınırı yok (callTimeout 0); yalnızca "hiç veri akmıyor" durumu
     *     (write/read timeout) hata sayılır.
     */
    val storageHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private fun retrofit(client: OkHttpClient): ArkiveApi =
        Retrofit.Builder()
            .baseUrl("${ApiConfig.BASE_URL}/")   // Retrofit taban adresin "/" ile bitmesini ister
            .client(client)
            // serializeNulls: Map içindeki null değerler de JSON'a yazılsın ("color": null = rengi sil).
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(ArkiveApi::class.java)

    /** Kullanım: `ApiClient.api.getMe()` (bir coroutine içinden). */
    val api: ArkiveApi by lazy { retrofit(http) }

    /**
     * Yapay zeka (bulut özetleme) çağrıları için: aynı oturum/kimlik doğrulama, ama sunucudaki dil modelinin yanıtı
     * dakikalar sürebildiği için okuma süresi uzundur. Yalnızca `summarizeFile` bunu kullanır.
     */
    val aiApi: ArkiveApi by lazy {
        retrofit(http.newBuilder().readTimeout(180, TimeUnit.SECONDS).build())
    }
}
