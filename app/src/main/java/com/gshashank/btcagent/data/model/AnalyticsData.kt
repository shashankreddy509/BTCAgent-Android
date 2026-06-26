package com.gshashank.btcagent.data.model

data class AnalyticsData(
    val metrics: TradeMetrics,
    val byPattern: List<PatternMetrics>,
    val equityCurve: List<Double>,
) {
    val isEmpty: Boolean get() = metrics.count == 0
}
