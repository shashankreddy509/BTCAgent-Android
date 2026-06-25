package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for GET /api/regime-log — MOBILE-12.
 *
 * Top-level response payload.
 */
@Serializable
data class RegimeLogDto(
    val rows: List<RegimeRowDto> = emptyList(),
    val accuracy: Double? = null,
    @SerialName("graded_count") val gradedCount: Int = 0,
    @SerialName("live_regime") val liveRegime: LiveRegimeDto? = null,
)

/**
 * DTO for a single row in the regime log (newest-first from backend).
 */
@Serializable
data class RegimeRowDto(
    val date: String = "",
    @SerialName("predicted_regime") val predictedRegime: String? = null,
    val conviction: Double? = null,
    @SerialName("computed_at") val computedAt: String = "",
    @SerialName("actual_regime") val actualRegime: String? = null,
    val correct: Boolean? = null,
)

/**
 * DTO for the live (today's) regime prediction.
 */
@Serializable
data class LiveRegimeDto(
    val date: String = "",
    val regime: String? = null,
    val conviction: Double? = null,
    @SerialName("computed_at") val computedAt: String = "",
    val error: String? = null,
)
