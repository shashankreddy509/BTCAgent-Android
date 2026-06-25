package com.gshashank.btcagent.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScanDto(
    val timestamp: String? = null,
    val results: List<ScanResultDto> = emptyList()
)

@Serializable
data class ScanResultDto(
    val tf: String? = null,
    val pattern: String? = null,
    @SerialName("bars_ago") val barsAgo: Int? = null,
    @SerialName("bar_open_time") val barOpenTime: String? = null,
    @SerialName("bar_open_price") val barOpenPrice: Double? = null,
    @SerialName("depo_line") val depoLine: Double? = null,
    val timestamp: String? = null
)

@Serializable
data class TriggerDto(
    val status: String? = null
)
