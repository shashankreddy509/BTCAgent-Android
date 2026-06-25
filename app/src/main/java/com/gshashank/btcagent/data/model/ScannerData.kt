package com.gshashank.btcagent.data.model

data class ScannerData(
    val timestamp: String?,
    val signals: List<ScanSignal>
)

data class ScanSignal(
    val timeframe: String,
    val pattern: String,
    val barsAgo: Int,
    val openPrice: Double,
    val depoLine: Double?,
    val direction: ScanDirection
)

enum class ScanDirection { Bullish, Bearish, Neutral }
