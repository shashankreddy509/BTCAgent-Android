package com.gshashank.btcagent.data.repository

/**
 * Repository interface for broker settings — MOBILE-45.
 */
interface BrokerRepository {
    suspend fun fetchBroker(): BrokerResult
    suspend fun replaceBroker(broker: String, apiKey: String, apiSecret: String): BrokerActionResult
}
