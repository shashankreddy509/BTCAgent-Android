package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit API interface for admin Users endpoints — MOBILE-21.
 *
 * Uses the AUTHENTICATED Retrofit (default, NOT @Named("public")).
 * Paths include the full "api/admin/" prefix — omitting it causes 404 in production.
 */
interface UsersApi {

    @GET("api/admin/users")
    suspend fun getUsers(): Response<List<AdminUserDto>>

    @GET("api/admin/allowlist")
    suspend fun getAllowlist(): Response<AllowlistDto>

    @PUT("api/admin/allowlist")
    suspend fun putAllowlist(@Body body: AllowlistDto): Response<AllowlistDto>

    @POST("api/admin/users/{uid}/mode")
    suspend fun setMode(
        @Path("uid") uid: String,
        @Body body: SetModeRequest,
    ): Response<AdminStatusResponseDto>

    @POST("api/admin/users/{uid}/stop")
    suspend fun stop(@Path("uid") uid: String): Response<AdminStatusResponseDto>
}
