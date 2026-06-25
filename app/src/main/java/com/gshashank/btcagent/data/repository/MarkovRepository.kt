package com.gshashank.btcagent.data.repository

/**
 * Data contract for the Markov Matrix feature — MOBILE-13.
 */
interface MarkovRepository {
    suspend fun fetchTickers(): MarkovResult
}
