package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.LiquidityMapData

/**
 * Result type returned by [LiquidityRepository.fetch] — MOBILE-15.
 */
sealed class LiquidityResult {
    data class Success(val data: LiquidityMapData) : LiquidityResult()
    object Forbidden : LiquidityResult()
    data class Error(val message: String? = null) : LiquidityResult()
}
