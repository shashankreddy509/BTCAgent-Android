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

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
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

    @Provides
    @Singleton
    fun provideCatalogApi(retrofit: Retrofit): CatalogApi =
        retrofit.create(CatalogApi::class.java)
}
