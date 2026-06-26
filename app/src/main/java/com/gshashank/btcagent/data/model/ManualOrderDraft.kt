package com.gshashank.btcagent.data.model

/**
 * Domain model representing a user-composed order before it is submitted — MOBILE-19.
 *
 * Moved from data.repository to data.model so domain types live in the correct layer.
 */
enum class OrderType { MARKET, LIMIT }

data class ManualOrderDraft(
    val direction: Side,
    val orderType: OrderType,
    val qty: Double,
    val limitPrice: Double?,
    val sl: Double,
    val tp: Double?,
)
