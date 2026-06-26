package com.gshashank.btcagent.data.model

/**
 * Domain model for the Liquidity Map feature — MOBILE-15.
 *
 * The DTO→domain mapping (and the string-field parsing helpers) live in
 * `data/repository/LiquidityMapper.kt`, keeping this model free of any network-layer dependency.
 */

/**
 * Liquidation-intensity tier derived from the CoinGlass heatmap color of a level.
 *
 * The backend `color` field is NOT a long/short side — it is CoinGlass's heat scale:
 * warm colors (YELLOW/ORANGE/RED) mark dense "hot" liquidation zones, cool colors
 * (BLUE/TEAL/GREEN/…) mark sparse zones. We collapse the 13 raw labels into three tiers.
 */
enum class HeatTier { HOT, WARM, COOL }

data class LiquidityLevel(
    val price: Double,
    val tier: HeatTier,
    val notional: Double,
    val timestamp: String,
)

data class LiquidityMapData(
    val levels: List<LiquidityLevel>,
    val lastUpdated: String?,
) {
    val isEmpty get() = levels.isEmpty()
    val maxNotional get() = levels.maxOfOrNull { it.notional } ?: 0.0
}
