package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT

/**
 * Retrofit API interface for broker settings endpoints — MOBILE-45.
 *
 * Uses the AUTHENTICATED Retrofit (NOT @Named("public")).
 * Paths include full "api/" prefix.
 *
 * The PUT body is a [Map] with dynamic keys (broker name is a key prefix), so a typed DTO
 * cannot capture the shape. [JvmSuppressWildcards] prevents Kotlin's use-site variance from
 * wrapping the value type as a wildcard, which would prevent the kotlinx.serialization converter
 * from resolving the correct serializer at runtime.
 */
interface BrokerApi {

    @GET("api/settings/broker")
    suspend fun getBroker(): Response<BrokerSummaryDto>

    @PUT("api/settings/broker")
    @Headers("Content-Type: application/json")
    suspend fun saveBroker(@Body body: @JvmSuppressWildcards Map<String, String>): Response<BrokerSaveResponseDto>
}
