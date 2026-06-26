package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API interface for Trading Control endpoints — MOBILE-18.
 *
 * [getTradingState] reads GET /api/trading/state (same as PositionsApi but owned here so
 * [TradingControlRepositoryImpl] can be constructed without injecting PositionsApi).
 * [cancel] delegates to the same endpoint as [PositionsApi.cancel] without reusing that interface,
 * keeping [TradingControlRepositoryImpl]'s constructor to a single API dependency.
 */
interface TradingControlApi {

    @GET("api/trading/state")
    suspend fun getTradingState(): Response<TradingStateDto>

    @POST("api/trading/start")
    suspend fun start(): Response<StatusResponseDto>

    @POST("api/trading/stop")
    suspend fun stop(): Response<StatusResponseDto>

    @POST("api/trading/settings")
    suspend fun setSettings(@Body body: SettingsWriteRequest): Response<SetSettingsResponseDto>

    @POST("api/trading/position/{signalId}/cancel")
    suspend fun cancel(@Path("signalId") signalId: String): Response<CancelResponseDto>
}
