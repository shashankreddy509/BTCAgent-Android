package com.gshashank.btcagent.data.model

/**
 * Domain model for the Reports screen — MOBILE-7.
 *
 * Stats are computed client-side in [ReportsRepositoryImpl].
 */
data class ReportsData(
    val signalsToday: Int,
    val winRatePct: Double,
    val weekPnl: Double,
    val trades: List<ClosedTrade>,
)

/**
 * A single closed-trade row in the Reports table.
 *
 * [side] reuses [Side] from [Position] — not redefined here.
 * [symbol] is always "BTC/USDT" (hardcoded; not present in backend history rows).
 */
data class ClosedTrade(
    val closedAt: String,
    val side: Side,
    val entryPrice: Double,
    val exitPrice: Double,
    val pnl: Double,
    val pattern: String,
)
