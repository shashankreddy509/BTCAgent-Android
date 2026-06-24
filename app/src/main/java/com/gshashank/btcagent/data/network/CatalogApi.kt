package com.gshashank.btcagent.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Catalog feature-flag API.
 * PUBLIC endpoint — no auth required.
 */
interface CatalogApi {
    @GET("api/catalogs")
    suspend fun getCatalogs(
        @Query("platform") platform: Int,
        @Query("version") version: Int,
    ): CatalogResponse
}
