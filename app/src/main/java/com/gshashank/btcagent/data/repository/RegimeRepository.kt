package com.gshashank.btcagent.data.repository

/**
 * Data contract for the BTC Regime feature — MOBILE-12.
 */
interface RegimeRepository {
    suspend fun fetchRegime(): RegimeResult
}
