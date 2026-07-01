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
    @SerialName("broker_account_name") val brokerAccountName: String? = null,
    // 24h price change (BTCWEB-52). Whole object is null when the ticker fetch fails.
    @SerialName("price_24h") val price24h: Price24hDto? = null,
    // MOBILE-41: null until the first scan has run — MUST stay nullable.
    @SerialName("last_scan_time") val lastScanTime: String? = null,
    @SerialName("signals_today") val signalsToday: Int = 0,
)

@Serializable
data class Price24hDto(
    @SerialName("change_usd") val changeUsd: Double,
    @SerialName("change_pct") val changePct: Double,
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
    val pattern: String? = null,
    val tf: Int? = null,
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
    @SerialName("scan_interval_min") val scanIntervalMin: Int = 0,
    // MOBILE-41: the STOPPED branch (scanner not in memory) omits ~50 settings keys including
    // these three — all default to a safe value so parsing never breaks when absent.
    @SerialName("scanner_autostart") val scannerAutostart: Boolean = false,
    @SerialName("push_enabled") val pushEnabled: Boolean = false,
    @SerialName("tf_count") val tfCount: Int = 0,
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
