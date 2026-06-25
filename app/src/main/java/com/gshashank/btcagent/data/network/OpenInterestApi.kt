package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API interface for the Open Interest endpoint — MOBILE-11.
 */
interface OpenInterestApi {

    @GET("api/trading/oi/native")
    suspend fun getOiNative(@Query("tf") tf: String = "5m"): Response<OiNativeResponseDto>
}
