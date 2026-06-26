package com.gshashank.btcagent.data.model

data class TradeMetrics(
    val count: Int,
    val winRatePct: Double,
    val avgWin: Double,
    val avgLoss: Double,
    val expectancy: Double,
    val grossWin: Double,
    val grossLoss: Double,
    val profitFactor: Double?,
    val maxDrawdown: Double,
)
