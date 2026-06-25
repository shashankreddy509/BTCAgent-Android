package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API interface for the reports endpoint — MOBILE-7.
 */
interface ReportsApi {

    @GET("api/trading/reports")
    suspend fun getReports(): Response<ReportsDto>
}
