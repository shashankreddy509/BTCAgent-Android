package com.gshashank.btcagent.di

import com.gshashank.btcagent.BuildConfig
import com.gshashank.btcagent.data.network.AccessApi
import com.gshashank.btcagent.data.network.BriefingApi
import com.gshashank.btcagent.data.network.CatalogApi
import com.gshashank.btcagent.data.network.DashboardApi
import com.gshashank.btcagent.data.network.LiquidityApi
import com.gshashank.btcagent.data.network.MarkovApi
import com.gshashank.btcagent.data.network.OpenInterestApi
import com.gshashank.btcagent.data.network.PositionsApi
import com.gshashank.btcagent.data.network.PriceWebSocketClient
import com.gshashank.btcagent.data.network.RegimeApi
import com.gshashank.btcagent.data.network.ReportsApi
import com.gshashank.btcagent.data.network.ScannerApi
import com.gshashank.btcagent.data.network.TokenAuthenticator
import com.gshashank.btcagent.data.network.AuthInterceptor
import com.gshashank.btcagent.data.network.TradingControlApi
import com.gshashank.btcagent.data.network.VolumeProfileApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
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
            // HEADERS (not BODY) on the AUTHENTICATED client: response bodies carry trading P&L /
            // position history (e.g. /api/trading/reports). At BODY that financial data lands in
            // Logcat, readable by any app with READ_LOGS. Headers-only keeps debugging useful
            // without leaking the payload. (The public client may log BODY — no user data.)
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
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

    @Provides
    @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi =
        retrofit.create(DashboardApi::class.java)

    @Provides
    @Singleton
    fun providePositionsApi(retrofit: Retrofit): PositionsApi =
        retrofit.create(PositionsApi::class.java)

    @Provides
    @Singleton
    fun provideReportsApi(retrofit: Retrofit): ReportsApi =
        retrofit.create(ReportsApi::class.java)

    @Provides
    @Singleton
    fun provideScannerApi(retrofit: Retrofit): ScannerApi =
        retrofit.create(ScannerApi::class.java)

    @Provides
    @Singleton
    fun provideBriefingApi(retrofit: Retrofit): BriefingApi =
        retrofit.create(BriefingApi::class.java)

    @Provides
    @Singleton
    fun provideRegimeApi(retrofit: Retrofit): RegimeApi =
        retrofit.create(RegimeApi::class.java)

    @Provides
    @Singleton
    fun provideOpenInterestApi(retrofit: Retrofit): OpenInterestApi =
        retrofit.create(OpenInterestApi::class.java)

    @Provides
    @Singleton
    fun provideMarkovApi(retrofit: Retrofit): MarkovApi =
        retrofit.create(MarkovApi::class.java)

    @Provides
    @Singleton
    fun provideLiquidityApi(retrofit: Retrofit): LiquidityApi =
        retrofit.create(LiquidityApi::class.java)

    @Provides
    @Singleton
    fun provideVolumeProfileApi(@Named("public") retrofit: Retrofit): VolumeProfileApi =
        retrofit.create(VolumeProfileApi::class.java)

    // TradingControlApi uses the AUTHENTICATED Retrofit — trading endpoints require the Firebase token.
    @Provides
    @Singleton
    fun provideTradingControlApi(retrofit: Retrofit): TradingControlApi =
        retrofit.create(TradingControlApi::class.java)

    /**
     * Provides a system clock lambda for injection.
     * Used by [com.gshashank.btcagent.data.repository.OpenInterestRepositoryImpl] to compute
     * signalAgeMs. Injectable for testability — tests pass a fake clock instead.
     */
    @Provides
    @Singleton
    fun provideSystemClock(): () -> Long = { System.currentTimeMillis() }

    /**
     * Provides [PriceWebSocketClient] with the production WS URL.
     * The [wsUrl] parameter has a default in [PriceWebSocketClient] but Hilt cannot inject
     * default parameters, so we provide it explicitly via this @Provides method.
     */
    @Provides
    @Singleton
    fun providePriceWebSocketClient(
        okHttpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): PriceWebSocketClient =
        PriceWebSocketClient(
            okHttpClient = okHttpClient,
            ioDispatcher = ioDispatcher,
            wsUrl = BuildConfig.WS_URL,
        )
}
