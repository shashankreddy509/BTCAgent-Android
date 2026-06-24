package com.gshashank.btcagent.di

import com.gshashank.btcagent.BuildConfig
import com.gshashank.btcagent.data.network.AccessApi
import com.gshashank.btcagent.data.network.AuthInterceptor
import com.gshashank.btcagent.data.network.CatalogApi
import com.gshashank.btcagent.data.network.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

/**
 * Network graph: JSON config, OkHttp client (with auth interceptor + authenticator), Retrofit,
 * and API interface providers.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true   // buffers minor backend field additions (SDLC 7b)
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Prevent the Firebase ID token from appearing in Logcat.
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            // Auth runs first so the Authorization header is present when logging
            // sees the request — that is what makes redactHeader actually fire.
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .build()
    }

    /**
     * Unauthenticated client for PUBLIC endpoints (e.g. /api/catalogs). Deliberately omits
     * [AuthInterceptor] + [TokenAuthenticator] so the user's Firebase ID token is never attached
     * to a public request that ignores it. Still redacts Authorization in logs as defence in depth.
     */
    @Provides
    @Singleton
    @Named("public")
    fun providePublicOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        buildRetrofit(client, json)

    @Provides
    @Singleton
    @Named("public")
    fun providePublicRetrofit(@Named("public") client: OkHttpClient, json: Json): Retrofit =
        buildRetrofit(client, json)

    private fun buildRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAccessApi(retrofit: Retrofit): AccessApi =
        retrofit.create(AccessApi::class.java)

    // CatalogApi uses the PUBLIC (unauthenticated) Retrofit — /api/catalogs needs no token.
    @Provides
    @Singleton
    fun provideCatalogApi(@Named("public") retrofit: Retrofit): CatalogApi =
        retrofit.create(CatalogApi::class.java)
}
