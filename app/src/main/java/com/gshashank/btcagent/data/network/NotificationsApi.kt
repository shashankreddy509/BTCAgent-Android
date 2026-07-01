package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

/**
 * Retrofit API interface for FCM push registration endpoints — MOBILE-41.
 *
 * Uses the AUTHENTICATED Retrofit (NOT @Named("public")) — both endpoints are authed
 * (Firebase bearer) + account allow-list, same host as every other API.
 *
 * POST returns 404 while the backend's global push_notifications toggle is OFF (prod OFF,
 * dev ON) — the repository must treat that as an inert/non-error result, not a hard failure.
 *
 * DELETE uses @HTTP(method="DELETE", hasBody=true) because Retrofit's @DELETE annotation does
 * not support @Body directly.
 */
interface NotificationsApi {

    @POST("api/notifications/register")
    suspend fun register(@Body body: RegisterRequest): Response<StatusResponseDto>

    @HTTP(method = "DELETE", path = "api/notifications/register", hasBody = true)
    suspend fun unregister(@Body body: UnregisterRequest): Response<StatusResponseDto>
}
