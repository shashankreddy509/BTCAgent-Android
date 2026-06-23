package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Stub Retrofit interface for the access-gate endpoint.
 * Real implementation is wired by NetworkModule; stub exists for compilation only.
 */
interface AccessApi {
    @GET("api/access/check")
    suspend fun checkAccess(): Response<Unit>
}
