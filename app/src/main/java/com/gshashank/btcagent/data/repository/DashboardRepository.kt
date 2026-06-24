package com.gshashank.btcagent.data.repository

import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    suspend fun fetchState(): DashboardResult
    fun priceFlow(): Flow<Float>
}
