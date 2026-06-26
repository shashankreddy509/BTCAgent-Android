package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.ReportsApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * JVM unit tests for [AnalyticsRepositoryImpl] — MOBILE-17.
 *
 * Uses [MockWebServer] to exercise the full HTTP → DTO → domain mapping path.
 * The repository reuses [ReportsApi] (`GET /api/trading/reports`) and derives:
 * - [TradeMetrics] via client-side canonical formula (extracted into TradeAggregation.kt).
 * - `byPattern` list (sorted by win_rate DESC, ties by count DESC).
 * - `equityCurve`: sorted by closed_at ASC, cumulative sum of pnl_closed, filtered to last 30
 *   calendar days (closed_at local date >= today - 29).
 *
 * All tests MUST fail (red) until [AnalyticsRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   a. 200 response with 3 rows (2 within 30 days, 1 outside) → Success with correct equity curve.
 *   b. Empty history → Success where data.isEmpty == true.
 *   c. HTTP 500 → AnalyticsResult.Error (never throws).
 *   d. Network exception (server shut down) → AnalyticsResult.Error (never throws).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var reportsApi: ReportsApi
    private lateinit var repository: AnalyticsRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ISO-8601 UTC formatter for generating closed_at timestamps.
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    /** today-N days, formatted as UTC ISO-8601 string */
    private fun daysAgoUtc(n: Long): String {
        val instant = LocalDate.now(ZoneOffset.UTC)
            .minusDays(n)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
        return isoFormatter.format(instant)
    }

    /**
     * Builds the JSON body for GET /api/trading/reports.
     * The shape must match [com.gshashank.btcagent.data.network.ReportsDto].
     */
    private fun reportsJson(historyJson: String = "[]"): String = """
        {
          "signals": [],
          "positions": [],
          "history": $historyJson
        }
    """.trimIndent()

    /**
     * Builds a single history entry JSON object.
     * Mirrors the structure used in [ReportsRepositoryImplTest.historyEntryJson].
     * Field names match [com.gshashank.btcagent.data.network.HistoryEntryDto].
     */
    private fun historyEntryJson(
        signalId: String = "sig-${entryCounter++}",
        direction: String = "long",
        entryPrice: Double = 50_000.0,
        closePrice: Double = 51_000.0,
        pnlClosed: Double = 100.0,
        closedAt: String = daysAgoUtc(1),
        pattern: String = "Bull Flag",
        qtyClosed: Double = 1.0,
        qty: Double = 1.0,
    ): String = """
        {
          "position": {
            "signal_id": "$signalId",
            "direction": "$direction",
            "entry_price": $entryPrice,
            "qty": $qty,
            "pattern": "$pattern"
          },
          "close_price": $closePrice,
          "close_reason": "tp",
          "closed_at": "$closedAt",
          "qty_closed": $qtyClosed,
          "pnl_closed": $pnlClosed,
          "mode": "paper"
        }
    """.trimIndent()

    private var entryCounter = 0

    @Before
    fun setUp() {
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()

        reportsApi = retrofit.create(ReportsApi::class.java)
        repository = AnalyticsRepositoryImpl(
            reportsApi = reportsApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // a. fetch_success_200_builds_metrics_and_equity_curve
    //
    // 3 history rows:
    //   Row 1: closedAt = today-2 days (within 30d), pnl_closed = 50.0
    //   Row 2: closedAt = today-1 day  (within 30d), pnl_closed = 100.0
    //   Row 3: closedAt = today-60 days (outside 30d), pnl_closed = 999.0 (must NOT appear)
    //
    // Equity curve sorted by closed_at ASC:
    //   Row 1 (today-2) first  → cumulative = 50.0
    //   Row 2 (today-1) second → cumulative = 150.0
    //   Row 3 excluded (> 30 days ago)
    // Expected equityCurve = [50.0, 150.0]
    // =========================================================================

    @Test
    fun `fetch success 200 builds correct metrics and equity curve excluding rows outside 30 days`() =
        runTest(testDispatcher) {
            val row1ClosedAt = daysAgoUtc(2)  // within 30 days
            val row2ClosedAt = daysAgoUtc(1)  // within 30 days
            val row3ClosedAt = daysAgoUtc(60) // outside 30 days → must be excluded

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        reportsJson(
                            historyJson = """[
                                ${historyEntryJson(
                                    signalId = "trade-1",
                                    direction = "long",
                                    entryPrice = 50_000.0,
                                    closePrice = 51_000.0,
                                    pnlClosed = 50.0,
                                    closedAt = row1ClosedAt,
                                )},
                                ${historyEntryJson(
                                    signalId = "trade-2",
                                    direction = "long",
                                    entryPrice = 50_000.0,
                                    closePrice = 52_000.0,
                                    pnlClosed = 100.0,
                                    closedAt = row2ClosedAt,
                                )},
                                ${historyEntryJson(
                                    signalId = "trade-old",
                                    direction = "long",
                                    entryPrice = 40_000.0,
                                    closePrice = 49_990.0,
                                    pnlClosed = 999.0,
                                    closedAt = row3ClosedAt,
                                )}
                            ]""",
                        )
                    ),
            )

            val result = repository.fetch()

            assertTrue("Result must be AnalyticsResult.Success, got $result", result is AnalyticsResult.Success)
            val data = (result as AnalyticsResult.Success).data

            // The two within-30d trades must count.
            assertTrue(
                "metrics.count must be >= 1 (at least the within-30d trades contribute to metrics)",
                data.metrics.count >= 1,
            )

            // Equity curve: sorted by closed_at ASC, cumulative pnl, only last 30 days.
            // Row1 (today-2) → 50.0; Row2 (today-1) → 150.0; Row3 excluded.
            assertEquals(
                "Equity curve must contain exactly 2 points (old row excluded)",
                2,
                data.equityCurve.size,
            )
            assertEquals(
                "First equity curve point must be 50.0 (cumulative after row1, sorted ASC by closed_at)",
                50.0,
                data.equityCurve[0],
                0.001,
            )
            assertEquals(
                "Second equity curve point must be 150.0 (50.0 + 100.0 cumulative)",
                150.0,
                data.equityCurve[1],
                0.001,
            )

            // The old row's pnl must NOT appear anywhere in the equity curve.
            assertFalse(
                "The old row's pnl (999.0) must NOT appear in the equity curve",
                data.equityCurve.any { it >= 999.0 },
            )
        }

    // =========================================================================
    // b. fetch_empty_history_returns_success_isEmpty
    //
    // JSON with empty history[] → Success where data.isEmpty == true.
    // =========================================================================

    @Test
    fun `fetch empty history returns Success with isEmpty true`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(reportsJson(historyJson = "[]")),
        )

        val result = repository.fetch()

        assertTrue(
            "Empty history must return AnalyticsResult.Success, got $result",
            result is AnalyticsResult.Success,
        )
        val data = (result as AnalyticsResult.Success).data
        assertTrue(
            "AnalyticsData.isEmpty must be true when history is empty (metrics.count == 0)",
            data.isEmpty,
        )
        assertTrue(
            "equityCurve must be empty when history is empty",
            data.equityCurve.isEmpty(),
        )
        assertTrue(
            "byPattern must be empty when history is empty",
            data.byPattern.isEmpty(),
        )
    }

    // =========================================================================
    // c. fetch_non_2xx_returns_error
    //
    // HTTP 500 → AnalyticsResult.Error (never throws, never crashes).
    // =========================================================================

    @Test
    fun `fetch HTTP 500 returns AnalyticsResult Error and never throws`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.fetch()

        assertTrue(
            "HTTP 500 must map to AnalyticsResult.Error — repository must never throw, got $result",
            result is AnalyticsResult.Error,
        )
    }

    // =========================================================================
    // d. fetch_network_exception_returns_error
    //
    // Server shut down before call → IOException → AnalyticsResult.Error (never throws).
    // =========================================================================

    @Test
    fun `fetch network exception returns AnalyticsResult Error and never throws`() =
        runTest(testDispatcher) {
            // Shut down the server so any HTTP call throws an IOException.
            mockWebServer.shutdown()

            val result = repository.fetch()

            assertTrue(
                "Network IOException must map to AnalyticsResult.Error — repository must never throw, " +
                    "got $result",
                result is AnalyticsResult.Error,
            )
        }
}
