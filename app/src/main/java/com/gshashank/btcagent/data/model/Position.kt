package com.gshashank.btcagent.data.model

/**
 * Domain model for a single open trading position — MOBILE-6.
 *
 * [qty] is a Double because the backend sends fractional quantities (e.g. 0.5 contracts).
 * [pnl] and [pnlPct] are computed client-side by [PositionsRepositoryImpl]; the server
 * field is null while the position is open.
 *
 * [pattern] is the signal pattern label (e.g. "4-Flag"); null when absent from the API.
 * [tf] is the timeframe in minutes as a bare integer (e.g. 240 = 4H); null when absent.
 * No data.network imports are allowed here — both are primitive types (String?, Int?).
 */
data class Position(
    val signalId: String,
    val side: Side,
    val entryPrice: Double,
    val currentPrice: Double,
    val qty: Double,
    val sl: Double?,
    val tp: Double?,
    val status: String,
    val openedAt: String,
    val pnl: Double,
    val pnlPct: Double,
    val contractSize: Double,
    val pattern: String? = null,
    val tf: Int? = null,
)

enum class Side { Long, Short }
