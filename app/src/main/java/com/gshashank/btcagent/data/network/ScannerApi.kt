package com.gshashank.btcagent.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

interface ScannerApi {
    @GET("api/scan")
    suspend fun getScan(): Response<ScanDto>

    @POST("api/scan/trigger")
    suspend fun triggerScan(): Response<TriggerDto>
}
