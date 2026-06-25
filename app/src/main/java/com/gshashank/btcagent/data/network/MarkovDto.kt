package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for GET /api/markov/tickers — MOBILE-13.
 *
 * Top-level response payload.
 */
@Serializable
data class MarkovTickersDto(
    val tickers: List<MarkovTickerDto> = emptyList(),
    @SerialName("last_refresh") val lastRefresh: String? = null,
)

/**
 * DTO for a single ticker's Markov regime data.
 */
@Serializable
data class MarkovTickerDto(
    val ticker: String = "",
    val market: String = "",
    val date: String = "",
    val regime: String = "",
    val conviction: Double? = null,
    val stationary: List<Double> = emptyList(),
    val error: String? = null,
    val accuracy: Double? = null,
    @SerialName("graded_count") val gradedCount: Int = 0,
)
