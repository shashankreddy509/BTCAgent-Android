package com.gshashank.btcagent.data.model

/**
 * Domain model for the Trading Control screen — MOBILE-18.
 *
 * Contains the scanner running state, execution mode, DEPO alerts toggle, and open positions.
 * Zero data.network imports — all mapping lives in TradingControlRepositoryImpl.
 */
data class TradingControlData(
    val running: Boolean,
    val mode: ExecutionMode,
    val depoAlertsEnabled: Boolean,
    val positions: List<Position>,
)

enum class ExecutionMode { PAPER, LIVE }
