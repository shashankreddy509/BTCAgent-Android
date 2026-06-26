package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for Manual Entry endpoints — MOBILE-19.
 *
 * Null fields are omitted from JSON because the shared Json instance uses explicitNulls=false.
 */

@Serializable
data class ManualMarketRequestDto(
    val direction: String,
    val qty: Double,
    val sl: Double,
    val tp: Double? = null,
)

@Serializable
data class ManualLimitRequestDto(
    val direction: String,
    val qty: Double,
    @SerialName("limit_price") val limitPrice: Double,
    val sl: Double,
    val tp: Double? = null,
)

@Serializable
data class ManualOrderResponseDto(
    @SerialName("signal_id") val signalId: String,
    val entry: Double,
    val sl: Double,
    val tp: Double? = null,
    val direction: String,
    val qty: Double,
    val mode: String,
)

@Serializable
data class ManualLimitResponseDto(
    val id: String,
    val direction: String,
    val qty: Double,
    @SerialName("limit_price") val limitPrice: Double,
    val sl: Double,
    val tp: Double? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("placed_on_exchange") val placedOnExchange: Boolean,
    @SerialName("broker_order_id") val brokerOrderId: String? = null,
)

@Serializable
data class CancelPendingResponseDto(
    val status: String,
    val id: String,
)
