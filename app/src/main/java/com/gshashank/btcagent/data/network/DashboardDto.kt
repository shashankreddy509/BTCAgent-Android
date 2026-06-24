package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TradingStateDto(
    val running: Boolean,
    val positions: List<PositionDto> = emptyList(),
    val history: List<TradeResultDto> = emptyList(),
    val settings: SettingsDto,
)

@Serializable
data class PositionDto(
    @SerialName("signal_id") val signalId: String? = null,
    @SerialName("entry_price") val entryPrice: Double? = null,
    val direction: String? = null,
    val status: String? = null,
    val mode: String? = null,
    val pnl: Double? = null,
)

@Serializable
data class TradeResultDto(
    @SerialName("pnl_closed") val pnlClosed: Double? = null,
    @SerialName("closed_at") val closedAt: String? = null,
)

@Serializable
data class SettingsDto(
    val mode: String = "paper",
)
