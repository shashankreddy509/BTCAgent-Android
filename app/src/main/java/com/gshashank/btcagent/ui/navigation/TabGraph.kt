package com.gshashank.btcagent.ui.navigation

import kotlinx.serialization.Serializable

sealed interface TabGraph {
    @Serializable data object Home : TabGraph
    @Serializable data object Markets : TabGraph
    @Serializable data object Trade : TabGraph
    @Serializable data object Reports : TabGraph
    @Serializable data object Settings : TabGraph
}

sealed interface MarketsRoute {
    @Serializable data object Hub : MarketsRoute
    @Serializable data object OpenInterest : MarketsRoute
    @Serializable data object BtcRegime : MarketsRoute
    @Serializable data object MarkovMatrix : MarketsRoute
    @Serializable data object VolumeProfile : MarketsRoute
    @Serializable data object LiquidityMap : MarketsRoute
    @Serializable data object ZoneStrategies : MarketsRoute
    @Serializable data object Analytics : MarketsRoute
    @Serializable data object MorningBriefing : MarketsRoute
    @Serializable data object Scanner : MarketsRoute
}

// Inner tab start destinations (graph-level only, not top-level routes)
sealed interface HomeTab {
    @Serializable data object Hub : HomeTab
    @Serializable data object Positions : HomeTab
    @Serializable data class PositionDetail(val signalId: String) : HomeTab
}

sealed interface TradeTab {
    @Serializable data object Hub : TradeTab
}

sealed interface ReportsTab {
    @Serializable data object Hub : ReportsTab
}

sealed interface SettingsTab {
    @Serializable data object Hub : SettingsTab
}
