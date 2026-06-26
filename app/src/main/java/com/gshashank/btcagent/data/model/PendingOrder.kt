package com.gshashank.btcagent.data.model

/**
 * Domain model for a resting limit order awaiting exchange placement — MOBILE-19.
 *
 * Moved from data.repository to data.model so domain types live in the correct layer.
 */
data class PendingOrder(
    val id: String,
    val direction: Side,
    val qty: Double,
    val limitPrice: Double,
    val sl: Double,
    val tp: Double?,
    val createdAt: String,
)
