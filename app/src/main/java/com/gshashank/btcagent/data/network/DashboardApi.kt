package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

interface DashboardApi {
    @GET("api/trading/state")
    suspend fun getTradingState(): Response<TradingStateDto>
}
