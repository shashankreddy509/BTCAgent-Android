package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.AnalyticsData
import com.gshashank.btcagent.data.network.ReportsApi
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [AnalyticsRepository] — MOBILE-17.
 *
 * Reuses [ReportsApi] (GET /api/trading/reports) and computes analytics client-side:
 * - Trade aggregation + metrics via canonical formulas in [TradeAggregation.kt].
 * - Equity curve: history sorted by closed_at ASC, cumulative pnl_closed, filtered to last
 *   30 calendar days (closed_at local date >= today - 29 days, UTC).
 *
 * NEVER throws to callers — [CancellationException] is rethrown; all other exceptions map to
 * [AnalyticsResult.Error].
 */
@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val reportsApi: ReportsApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnalyticsRepository {

    override suspend fun fetch(): AnalyticsResult = withContext(ioDispatcher) {
        try {
            val response = reportsApi.getReports()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext AnalyticsResult.Error(message = "HTTP ${response.code()}")
            }
            val body = response.body()
                ?: run {
                    response.errorBody()?.close()
                    return@withContext AnalyticsResult.Error(message = "Empty response")
                }

            val historyRows = body.history

            // 1. Aggregate per-trade points via canonical formula.
            val trades = aggregateTrades(historyRows)

            // 2. Compute overall metrics.
            val metrics = computeMetrics(trades.map { it.points })

            // 3. Per-pattern metrics, sorted by win rate DESC (ties: count DESC).
            val byPattern = metricsByPattern(trades)

            // 4. Equity curve: sort by closed_at ASC (UTC), filter to last 30 calendar days
            //    (UTC local date >= today_utc - 29), cumulative running sum of pnl_closed.
            val cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(29)

            // Parse each row's closedAt as an Instant; skip rows without a parseable timestamp.
            data class TimedPnl(val instant: Instant, val pnl: Double)
            val timedRows = historyRows.mapNotNull { entry ->
                val closedAt = entry.closedAt ?: return@mapNotNull null
                val pnl = entry.pnlClosed ?: return@mapNotNull null
                try {
                    val instant = Instant.parse(closedAt)
                    TimedPnl(instant = instant, pnl = pnl)
                } catch (e: Exception) {
                    null // skip rows with unparseable timestamps
                }
            }

            // Sort ASC by instant, then filter to within 30 days, then cumulate.
            val sortedRows = timedRows.sortedBy { it.instant }
            val equityCurve = mutableListOf<Double>()
            var cumPnl = 0.0
            for (row in sortedRows) {
                val localDate = row.instant.atZone(ZoneOffset.UTC).toLocalDate()
                if (!localDate.isBefore(cutoff)) {
                    cumPnl += row.pnl
                    equityCurve.add(cumPnl)
                }
            }

            AnalyticsResult.Success(
                AnalyticsData(
                    metrics = metrics,
                    byPattern = byPattern,
                    equityCurve = equityCurve,
                )
            )
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            // Generic message — don't surface raw exception text (may carry host/IP).
            AnalyticsResult.Error(message = "Network error")
        }
    }
}
