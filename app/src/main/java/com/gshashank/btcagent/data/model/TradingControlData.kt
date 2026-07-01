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
    // MOBILE-41 additions — mock alignment (scanner card detail + toggle card).
    val lastScanTime: String? = null,
    val signalsToday: Int = 0,
    val scanInterval: Int = 0,
    val tfCount: Int = 0,
    val autostartEnabled: Boolean = false,
    val pushEnabled: Boolean = false,
)

enum class ExecutionMode { PAPER, LIVE }
