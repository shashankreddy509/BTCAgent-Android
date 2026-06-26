package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TradingStateDto(
    val running: Boolean,
    val positions: List<PositionDto> = emptyList(),
    val history: List<TradeResultDto> = emptyList(),
    val settings: SettingsDto,
    @SerialName("current_price") val currentPrice: Double = 0.0,
    @SerialName("manual_pending") val manualPending: List<ManualPendingDto> = emptyList(),
)

@Serializable
data class PositionDto(
    @SerialName("signal_id") val signalId: String? = null,
    @SerialName("entry_price") val entryPrice: Double? = null,
    val direction: String? = null,
    val status: String? = null,
    val mode: String? = null,
    val pnl: Double? = null,
    val sl: Double? = null,
    val tp: Double? = null,
    val qty: Double? = null,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("contract_size") val contractSize: Double? = null,
)

@Serializable
data class TradeResultDto(
    @SerialName("pnl_closed") val pnlClosed: Double? = null,
    @SerialName("closed_at") val closedAt: String? = null,
)

@Serializable
data class SettingsDto(
    val mode: String = "paper",
    @SerialName("depo_entry_filter") val depoEntryFilter: Boolean = false,
)

@Serializable
data class ManualPendingDto(
    val id: String,
    val direction: String,
    val qty: Double,
    @SerialName("limit_price") val limitPrice: Double,
    val sl: Double,
    val tp: Double? = null,
    @SerialName("created_at") val createdAt: String,
)
