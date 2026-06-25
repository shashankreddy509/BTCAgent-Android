package com.gshashank.btcagent.data.repository

/**
 * Data contract for the Reports feature — MOBILE-7.
 */
interface ReportsRepository {
    suspend fun fetchReports(): ReportsResult
}
