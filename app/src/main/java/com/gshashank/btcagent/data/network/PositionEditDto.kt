package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for POST /api/trading/position/{signalId}/edit — MOBILE-6.
 */
@Serializable
data class EditTpSlRequest(
    val sl: Double?,
    val tp: Double?,
)

/**
 * Response from POST /api/trading/position/{signalId}/cancel — MOBILE-6.
 */
@Serializable
data class CancelResponseDto(
    val status: String,
)

/**
 * Response from POST /api/trading/position/{signalId}/edit — MOBILE-6.
 */
@Serializable
data class EditResponseDto(
    @SerialName("signal_id") val signalId: String,
    val sl: Double?,
    val tp: Double?,
)
