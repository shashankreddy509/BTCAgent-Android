package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API interface for Manual Entry endpoints — MOBILE-19.
 *
 * All endpoints require Bearer token authentication (authenticated Retrofit, not @Named("public")).
 *
 * W4 note: [getTradingState] shares the /api/trading/state endpoint shape with [TradingControlApi]
 * and [PositionsApi] intentionally. [ManualEntryRepositoryImpl] injects [ManualEntryApi] directly
 * to read manual_pending[] from the state response, avoiding a cross-repository dependency on
 * [TradingControlRepository]. This duplication is accepted per code review guidance.
 */
interface ManualEntryApi {

    @POST("api/trading/manual-entry")
    suspend fun placeMarket(@Body request: ManualMarketRequestDto): Response<ManualOrderResponseDto>

    @POST("api/trading/manual-limit")
    suspend fun placeLimit(@Body request: ManualLimitRequestDto): Response<ManualLimitResponseDto>

    @POST("api/trading/manual-pending/{id}/cancel")
    suspend fun cancelPending(@Path("id") id: String): Response<CancelPendingResponseDto>

    @GET("api/trading/state")
    suspend fun getTradingState(): Response<TradingStateDto>
}
