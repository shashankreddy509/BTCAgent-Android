package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit interface for the access-gate endpoint.
 *
 * `GET /api/access/check` returns HTTP 200 with an [AccessCheckDto] body for any
 * authenticated user; allow-list membership is the `allowed` field, NOT the status code.
 * (401 means the token is invalid.)
 */
interface AccessApi {
    @GET("api/access/check")
    suspend fun checkAccess(): Response<AccessCheckDto>
}
