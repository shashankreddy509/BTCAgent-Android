package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for GET /api/liquidity — MOBILE-15.
 *
 * Top-level response payload.
 */
@Serializable
data class LiquidityDto(
    val rows: List<LiquidityRowDto> = emptyList(),
    val status: String? = null,
)

/**
 * DTO for a single liquidity level row.
 */
@Serializable
data class LiquidityRowDto(
    val timestamp: String? = null,
    val color: String? = null,
    @SerialName("y_pixel") val yPixel: String? = null,
    @SerialName("y_range") val yRange: String? = null,
    val leverage: String? = null,
    val price: String? = null,
)
