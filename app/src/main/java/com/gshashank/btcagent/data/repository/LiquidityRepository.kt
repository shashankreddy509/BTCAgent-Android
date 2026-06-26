package com.gshashank.btcagent.data.repository

/**
 * Data contract for the Liquidity Map feature — MOBILE-15.
 */
interface LiquidityRepository {
    suspend fun fetch(): LiquidityResult
}
