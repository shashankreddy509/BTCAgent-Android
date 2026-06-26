package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.TradingControlData

/**
 * Result type returned by [TradingControlRepository.fetchState] — MOBILE-18.
 *
 * Write operations (start, stop, setMode, setDepoAlerts, close) reuse [ActionResult]
 * from PositionsResult.kt.
 */
sealed class TradingControlResult {
    data class Success(val data: TradingControlData) : TradingControlResult()
    data class Error(val message: String? = null) : TradingControlResult()
}
