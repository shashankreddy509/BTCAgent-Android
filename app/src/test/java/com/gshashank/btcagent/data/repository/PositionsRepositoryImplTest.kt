package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.PositionsApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [PositionsRepositoryImpl] — MOBILE-6.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by
 * the Retrofit layer and then mapped by the repository.
 *
 * The trading-state endpoint returns current_price at the STATE ROOT and positions[] array.
 * The cancel endpoint is POST /api/trading/position/{signalId}/cancel.
 * The edit endpoint is POST /api/trading/position/{signalId}/edit.
 *
 * The repository is responsible for:
 *   - Fetching GET /api/trading/state and mapping PositionDto[] → List<Position>.
 *   - Computing live P&L client-side (server pnl is null while open):
 *       long:  (current − entry) * qty * contractSize
 *       short: (entry − current) * qty * contractSize
 *       pnl_pct = pnl / (entry * qty * contractSize) * 100
 *     When contractSize is absent or 0, fall back to qty only (contractSize treated as 1).
 *   - POST cancel / edit, mapping HTTP status to [ActionResult].
 *   - NEVER throwing to callers — catching exceptions → [PositionsResult.Error] /
 *     [ActionResult.Error]. CancellationException is rethrown.
 *
 * All tests MUST fail (red) until [PositionsRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   a. P&L long: entry=50000, current=51000, qty=2, contractSize=0.001 → pnl = 2.0
 *   b. P&L short: entry=50000, current=49000, qty=2, contractSize=0.001 → pnl = 2.0
 *   c. pnl_pct: 2.0 / (50000 * 2 * 0.001) * 100 = 2.0%
 *   d. contractSize=0 fallback: pnl = price_diff * qty (contractSize treated as 1)
 *   e. cancel 200 {status:"cancelled"} → ActionResult.Success
 *   f. cancel 404 → ActionResult.Error with code 404
 *   g. edit 200 {signal_id, sl, tp} → ActionResult.Success
 *   h. edit 403 → ActionResult.Error with code 403
 *   i. edit 400 → ActionResult.Error with code 400
 *   j. network exception → PositionsResult.Error (never throws)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionsRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var positionsApi: PositionsApi
    private lateinit var repository: PositionsRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * A valid /api/trading/state JSON response.
     * current_price is at ROOT level (not per-position).
     * positions[] items carry: signal_id, entry_price, qty, direction, contract_size,
     * sl, tp, opened_at, status, mode — pnl is null (open position).
     */
    private fun tradingStateJson(
        currentPrice: Double,
        signalId: String = "sig-001",
        entryPrice: Double = 50_000.0,
        qty: Double = 2.0,
        direction: String = "long",
        contractSize: Double? = 0.001,
        sl: Double = 49_000.0,
        tp: Double = 53_000.0,
    ): String {
        val contractSizeField = if (contractSize != null) {
            """"contract_size": $contractSize,"""
        } else {
            ""
        }
        return """
        {
          "running": true,
          "current_price": $currentPrice,
          "settings": { "mode": "live" },
          "positions": [
            {
              "signal_id": "$signalId",
              "entry_price": $entryPrice,
              "qty": $qty,
              "direction": "$direction",
              $contractSizeField
              "sl": $sl,
              "tp": $tp,
              "opened_at": "2026-06-24T10:00:00Z",
              "status": "open",
              "mode": "live",
              "pnl": null
            }
          ],
          "history": []
        }
        """.trimIndent()
    }

    @Before
    fun setUp() {
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()

        positionsApi = retrofit.create(PositionsApi::class.java)

        repository = PositionsRepositoryImpl(
            positionsApi = positionsApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // a. P&L long compute: entry=50000, current=51000, qty=2, contractSize=0.001
    //    pnl = (51000 - 50000) * 2 * 0.001 = 2.0
    // =========================================================================

    @Test
    fun `long position pnl computed correctly`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    tradingStateJson(
                        currentPrice = 51_000.0,
                        entryPrice = 50_000.0,
                        qty = 2.0,
                        direction = "long",
                        contractSize = 0.001,
                    )
                ),
        )

        val result = repository.fetchPositions()

        assertTrue("Must be Success, got $result", result is PositionsResult.Success)
        val positions = (result as PositionsResult.Success).positions
        assertEquals("Must have 1 position", 1, positions.size)
        assertEquals(
            "Long P&L: (51000 - 50000) * 2 * 0.001 = 2.0",
            2.0,
            positions[0].pnl,
            0.0001,
        )
    }

    // =========================================================================
    // b. P&L short compute: entry=50000, current=49000, qty=2, contractSize=0.001
    //    pnl = (50000 - 49000) * 2 * 0.001 = 2.0
    // =========================================================================

    @Test
    fun `short position pnl computed correctly`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    tradingStateJson(
                        currentPrice = 49_000.0,
                        entryPrice = 50_000.0,
                        qty = 2.0,
                        direction = "short",
                        contractSize = 0.001,
                    )
                ),
        )

        val result = repository.fetchPositions()

        assertTrue("Must be Success, got $result", result is PositionsResult.Success)
        val positions = (result as PositionsResult.Success).positions
        assertEquals("Must have 1 position", 1, positions.size)
        assertEquals(
            "Short P&L: (50000 - 49000) * 2 * 0.001 = 2.0",
            2.0,
            positions[0].pnl,
            0.0001,
        )
    }

    // =========================================================================
    // c. pnl_pct = pnl / (entry * qty * contractSize) * 100
    //    = 2.0 / (50000 * 2 * 0.001) * 100 = 2.0%
    // =========================================================================

    @Test
    fun `pnl pct computed correctly for long position`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    tradingStateJson(
                        currentPrice = 51_000.0,
                        entryPrice = 50_000.0,
                        qty = 2.0,
                        direction = "long",
                        contractSize = 0.001,
                    )
                ),
        )

        val result = repository.fetchPositions()

        assertTrue("Must be Success, got $result", result is PositionsResult.Success)
        val positions = (result as PositionsResult.Success).positions
        assertEquals(
            "pnlPct: 2.0 / (50000 * 2 * 0.001) * 100 = 2.0%",
            2.0,
            positions[0].pnlPct,
            0.0001,
        )
    }

    // =========================================================================
    // d. contractSize=0 fallback: pnl = price_diff * qty (contractSize treated as 1)
    //    entry=50000, current=51000, qty=2, contractSize=0 → pnl = 1000 * 2 = 2000
    // =========================================================================

    @Test
    fun `contractSize zero falls back to qty only for pnl computation`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    tradingStateJson(
                        currentPrice = 51_000.0,
                        entryPrice = 50_000.0,
                        qty = 2.0,
                        direction = "long",
                        contractSize = 0.0, // zero → fallback to qty-only
                    )
                ),
        )

        val result = repository.fetchPositions()

        assertTrue("Must be Success, got $result", result is PositionsResult.Success)
        val positions = (result as PositionsResult.Success).positions
        assertEquals(
            "contractSize=0 fallback: pnl = (51000 - 50000) * 2 = 2000.0",
            2000.0,
            positions[0].pnl,
            0.0001,
        )
    }

    // =========================================================================
    // e. cancel 200 {status:"cancelled"} → ActionResult.Success
    // =========================================================================

    @Test
    fun `cancel 200 maps to ActionResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "cancelled"}"""),
        )

        val result = repository.close(signalId = "sig-001")

        assertTrue(
            "HTTP 200 from cancel endpoint must map to ActionResult.Success, got $result",
            result is ActionResult.Success,
        )
    }

    // =========================================================================
    // f. cancel 404 → ActionResult.Error with code 404
    // =========================================================================

    @Test
    fun `cancel 404 maps to ActionResult Error with code 404`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "not found"}"""),
        )

        val result = repository.close(signalId = "sig-001")

        assertTrue(
            "HTTP 404 from cancel endpoint must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
        assertEquals(
            "Error code must be 404",
            404,
            (result as ActionResult.Error).code,
        )
    }

    // =========================================================================
    // g. edit 200 {signal_id, sl, tp} → ActionResult.Success
    // =========================================================================

    @Test
    fun `edit 200 maps to ActionResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"signal_id": "sig-001", "sl": 48500.0, "tp": 54000.0}"""),
        )

        val result = repository.editTpSl(
            signalId = "sig-001",
            sl = 48_500.0,
            tp = 54_000.0,
        )

        assertTrue(
            "HTTP 200 from edit endpoint must map to ActionResult.Success, got $result",
            result is ActionResult.Success,
        )
    }

    // =========================================================================
    // h. edit 403 → ActionResult.Error with code 403
    // =========================================================================

    @Test
    fun `edit 403 maps to ActionResult Error with code 403`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "Forbidden"}"""),
        )

        val result = repository.editTpSl(
            signalId = "sig-001",
            sl = 48_500.0,
            tp = 54_000.0,
        )

        assertTrue(
            "HTTP 403 from edit endpoint must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
        assertEquals(
            "Error code must be 403",
            403,
            (result as ActionResult.Error).code,
        )
    }

    // =========================================================================
    // i. edit 400 → ActionResult.Error with code 400
    // =========================================================================

    @Test
    fun `edit 400 maps to ActionResult Error with code 400`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "invalid sl/tp values"}"""),
        )

        val result = repository.editTpSl(
            signalId = "sig-001",
            sl = -1.0,
            tp = null,
        )

        assertTrue(
            "HTTP 400 from edit endpoint must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
        assertEquals(
            "Error code must be 400",
            400,
            (result as ActionResult.Error).code,
        )
    }

    // =========================================================================
    // j. network exception → PositionsResult.Error (never throws)
    // =========================================================================

    @Test
    fun `network exception maps to PositionsResult Error and never throws`() =
        runTest(testDispatcher) {
            // Shut down the server so any HTTP call throws an IOException.
            mockWebServer.shutdown()

            val result = repository.fetchPositions()

            assertTrue(
                "A network IOException must map to PositionsResult.Error — repository must never throw",
                result is PositionsResult.Error,
            )
        }

    // =========================================================================
    // Bonus: absent positions array → empty list (not a crash)
    // =========================================================================

    @Test
    fun `state with empty positions array returns empty Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": true,
                      "current_price": 50000.0,
                      "settings": { "mode": "paper" },
                      "positions": [],
                      "history": []
                    }
                    """.trimIndent()
                ),
        )

        val result = repository.fetchPositions()

        assertTrue("Must be Success with empty list, got $result", result is PositionsResult.Success)
        assertTrue(
            "positions list must be empty",
            (result as PositionsResult.Success).positions.isEmpty(),
        )
    }

    // =========================================================================
    // Bonus: contractSize absent (null) falls back to qty-only
    //    entry=50000, current=51000, qty=2, contractSize=null → pnl = 1000 * 2 = 2000
    // =========================================================================

    @Test
    fun `absent contractSize falls back to qty only`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    tradingStateJson(
                        currentPrice = 51_000.0,
                        entryPrice = 50_000.0,
                        qty = 2.0,
                        direction = "long",
                        contractSize = null, // absent → fallback
                    )
                ),
        )

        val result = repository.fetchPositions()

        assertTrue("Must be Success, got $result", result is PositionsResult.Success)
        val positions = (result as PositionsResult.Success).positions
        assertEquals(
            "contractSize=null fallback: pnl = (51000 - 50000) * 2 = 2000.0",
            2000.0,
            positions[0].pnl,
            0.0001,
        )
    }
}
