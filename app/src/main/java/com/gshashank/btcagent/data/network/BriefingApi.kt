package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API interface for the morning briefing endpoint — MOBILE-9.
 */
interface BriefingApi {

    @GET("api/brief")
    suspend fun getBriefing(): Response<BriefingDto>
}
