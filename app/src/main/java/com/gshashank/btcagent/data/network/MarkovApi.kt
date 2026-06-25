package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API interface for the Markov Matrix endpoint — MOBILE-13.
 */
interface MarkovApi {

    @GET("api/markov/tickers")
    suspend fun getTickers(): Response<MarkovTickersDto>
}
