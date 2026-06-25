package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for GET /api/trading/oi/native — MOBILE-11.
 *
 * Top-level response payload.
 */
@Serializable
data class OiNativeResponseDto(
    val ok: Boolean = false,
    @SerialName("native") val native: OiNativeDto? = null,
)

/**
 * DTO for the native OI snapshot.
 */
@Serializable
data class OiNativeDto(
    val symbol: String? = null,
    val tf: String? = null,
    val price: Double? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("oi_delta") val oiDelta: Double? = null,
    @SerialName("upper_thresh") val upperThresh: Double? = null,
    @SerialName("lower_thresh") val lowerThresh: Double? = null,
    val signal: String? = null,
    @SerialName("large_oi_up") val largeOiUp: Boolean = false,
    @SerialName("large_oi_dw") val largeOiDw: Boolean = false,
    @SerialName("bar_time") val barTime: Long? = null,
    val history: List<OiBarDto> = emptyList(),
)

/**
 * DTO for a single OI history bar.
 */
@Serializable
data class OiBarDto(
    @SerialName("oi_delta") val oiDelta: Double? = null,
    val signal: String? = null,
    @SerialName("bar_time") val barTime: Long? = null,
)
