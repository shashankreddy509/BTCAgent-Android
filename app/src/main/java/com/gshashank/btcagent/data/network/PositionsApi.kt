package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API interface for position-related endpoints — MOBILE-6.
 *
 * NOTE: [getTradingState] is duplicated from [DashboardApi] so that [PositionsRepositoryImpl]
 * can be constructed with a single API interface (required by the test contract).
 */
interface PositionsApi {

    @GET("api/trading/state")
    suspend fun getTradingState(): Response<TradingStateDto>

    @POST("api/trading/position/{signalId}/cancel")
    suspend fun cancel(@Path("signalId") signalId: String): Response<CancelResponseDto>

    @POST("api/trading/position/{signalId}/edit")
    suspend fun edit(
        @Path("signalId") signalId: String,
        @Body body: EditTpSlRequest,
    ): Response<EditResponseDto>
}
