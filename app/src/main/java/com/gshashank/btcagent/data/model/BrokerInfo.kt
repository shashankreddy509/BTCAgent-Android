package com.gshashank.btcagent.data.model

/**
 * Domain model for a configured broker — MOBILE-45.
 *
 * Zero imports from data.network; the DTO-to-domain mapping lives in BrokerRepositoryImpl.
 *
 * [connected]: true = live probe confirmed connected; false = probe ran, disconnected;
 * null = not probed (neutral state, shown as "—" in UI).
 */
data class BrokerInfo(
    val broker: String,
    val accountName: String,
    val apiKeyMasked: String,
    val connected: Boolean?,  // nullable — null = not probed (neutral state)
)
