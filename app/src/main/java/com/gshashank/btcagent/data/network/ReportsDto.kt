package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root DTO for GET /api/trading/reports — MOBILE-7.
 *
 * ignoreUnknownKeys is already set globally on the Json instance; the `positions` array
 * present in the real response is intentionally ignored here (not needed for reports).
 */
@Serializable
data class ReportsDto(
    val signals: List<SignalDto> = emptyList(),
    val history: List<HistoryEntryDto> = emptyList(),
)

@Serializable
data class SignalDto(
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class HistoryEntryDto(
    @SerialName("close_price") val closePrice: Double? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("pnl_closed") val pnlClosed: Double? = null,
    val position: HistoryPositionDto? = null,
    @SerialName("close_reason") val closeReason: String? = null,
    // Per-leg quantity closed in THIS event. Double (matches backend float). Used for the
    // qty-weighted points in the canonical win-rate aggregation (group by signal_id).
    @SerialName("qty_closed") val qtyClosed: Double? = null,
    val mode: String? = null,
)

@Serializable
data class HistoryPositionDto(
    // signal_id is the grouping key: partial-TP legs of one trade share it (canonical aggregation).
    @SerialName("signal_id") val signalId: String? = null,
    val direction: String? = null,
    @SerialName("entry_price") val entryPrice: Double? = null,
    // Position qty, used as the leg-qty fallback when qty_closed is absent (matches backend).
    val qty: Double? = null,
    val pattern: String? = null,
)
