package com.eayd.arkive.data

import com.eayd.arkive.BuildConfig
import com.eayd.arkive.i18n.Lang
import com.eayd.arkive.i18n.Txt
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
    /** Sunucu adresi. local.properties'teki api.base.url satırından gelir; satır yoksa emülatör adresi kullanılır. */
    val BASE_URL: String = BuildConfig.API_BASE_URL.trimEnd('/')
}

/** Firebase kimlik token'ını alır. Ağ iş parçacığında çağrılır, bloklar. */
private fun currentIdToken(forceRefresh: Boolean): String {
    val user = FirebaseAuth.getInstance().currentUser
        ?: throw IOException(Txt.errors.notSignedIn)
    return try {
        Tasks.await(user.getIdToken(forceRefresh)).token
            ?: throw IOException(Txt.errors.credentialsUnavailable)
    } catch (e: ExecutionException) {
        throw IOException(Txt.errors.credentialsUnavailable, e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException(Txt.errors.credentialsUnavailable, e)
    }
}

/** Her isteğe Firebase token'ını ve uygulamanın dilini ekler; sunucu mesajları bu dilde döner. */
private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentIdToken(forceRefresh = false)
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept-Language", Lang.app.tag)
            .build()
        return chain.proceed(request)
    }
}

/** Sunucu 401 dönerse token'ı yenileyip isteği bir kez daha dener. */
private class TokenRefreshAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
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
     * Depolamaya (RustFS) giden yükleme ve indirmeler için ayrı istemci.
     *
     * Bu adresler kendi imzasını taşır; Authorization başlığı eklenmez.
     */
    val storageHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private fun retrofit(client: OkHttpClient): ArkiveApi =
        Retrofit.Builder()
            .baseUrl("${ApiConfig.BASE_URL}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(ArkiveApi::class.java)

    val api: ArkiveApi by lazy { retrofit(http) }

    /** Bulut özetleme için istemci; yanıt dakikalar sürebildiğinden okuma süresi uzun. */
    val aiApi: ArkiveApi by lazy {
        retrofit(http.newBuilder().readTimeout(180, TimeUnit.SECONDS).build())
    }
}
