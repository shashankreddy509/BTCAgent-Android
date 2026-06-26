package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Retrofit API interface for user settings endpoints — MOBILE-20.
 *
 * Uses the AUTHENTICATED Retrofit (default, NOT @Named("public")).
 * Paths include full "api/" prefix — a missing prefix 404s in prod.
 */
interface SettingsApi {

    @GET("api/settings/user")
    suspend fun getUserSettings(): Response<UserSettingsDto>

    @PUT("api/settings/user")
    suspend fun saveUserSettings(@Body body: UserSettingsWriteRequest): Response<SaveSettingsResponseDto>
}
