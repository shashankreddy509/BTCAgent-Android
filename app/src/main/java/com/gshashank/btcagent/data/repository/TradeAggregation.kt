package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.PatternMetrics
import com.gshashank.btcagent.data.model.TradeMetrics
import com.gshashank.btcagent.data.model.TradePoint
import com.gshashank.btcagent.data.network.HistoryEntryDto

/**
 * Direction-aware per-leg points (mirrors backend analysis._leg_points).
 * long  → close - entry
 * short → entry - close
 * else  → null (unknown direction: exclude the leg)
 */
internal fun legPoints(direction: String?, entry: Double?, close: Double?): Double? {
    if (entry == null || close == null) return null
    return when (direction?.lowercase()) {
        "long" -> close - entry
        "short" -> entry - close
        else -> null // unknown/absent direction: exclude from the trade
    }
}

/**
 * Groups history legs by signal_id (fallback: closedAt, then "keyless_$index") and computes
 * one [TradePoint] per logical trade using a qty-weighted average of per-leg points.
 *
 * Mirrors the canonical backend analysis.aggregate_trades + _leg_points logic.
 *
 * Weight per leg = qty_closed ?: position.qty ?: 1.0 (0.0 treated as 1.0).
 * A trade whose legs yield no computable points (qsum == 0.0) is excluded (returns null → filtered).
 * pattern is taken from position.pattern (default "—" when absent/empty).
 */
internal fun aggregateTrades(history: List<HistoryEntryDto>): List<TradePoint> {
    return history
        .mapIndexed { index, row ->
            val key = row.position?.signalId ?: row.closedAt ?: "keyless_$index"
            key to row
        }
        .groupBy({ it.first }, { it.second })
        .values
        .mapNotNull { legs ->
            var wsum = 0.0
            var qsum = 0.0
            var pnlSum = 0.0
            var pattern = "—"
            for (leg in legs) {
                val pos = leg.position
                val pts = legPoints(pos?.direction, pos?.entryPrice, leg.closePrice) ?: continue
                val q = (leg.qtyClosed ?: pos?.qty ?: 1.0).let { if (it == 0.0) 1.0 else it }
                wsum += pts * q
                qsum += q
                pnlSum += leg.pnlClosed ?: 0.0
                // Take pattern from position; use last non-empty value across legs
                val legPattern = pos?.pattern
                if (!legPattern.isNullOrEmpty()) pattern = legPattern
            }
            if (qsum == 0.0) null // no computable points → excluded from n
            else TradePoint(points = wsum / qsum, pnl = pnlSum, pattern = pattern)
        }
}

/**
 * Computes [TradeMetrics] over a list of per-trade points values (one Double per trade).
 *
 * Canonical formulas:
 * - n            = points.size (all entries count; null already filtered by aggregateTrades)
 * - wins         = count { it > 0 } (breakeven == 0 is in n but NOT a win)
 * - winRatePct   = wins / n * 100.0 (percent); 0 if n == 0
 * - avgWin       = mean of { it > 0 }; 0.0 if none
 * - avgLoss      = mean of { it < 0 }; 0.0 (negative value) if none
 * - lossRate     = count { it < 0 } / n
 * - expectancy   = winRate * avgWin + lossRate * avgLoss  (avgLoss already negative)
 * - grossWin     = sum of { it > 0 }
 * - grossLoss    = -sum of { it < 0 }  (positive magnitude)
 * - profitFactor = grossWin / grossLoss; null when grossLoss == 0 (∞)
 * - maxDrawdown  = peak-to-trough on cumulative curve; >= 0
 */
internal fun computeMetrics(points: List<Double>): TradeMetrics {
    val n = points.size
    if (n == 0) {
        return TradeMetrics(
            count = 0,
            winRatePct = 0.0,
            avgWin = 0.0,
            avgLoss = 0.0,
            expectancy = 0.0,
            grossWin = 0.0,
            grossLoss = 0.0,
            profitFactor = null,
            maxDrawdown = 0.0,
        )
    }

    val wins = points.filter { it > 0.0 }
    val losses = points.filter { it < 0.0 }

    val winCount = wins.size
    val lossCount = losses.size

    val winRate = winCount.toDouble() / n
    val lossRate = lossCount.toDouble() / n

    val avgWin = if (wins.isEmpty()) 0.0 else wins.average()
    val avgLoss = if (losses.isEmpty()) 0.0 else losses.average() // negative value

    val expectancy = winRate * avgWin + lossRate * avgLoss

    val grossWin = wins.sum()
    val grossLoss = -losses.sum() // positive magnitude

    val profitFactor = if (grossLoss == 0.0) null else grossWin / grossLoss

    // Peak-to-trough max drawdown on cumulative-points curve
    var cum = 0.0
    var peak = 0.0
    var maxDrawdown = 0.0
    for (p in points) {
        cum += p
        if (cum > peak) peak = cum
        val dd = peak - cum
        if (dd > maxDrawdown) maxDrawdown = dd
    }

    return TradeMetrics(
        count = n,
        winRatePct = winRate * 100.0,
        avgWin = avgWin,
        avgLoss = avgLoss,
        expectancy = expectancy,
        grossWin = grossWin,
        grossLoss = grossLoss,
        profitFactor = profitFactor,
        maxDrawdown = maxDrawdown,
    )
}

/**
 * Groups [TradePoint]s by pattern (empty/blank → "—"), computes [TradeMetrics] per group,
 * and sorts by winRatePct DESC (ties broken by count DESC).
 */
internal fun metricsByPattern(trades: List<TradePoint>): List<PatternMetrics> {
    return trades
        .groupBy { trade -> trade.pattern.ifEmpty { "—" } }
        .map { (pattern, group) ->
            PatternMetrics(
                pattern = pattern,
                metrics = computeMetrics(group.map { it.points }),
            )
        }
        .sortedWith(
            compareByDescending<PatternMetrics> { it.metrics.winRatePct }
                .thenByDescending { it.metrics.count },
        )
}
