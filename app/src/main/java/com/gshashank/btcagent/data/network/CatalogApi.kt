package com.gshashank.btcagent.data.network

import retrofit2.http.GET

/**
 * Retrofit interface for the feature-flag catalog endpoint.
 *
 * `GET /api/catalogs` is PUBLIC — no auth required. The backend ignores any Bearer token
 * that the shared OkHttpClient may attach.
 *
 * Returns a flat map of feature-flag names to their boolean state.
 * A flag absent from the response is treated as OFF (false) by callers — see CatalogRepository.
 */
interface CatalogApi {
    @GET("api/catalogs")
    suspend fun getCatalogs(): Map<String, Boolean>
}
