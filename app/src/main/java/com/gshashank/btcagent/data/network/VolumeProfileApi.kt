package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit API interface for the Volume Profile endpoint — MOBILE-14.
 */
interface VolumeProfileApi {
    @GET("api/volume-profiles")
    suspend fun get(): Response<VolumeProfileDto>
}
