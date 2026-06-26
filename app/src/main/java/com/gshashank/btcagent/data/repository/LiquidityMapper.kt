package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.HeatTier
import com.gshashank.btcagent.data.model.LiquidityLevel
import com.gshashank.btcagent.data.model.LiquidityMapData
import com.gshashank.btcagent.data.network.LiquidityDto

/**
 * Maps the network [LiquidityDto] to the domain [LiquidityMapData] — MOBILE-15.
 *
 * Lives in the data/repository layer (not data/model) so the domain model stays free of any
 * dependency on the network layer. Repositories call [LiquidityDto.toDomain].
 *
 * All wire fields are strings; rows whose price OR leverage can't be parsed are dropped.
 */
internal fun parsePrice(s: String?): Double? = s?.trim()?.toDoubleOrNull()

internal fun parseLeverage(s: String?): Double? {
    if (s == null) return null
    val clean = s.replace(",", "").trim()
    if (clean.isEmpty()) return null
    val upper = clean.uppercase()
    return when {
        upper.endsWith("K") -> upper.dropLast(1).toDoubleOrNull()?.times(1_000.0)
        upper.endsWith("M") -> upper.dropLast(1).toDoubleOrNull()?.times(1_000_000.0)
        upper.endsWith("B") -> upper.dropLast(1).toDoubleOrNull()?.times(1_000_000_000.0)
        else -> clean.toDoubleOrNull()
    }
}

/**
 * Maps a raw CoinGlass color label to a [HeatTier]. The backend vocabulary (from the scrape's
 * `classify_pixel`) is: YELLOW, LIME, ORANGE, RED, TEAL, NAVY, BLUE, PURPLE, PINK, WHITE, CYAN,
 * GREEN, BLACK, UNKNOWN. Warm colors = dense liquidation zones (HOT/WARM); cool/other = COOL.
 */
internal fun colorToHeat(color: String?): HeatTier = when (color?.uppercase()) {
    "RED", "ORANGE", "PINK" -> HeatTier.HOT
    "YELLOW", "LIME" -> HeatTier.WARM
    else -> HeatTier.COOL // TEAL, NAVY, BLUE, PURPLE, CYAN, GREEN, WHITE, BLACK, UNKNOWN, null
}

internal fun LiquidityDto.toDomain(): LiquidityMapData {
    val levels = rows.mapNotNull { row ->
        val price = parsePrice(row.price) ?: return@mapNotNull null
        val notional = parseLeverage(row.leverage) ?: return@mapNotNull null
        LiquidityLevel(
            price = price,
            tier = colorToHeat(row.color),
            notional = notional,
            timestamp = row.timestamp ?: "",
        )
    }.sortedByDescending { it.price }
    return LiquidityMapData(
        levels = levels,
        // rows are appended newest-last by the backend scrape, so the final raw row carries the
        // most-recent timestamp. This reads the RAW rows, not the price-sorted `levels`.
        lastUpdated = rows.lastOrNull()?.timestamp,
    )
}
