package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.TradePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [TradeAggregation] — MOBILE-17.
 *
 * Verifies the canonical backend formulas extracted into TradeAggregation.kt:
 * - legPoints(): direction-aware per-leg points (long = close - entry, short = entry - close).
 * - aggregateTrades(): group history legs by signal_id, qty-weighted avg per trade.
 * - computeMetrics(): win rate, expectancy, profit factor, max drawdown from per-trade points.
 * - metricsByPattern(): group trades by pattern, run metrics per group, sort by win_rate DESC.
 *
 * All tests MUST fail (red) until TradeAggregation.kt is implemented.
 */
class TradeAggregationTest {

    // =========================================================================
    // a. legPoints_long_returns_close_minus_entry
    // =========================================================================

    @Test
    fun `legPoints long returns close minus entry`() {
        val result = legPoints("long", entry = 100.0, close = 110.0)
        assertEquals(
            "Long leg: legPoints must return close - entry = 110 - 100 = 10.0",
            10.0,
            result!!,
            0.0001,
        )
    }

    // =========================================================================
    // b. legPoints_short_returns_entry_minus_close
    // =========================================================================

    @Test
    fun `legPoints short returns entry minus close`() {
        val result = legPoints("short", entry = 100.0, close = 95.0)
        assertEquals(
            "Short leg: legPoints must return entry - close = 100 - 95 = 5.0",
            5.0,
            result!!,
            0.0001,
        )
    }

    // =========================================================================
    // c. legPoints_unknown_direction_returns_null
    // =========================================================================

    @Test
    fun `legPoints unknown direction returns null`() {
        val result = legPoints("flat", entry = 100.0, close = 110.0)
        assertNull(
            "Unknown direction 'flat' must return null (exclude from trade rather than corrupt it)",
            result,
        )
    }

    // =========================================================================
    // d. legPoints_null_inputs_returns_null
    // =========================================================================

    @Test
    fun `legPoints null inputs returns null`() {
        val result = legPoints(null, null, null)
        assertNull(
            "Null direction, entry, and close must return null",
            result,
        )
    }

    // =========================================================================
    // e. aggregateTrades_partial_tp_collapses_to_one_trade
    //
    // Two legs with the same signal_id (partial TP) collapse to ONE TradePoint.
    // Weighted avg: (10*2 + 15*3) / (2+3) = (20+45)/5 = 65/5 = 13.0
    // Total pnl: 20.0 + 45.0 = 65.0
    // =========================================================================

    @Test
    fun `aggregateTrades partial TP with same signal id collapses to one trade`() {
        val historyRows = listOf(
            makeHistoryEntry(
                signalId = "A",
                direction = "long",
                entryPrice = 100.0,
                closePrice = 110.0,  // points = 10.0
                qtyClosed = 2.0,
                pnlClosed = 20.0,
            ),
            makeHistoryEntry(
                signalId = "A",
                direction = "long",
                entryPrice = 100.0,
                closePrice = 115.0,  // points = 15.0
                qtyClosed = 3.0,
                pnlClosed = 45.0,
            ),
        )

        val trades = aggregateTrades(historyRows)

        assertEquals(
            "Two legs with same signal_id must collapse to exactly 1 TradePoint",
            1,
            trades.size,
        )
        assertEquals(
            "Qty-weighted avg points: (10.0*2 + 15.0*3) / (2+3) = 13.0",
            13.0,
            trades[0].points,
            0.0001,
        )
        assertEquals(
            "Total pnl must be sum of leg pnl_closed: 20.0 + 45.0 = 65.0",
            65.0,
            trades[0].pnl,
            0.0001,
        )
    }

    // =========================================================================
    // f. aggregateTrades_direction_aware_short
    //
    // Short trade: entry=100, close=90 → legPoints = entry - close = 10.0 (positive for profit).
    // =========================================================================

    @Test
    fun `aggregateTrades direction aware short trade produces positive points`() {
        val historyRows = listOf(
            makeHistoryEntry(
                signalId = "short-trade",
                direction = "short",
                entryPrice = 100.0,
                closePrice = 90.0,
                qtyClosed = 1.0,
                pnlClosed = 10.0,
            ),
        )

        val trades = aggregateTrades(historyRows)

        assertEquals("Short trade must produce 1 TradePoint", 1, trades.size)
        assertEquals(
            "Short leg: entry(100) - close(90) = 10.0 (profitable short must have positive points)",
            10.0,
            trades[0].points,
            0.0001,
        )
    }

    // =========================================================================
    // g. discriminator_per_trade_vs_per_leg_win_rate_differ
    //
    // MOST CRITICAL test (MOBILE-7 lesson): per-trade win rate != per-leg win rate.
    //
    // Trade A (signal_id="A"): two legs.
    //   Leg A1: long, entry=100, close=130 → points=30 (winning)
    //   Leg A2: long, entry=100, close=95  → points=-5 (losing)
    //   Weighted avg (qty=1 each): (30 + -5) / 2 = 12.5 → WINNING trade.
    //
    // Trade B (signal_id="B"): single leg.
    //   Leg B1: long, entry=100, close=90  → points=-10 → LOSING trade.
    //
    // Per-leg naive: wins=[A1] out of [A1,A2,B1] = 1/3 = 33.3%
    // Per-trade (canonical): wins=[A] out of [A,B] = 1/2 = 50%
    // computeMetrics must return winRatePct=50.0, NOT 33.3.
    // =========================================================================

    @Test
    fun `discriminator per trade vs per leg win rate differ - canonical is per trade`() {
        // Trade A: mixed-result legs that net out to a winning trade
        val legA1 = makeHistoryEntry(
            signalId = "A",
            direction = "long",
            entryPrice = 100.0,
            closePrice = 130.0,  // +30 points
            qtyClosed = 1.0,
            pnlClosed = 30.0,
        )
        val legA2 = makeHistoryEntry(
            signalId = "A",
            direction = "long",
            entryPrice = 100.0,
            closePrice = 95.0,  // -5 points
            qtyClosed = 1.0,
            pnlClosed = -5.0,
        )
        // Trade B: single losing leg
        val legB1 = makeHistoryEntry(
            signalId = "B",
            direction = "long",
            entryPrice = 100.0,
            closePrice = 90.0,  // -10 points
            qtyClosed = 1.0,
            pnlClosed = -10.0,
        )

        val trades = aggregateTrades(listOf(legA1, legA2, legB1))

        // Trade A avg = (30 + -5) / 2 = 12.5 → win; Trade B = -10 → loss.
        assertEquals("Must produce exactly 2 per-trade TradePoints (A and B)", 2, trades.size)

        val tradeAPoints = trades.find { it.points > 0 }
        val tradeBPoints = trades.find { it.points < 0 }
        assertTrue("Trade A weighted avg must be positive (winning)", tradeAPoints != null)
        assertTrue("Trade B must be negative (losing)", tradeBPoints != null)

        val points = trades.map { it.points }
        val metrics = computeMetrics(points)

        assertEquals(
            "Per-trade win rate: 1 win out of 2 trades = 50.0%  " +
                "(per-leg naive would give 33.3% — must use per-trade canonical formula)",
            50.0,
            metrics.winRatePct,
            0.01,
        )
    }

    // =========================================================================
    // h. computeMetrics_win_rate_excludes_breakeven
    //
    // points=[10.0, 0.0, -5.0]  → n=3, wins=1 (only 10.0>0), winRatePct=33.333...
    // Breakeven 0.0 is in n but NOT a win.
    // =========================================================================

    @Test
    fun `computeMetrics win rate excludes breakeven from wins but includes in denominator`() {
        val metrics = computeMetrics(listOf(10.0, 0.0, -5.0))

        assertEquals(
            "n must be 3 (breakeven is counted in denominator)",
            3,
            metrics.count,
        )
        assertEquals(
            "winRatePct must be 1/3 * 100 = 33.33... (breakeven 0.0 is NOT a win)",
            100.0 / 3.0,
            metrics.winRatePct,
            0.01,
        )
    }

    // =========================================================================
    // i. computeMetrics_expectancy
    //
    // points=[10.0, -5.0]
    // win_rate=0.5, avg_win=10.0, loss_rate=0.5, avg_loss=-5.0
    // expectancy = 0.5*10.0 + 0.5*(-5.0) = 5.0 - 2.5 = 2.5
    // =========================================================================

    @Test
    fun `computeMetrics expectancy formula win_rate x avg_win plus loss_rate x avg_loss`() {
        val metrics = computeMetrics(listOf(10.0, -5.0))

        assertEquals("win_rate must be 0.5 → winRatePct=50.0", 50.0, metrics.winRatePct, 0.001)
        assertEquals("avg_win must be 10.0", 10.0, metrics.avgWin, 0.001)
        assertEquals("avg_loss must be -5.0 (negative)", -5.0, metrics.avgLoss, 0.001)
        assertEquals(
            "expectancy = 0.5*10.0 + 0.5*(-5.0) = 2.5",
            2.5,
            metrics.expectancy,
            0.001,
        )
    }

    // =========================================================================
    // j. computeMetrics_profit_factor_null_when_no_losses
    //
    // points=[10.0, 5.0] → gross_loss=0 → profit_factor is null (∞)
    // =========================================================================

    @Test
    fun `computeMetrics profit factor is null when there are no losing trades`() {
        val metrics = computeMetrics(listOf(10.0, 5.0))

        assertNull(
            "profitFactor must be null when gross_loss == 0 (∞ — no losses to divide by)",
            metrics.profitFactor,
        )
    }

    // =========================================================================
    // k. computeMetrics_max_drawdown
    //
    // points=[10.0, -20.0, 15.0]
    // Trace:
    //   p=10:   cum=10,  peak=10,  dd=max(0,  10-10)=0
    //   p=-20:  cum=-10, peak=10,  dd=max(0,  10-(-10))=20
    //   p=15:   cum=5,   peak=10,  dd=max(20, 10-5)=20
    // maxDrawdown = 20.0
    // =========================================================================

    @Test
    fun `computeMetrics max drawdown peak to trough on cumulative points curve`() {
        val metrics = computeMetrics(listOf(10.0, -20.0, 15.0))

        assertEquals(
            "maxDrawdown must be 20.0: peak=10 at step1, trough=-10 at step2 → 10-(-10)=20",
            20.0,
            metrics.maxDrawdown,
            0.001,
        )
    }

    // =========================================================================
    // l. computeMetrics_empty_points
    // =========================================================================

    @Test
    fun `computeMetrics empty points list returns zero metrics with null profitFactor`() {
        val metrics = computeMetrics(emptyList())

        assertEquals("count must be 0 for empty input", 0, metrics.count)
        assertEquals("winRatePct must be 0.0 for empty input", 0.0, metrics.winRatePct, 0.0001)
        assertNull("profitFactor must be null for empty input (no losses)", metrics.profitFactor)
        assertEquals("maxDrawdown must be 0.0 for empty input", 0.0, metrics.maxDrawdown, 0.0001)
    }

    // =========================================================================
    // m. metricsByPattern_groups_and_sorts_by_win_rate_desc
    //
    // Bull: 2 trades (1 win, 1 loss → 50% win rate)
    // Bear: 1 trade  (1 win         → 100% win rate)
    // Expected order: Bear (100%) first, Bull (50%) second.
    // =========================================================================

    @Test
    fun `metricsByPattern groups trades by pattern and sorts by win rate descending`() {
        val trades = listOf(
            TradePoint(points = 10.0, pnl = 10.0, pattern = "Bull"),  // win
            TradePoint(points = -5.0, pnl = -5.0, pattern = "Bull"),  // loss
            TradePoint(points = 8.0,  pnl = 8.0,  pattern = "Bear"),  // win
        )

        val result = metricsByPattern(trades)

        assertEquals("Must have 2 pattern groups (Bull and Bear)", 2, result.size)
        assertEquals(
            "First pattern must be Bear with 100% win rate (sorted DESC)",
            "Bear",
            result[0].pattern,
        )
        assertEquals(
            "Bear win rate must be 100.0%",
            100.0,
            result[0].metrics.winRatePct,
            0.001,
        )
        assertEquals(
            "Second pattern must be Bull with 50% win rate",
            "Bull",
            result[1].pattern,
        )
        assertEquals(
            "Bull win rate must be 50.0%",
            50.0,
            result[1].metrics.winRatePct,
            0.001,
        )
    }

    // =========================================================================
    // n. metricsByPattern_ties_broken_by_count_desc
    //
    // Pattern A: 3 winning trades → 100% win rate, count=3
    // Pattern B: 1 winning trade  → 100% win rate, count=1
    // Tie on win rate → A comes first (higher count).
    // =========================================================================

    @Test
    fun `metricsByPattern ties on win rate broken by count descending`() {
        val trades = listOf(
            TradePoint(points = 10.0, pnl = 10.0, pattern = "A"),
            TradePoint(points = 12.0, pnl = 12.0, pattern = "A"),
            TradePoint(points = 8.0,  pnl = 8.0,  pattern = "A"),
            TradePoint(points = 15.0, pnl = 15.0, pattern = "B"),
        )

        val result = metricsByPattern(trades)

        assertEquals("Must have 2 pattern groups", 2, result.size)
        assertEquals(
            "Pattern A (count=3) must come before Pattern B (count=1) when win rates are tied",
            "A",
            result[0].pattern,
        )
        assertEquals("Pattern A count must be 3", 3, result[0].metrics.count)
        assertEquals(
            "Pattern B (count=1) must come second",
            "B",
            result[1].pattern,
        )
        assertEquals("Pattern B count must be 1", 1, result[1].metrics.count)
    }

    // =========================================================================
    // o. metricsByPattern_default_pattern_for_null
    //
    // A TradePoint with empty/null pattern (stored as "" or "—") must be grouped under "—".
    // =========================================================================

    @Test
    fun `metricsByPattern defaults empty pattern to em dash placeholder`() {
        val trades = listOf(
            TradePoint(points = 5.0, pnl = 5.0, pattern = ""),  // empty string → default "—"
        )

        val result = metricsByPattern(trades)

        assertEquals("Must have 1 pattern group", 1, result.size)
        assertEquals(
            "Empty pattern must be grouped under '—' (em dash default)",
            "—",
            result[0].pattern,
        )
    }

    // =========================================================================
    // Helpers — build HistoryEntryDto instances without a real network response.
    // =========================================================================

    /**
     * Creates a [com.gshashank.btcagent.data.network.HistoryEntryDto] for use in
     * [aggregateTrades] tests. The DTO structure mirrors what the ReportsApi returns.
     */
    private fun makeHistoryEntry(
        signalId: String,
        direction: String,
        entryPrice: Double,
        closePrice: Double,
        qtyClosed: Double,
        pnlClosed: Double,
        qty: Double = 1.0,
        pattern: String = "Bull Flag",
        closedAt: String = "2026-06-24T10:00:00Z",
    ): com.gshashank.btcagent.data.network.HistoryEntryDto =
        com.gshashank.btcagent.data.network.HistoryEntryDto(
            closePrice = closePrice,
            closedAt = closedAt,
            pnlClosed = pnlClosed,
            qtyClosed = qtyClosed,
            position = com.gshashank.btcagent.data.network.HistoryPositionDto(
                signalId = signalId,
                direction = direction,
                entryPrice = entryPrice,
                qty = qty,
                pattern = pattern,
            ),
        )
}
