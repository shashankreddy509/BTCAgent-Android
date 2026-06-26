package com.gshashank.btcagent.data.repository

interface AnalyticsRepository {
    suspend fun fetch(): AnalyticsResult
}
