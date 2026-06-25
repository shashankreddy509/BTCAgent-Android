package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ClosedTrade
import com.gshashank.btcagent.data.model.ReportsData
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.network.ReportsApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [ReportsRepository] — MOBILE-7.
 *
 * Calls [ReportsApi.getReports] and maps the DTO to [ReportsData], computing three stats
 * client-side:
 *
 * 1. signalsToday: count of signals[] whose created_at UTC Instant, converted to device
 *    timezone, falls on today's LOCAL date.
 *
 * 2. winRatePct: replicates the backend CANONICAL win-rate (analysis.aggregate_trades +
 *    _leg_points + _metrics) so the mobile number matches the server's. Per TRADE, not per leg:
 *    group history legs by position.signal_id; per trade points = qty-weighted avg of leg points
 *    (direction-aware: long close-entry, short entry-close; weighted by qty_closed, fallback
 *    position.qty, then 1.0); trade counts only if it has computable points (denominator n);
 *    win = points > 0; breakeven (==0) is in n but not a win. winRatePct = wins / n * 100.
 *
 * 3. weekPnl: sum of pnl_closed for rows whose closed_at, in device-local date, is within the
 *    last 7 days (>= today - 6). Rows older than 7 calendar days are excluded.
 *
 * NEVER throws to callers — all exceptions are caught and returned as [ReportsResult.Error].
 * [CancellationException] is rethrown so coroutine cancellation propagates correctly.
 */
@Singleton
class ReportsRepositoryImpl @Inject constructor(
    private val reportsApi: ReportsApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ReportsRepository {

    override suspend fun fetchReports(): ReportsResult = withContext(ioDispatcher) {
        try {
            val response = reportsApi.getReports()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext ReportsResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext ReportsResult.Error(message = "Empty response body")
                }

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            // Cutoff: today - 6 days. A trade whose local date is before the cutoff is excluded.
            val weekCutoff = today.minusDays(6)

            // 1. signalsToday — count signals created on today's local date.
            val signalsToday = body.signals.count { signal ->
                val createdAt = signal.createdAt ?: return@count false
                try {
                    Instant.parse(createdAt).atZone(zone).toLocalDate() == today
                } catch (e: Exception) {
                    false
                }
            }

            val historyRows = body.history

            // 2. winRatePct — canonical (mirrors backend analysis.aggregate_trades + _metrics):
            // group legs by signal_id, qty-weighted direction-aware points per trade.
            // Fallback key uses the row INDEX (mirrors Python's id(r) per-row uniqueness) so two
            // rows missing BOTH signal_id and closed_at don't collapse into one phantom trade.
            val tradePoints = historyRows
                .mapIndexed { index, row ->
                    (row.position?.signalId ?: row.closedAt ?: "keyless_$index") to row
                }
                .groupBy({ it.first }, { it.second })
                .values
                .mapNotNull { legs ->
                    var wsum = 0.0
                    var qsum = 0.0
                    for (leg in legs) {
                        val pos = leg.position
                        val pts = legPoints(pos?.direction, pos?.entryPrice, leg.closePrice) ?: continue
                        val q = (leg.qtyClosed ?: pos?.qty ?: 1.0).let { if (it == 0.0) 1.0 else it }
                        wsum += pts * q
                        qsum += q
                    }
                    if (qsum != 0.0) wsum / qsum else null // null points → excluded from n
                }
            val n = tradePoints.size
            val wins = tradePoints.count { it > 0.0 } // breakeven (==0) is in n, not a win
            val winRatePct = if (n == 0) 0.0 else (wins.toDouble() / n) * 100.0

            // 3. weekPnl — sum pnl_closed for rows within the last 7 calendar days.
            val weekPnl = historyRows.sumOf { entry ->
                val pnl = entry.pnlClosed ?: return@sumOf 0.0
                val closedAt = entry.closedAt ?: return@sumOf 0.0
                try {
                    val localDate = Instant.parse(closedAt).atZone(zone).toLocalDate()
                    if (!localDate.isBefore(weekCutoff)) pnl else 0.0
                } catch (e: Exception) {
                    0.0
                }
            }

            // 4. Map history rows 1:1 to ClosedTrade (partial + final are two separate rows).
            val trades = historyRows.mapNotNull { entry ->
                val closedAt = entry.closedAt ?: return@mapNotNull null
                val closePrice = entry.closePrice ?: return@mapNotNull null
                val pnlClosed = entry.pnlClosed ?: return@mapNotNull null
                val position = entry.position ?: return@mapNotNull null
                val entryPrice = position.entryPrice ?: return@mapNotNull null
                val direction = position.direction ?: return@mapNotNull null
                val pattern = position.pattern ?: ""

                val side = when (direction.lowercase()) {
                    "long" -> Side.Long
                    "short" -> Side.Short
                    else -> return@mapNotNull null
                }

                ClosedTrade(
                    closedAt = closedAt,
                    side = side,
                    entryPrice = entryPrice,
                    exitPrice = closePrice,
                    pnl = pnlClosed,
                    pattern = pattern,
                )
            }

            ReportsResult.Success(
                ReportsData(
                    signalsToday = signalsToday,
                    winRatePct = winRatePct,
                    weekPnl = weekPnl,
                    trades = trades,
                )
            )
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            ReportsResult.Error(message = e.message)
        }
    }

    /** Direction-aware leg points (mirrors backend analysis._leg_points). null if data missing. */
    private fun legPoints(direction: String?, entry: Double?, close: Double?): Double? {
        if (entry == null || close == null) return null
        return when (direction?.lowercase()) {
            "long" -> close - entry
            "short" -> entry - close
            else -> null // unknown/absent direction: exclude from the trade rather than corrupt it
        }
    }
}
