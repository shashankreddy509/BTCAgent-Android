package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ExecutionMode

/**
 * Repository interface for user settings — MOBILE-20.
 */
interface SettingsRepository {
    suspend fun fetchUserSettings(): SettingsResult
    suspend fun saveTradingParams(
        qty: Int?,
        maxSl: Double?,
        minTp: Double?,
        maxConcurrent: Int?,
        mode: ExecutionMode?,
    ): ActionResult
}
