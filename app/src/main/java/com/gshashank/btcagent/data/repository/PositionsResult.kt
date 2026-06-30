package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Position

/**
 * Result type returned by [PositionsRepository.fetchPositions] — MOBILE-6, MOBILE-43.
 *
 * [Success.todayPnl] is computed by the repository from history[] entries closed today
 * in the device timezone (same logic as DashboardRepositoryImpl).
 * [Success.mode] reflects settings.mode ("paper" or "live") from the API response.
 * Default values preserve backwards-compatibility with existing construction sites.
 */
sealed class PositionsResult {
    data class Success(
        val positions: List<Position>,
        val todayPnl: Double = 0.0,
        val mode: String = "paper",
    ) : PositionsResult()
    data class Error(val message: String? = null) : PositionsResult()
}

/**
 * Result type returned by [PositionsRepository.close] and [PositionsRepository.editTpSl] — MOBILE-6.
 */
sealed class ActionResult {
    data object Success : ActionResult()
    data class Error(val code: Int, val message: String) : ActionResult()
}
