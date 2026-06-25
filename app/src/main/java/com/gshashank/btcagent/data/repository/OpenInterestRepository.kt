package com.gshashank.btcagent.data.repository

/**
 * Data contract for the Open Interest feature — MOBILE-11.
 */
interface OpenInterestRepository {
    suspend fun fetchOpenInterest(): OpenInterestResult
}
