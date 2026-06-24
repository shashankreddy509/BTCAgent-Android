package com.gshashank.btcagent.data.repository

/**
 * Contract for position-related operations — MOBILE-6.
 *
 * All methods NEVER throw to callers; errors are returned as sealed result types.
 */
interface PositionsRepository {

    /**
     * Fetches all open positions from the trading state endpoint and computes live P&L
     * client-side (server pnl is null while a position is open).
     */
    suspend fun fetchPositions(): PositionsResult

    /**
     * Closes (cancels) the position identified by [signalId].
     * POST /api/trading/position/{signalId}/cancel
     */
    suspend fun close(signalId: String): ActionResult

    /**
     * Edits the TP/SL values for the position identified by [signalId].
     * POST /api/trading/position/{signalId}/edit — ADMIN-ONLY (403 for non-admins).
     */
    suspend fun editTpSl(signalId: String, sl: Double?, tp: Double?): ActionResult
}
