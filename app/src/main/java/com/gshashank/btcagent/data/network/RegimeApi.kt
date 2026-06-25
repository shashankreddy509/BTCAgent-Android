package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API interface for the BTC Regime endpoint — MOBILE-12.
 */
interface RegimeApi {

    @GET("api/regime-log")
    suspend fun getRegimeLog(): Response<RegimeLogDto>
}
