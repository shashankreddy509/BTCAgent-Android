package com.gshashank.btcagent.data.model

data class DashboardData(
    val btcPrice: Double,
    val priceDirection: PriceDirection,
    val todayPnlPts: Double,
    val openPositionCount: Int,
    val openUnrealisedPnl: Double,
    val botRunning: Boolean,
    val botMode: BotMode,
    /**
     * Epoch-milliseconds of the last WS price tick. Updated on every tick — even when the price
     * is the same as the previous value — so [StateFlow] deduplication never suppresses a tick
     * emission. Defaults to 0L for REST-only state (no tick yet).
     */
    val priceTickMs: Long = 0L,
    val scanIntervalMin: Int = 0,
    val brokerName: String = "Coinbase",
    val longCount: Int = 0,
    val shortCount: Int = 0,
    val positions: List<Position> = emptyList(),
)

enum class PriceDirection { Up, Down, Flat }
enum class BotMode { Live, Paper }
