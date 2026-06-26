package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API interface for the Liquidity Map endpoint — MOBILE-15.
 */
interface LiquidityApi {

    @GET("api/liquidity")
    suspend fun getLiquidity(): Response<LiquidityDto>
}
