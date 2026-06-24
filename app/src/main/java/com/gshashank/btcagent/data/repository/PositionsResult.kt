package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Position

/**
 * Result type returned by [PositionsRepository.fetchPositions] — MOBILE-6.
 */
sealed class PositionsResult {
    data class Success(val positions: List<Position>) : PositionsResult()
    data class Error(val message: String? = null) : PositionsResult()
}

/**
 * Result type returned by [PositionsRepository.close] and [PositionsRepository.editTpSl] — MOBILE-6.
 */
sealed class ActionResult {
    data object Success : ActionResult()
    data class Error(val code: Int, val message: String) : ActionResult()
}
