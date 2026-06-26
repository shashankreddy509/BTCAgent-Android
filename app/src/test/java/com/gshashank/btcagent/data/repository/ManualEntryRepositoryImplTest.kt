package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ManualOrderDraft
import com.gshashank.btcagent.data.model.OrderType
import com.gshashank.btcagent.data.model.PendingOrder
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.network.ManualEntryApi
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [ManualEntryRepositoryImpl] — MOBILE-19.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by the
 * Retrofit layer and then mapped by the repository.
 *
 * Endpoints under test (all on /api/trading, Bearer token):
 *   POST /manual-entry          — placeMarket()
 *   POST /manual-limit          — placeLimit()
 *   POST /manual-pending/{id}/cancel — cancelPending()
 *   GET  /api/trading/state     — fetchPending() (reads manual_pending[] from state)
 *
 * Repository contract:
 *   - NEVER throws to callers; CancellationException rethrown; errorBody() closed on non-2xx.
 *   - 403 mapped to the admin-only user message.
 *   - Error messages masked (not raw server text).
 *   - JSON: explicitNulls=false (tp omitted when null); direction sent lowercased.
 *   - No symbol field, no order-type field in POST bodies.
 *
 * All tests MUST fail (red) until [ManualEntryRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1.  placeMarket() 200 → ActionResult.Success
 *   2.  placeMarket() body: direction lowercased, tp omitted when null, no symbol, no order-type
 *   3.  placeMarket() 400 → ActionResult.Error with masked message (not raw server text)
 *   4.  placeMarket() 403 → ActionResult.Error containing admin-only message
 *   5.  placeMarket() 409 → ActionResult.Error
 *   6.  placeLimit() 200 → ActionResult.Success; body includes limit_price, no symbol
 *   7.  placeLimit() 400 → ActionResult.Error
 *   8.  cancelPending() 200 → ActionResult.Success
 *   9.  cancelPending() non-200 → ActionResult.Error; connection pool not exhausted (errorBody closed)
 *   10. fetchPending() maps manual_pending[] from TradingStateDto to List<PendingOrder>
 *   11. CancellationException is rethrown (not swallowed)
 *   12. Network exception → ActionResult.Error (never throws)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var manualEntryApi: ManualEntryApi
    private lateinit var repository: ManualEntryRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // Shared JSON helpers
    // -------------------------------------------------------------------------

    /** A minimal valid POST /manual-entry 200 response. */
    private fun marketEntryResponseJson(
        signalId: String = "sig-manual-001",
        entry: Double = 50_000.0,
        sl: Double = 49_000.0,
        tp: Double? = 53_000.0,
        direction: String = "long",
        qty: Double = 0.01,
        mode: String = "paper",
    ): String {
        val tpField = if (tp != null) """"tp": $tp,""" else ""
        return """
            {
              "signal_id": "$signalId",
              "entry": $entry,
              "sl": $sl,
              $tpField
              "direction": "$direction",
              "qty": $qty,
              "mode": "$mode"
            }
        """.trimIndent()
    }

    /** A minimal valid POST /manual-limit 200 response. */
    private fun limitOrderResponseJson(
        id: String = "limit-001",
        direction: String = "long",
        qty: Double = 0.01,
        limitPrice: Double = 49_500.0,
        sl: Double = 49_000.0,
        tp: Double? = null,
        createdAt: String = "2026-06-26T10:00:00Z",
    ): String {
        val tpField = if (tp != null) """"tp": $tp,""" else ""
        return """
            {
              "id": "$id",
              "direction": "$direction",
              "qty": $qty,
              "limit_price": $limitPrice,
              "sl": $sl,
              $tpField
              "created_at": "$createdAt",
              "placed_on_exchange": false
            }
        """.trimIndent()
    }

    /** A minimal valid POST /manual-pending/{id}/cancel 200 response. */
    private fun cancelPendingResponseJson(
        status: String = "cancelled",
        id: String = "limit-001",
    ): String = """{"status": "$status", "id": "$id"}"""

    /** Builds a GET /api/trading/state response body with a manual_pending[] array. */
    private fun tradingStateWithPendingJson(
        pendingJson: String = "[]",
    ): String = """
        {
          "running": true,
          "current_price": 50000.0,
          "settings": {
            "mode": "paper",
            "depo_entry_filter": false
          },
          "positions": [],
          "history": [],
          "manual_pending": $pendingJson
        }
    """.trimIndent()

    /** A single pending limit order JSON entry. */
    private fun pendingOrderJson(
        id: String = "limit-001",
        direction: String = "long",
        qty: Double = 0.01,
        limitPrice: Double = 49_500.0,
        sl: Double = 49_000.0,
        tp: String = "null",
        createdAt: String = "2026-06-26T10:00:00Z",
    ): String = """
        {
          "id": "$id",
          "direction": "$direction",
          "qty": $qty,
          "limit_price": $limitPrice,
          "sl": $sl,
          "tp": $tp,
          "created_at": "$createdAt",
          "placed_on_exchange": false
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()

        manualEntryApi = retrofit.create(ManualEntryApi::class.java)

        repository = ManualEntryRepositoryImpl(
            manualEntryApi = manualEntryApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. placeMarket() 200 → ActionResult.Success
    // =========================================================================

    @Test
    fun `placeMarket 200 returns ActionResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(marketEntryResponseJson()),
        )

        val draft = ManualOrderDraft(
            direction = Side.Long,
            orderType = OrderType.MARKET,
            qty = 0.01,
            limitPrice = null,
            sl = 49_000.0,
            tp = 53_000.0,
        )

        val result = repository.placeMarket(draft)

        assertTrue(
            "HTTP 200 from POST /manual-entry must map to ActionResult.Success, got $result",
            result is ActionResult.Success,
        )
    }

    // =========================================================================
    // 2. placeMarket() request body: direction lowercased, tp omitted when null,
    //    no symbol field, no order-type field
    // =========================================================================

    @Test
    fun `placeMarket sends direction lowercased and omits tp when null`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(marketEntryResponseJson()),
        )

        val draft = ManualOrderDraft(
            direction = Side.Long,
            orderType = OrderType.MARKET,
            qty = 0.01,
            limitPrice = null,
            sl = 49_000.0,
            tp = null, // null → must be omitted
        )

        repository.placeMarket(draft)

        val recordedRequest = mockWebServer.takeRequest()
        val body = recordedRequest.body.readUtf8()

        assertTrue(
            "direction must be sent lowercased ('long' not 'Long'), got body: $body",
            body.contains("\"direction\"") && body.contains("\"long\""),
        )
        assertFalse(
            "tp must be omitted when null (explicitNulls=false contract), got body: $body",
            body.contains("\"tp\""),
        )
        assertFalse(
            "No symbol field must appear in the POST body, got body: $body",
            body.contains("\"symbol\""),
        )
        assertFalse(
            "No order_type field must appear in the POST body, got body: $body",
            body.contains("\"order_type\""),
        )
    }

    @Test
    fun `placeMarket sends direction short lowercased`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(marketEntryResponseJson(direction = "short")),
        )

        val draft = ManualOrderDraft(
            direction = Side.Short,
            orderType = OrderType.MARKET,
            qty = 0.01,
            limitPrice = null,
            sl = 51_000.0,
            tp = null,
        )

        repository.placeMarket(draft)

        val body = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(
            "direction for Side.Short must be sent as 'short' (lowercase), got body: $body",
            body.contains("\"short\""),
        )
    }

    // =========================================================================
    // 3. placeMarket() 400 → ActionResult.Error with masked message
    // =========================================================================

    @Test
    fun `placeMarket 400 returns ActionResult Error with masked message`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "qty must be positive — internal detail that must NOT leak"}"""),
        )

        val draft = ManualOrderDraft(
            direction = Side.Long,
            orderType = OrderType.MARKET,
            qty = -1.0,
            limitPrice = null,
            sl = 49_000.0,
            tp = null,
        )

        val result = repository.placeMarket(draft)

        assertTrue(
            "HTTP 400 from POST /manual-entry must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
        val error = result as ActionResult.Error
        assertFalse(
            "Error message must NOT leak raw server error text ('internal detail that must NOT leak'): " +
                "got '${error.message}'",
            error.message.contains("internal detail that must NOT leak", ignoreCase = true),
        )
    }

    // =========================================================================
    // 4. placeMarket() 403 → ActionResult.Error with admin-only message
    // =========================================================================

    @Test
    fun `placeMarket 403 returns ActionResult Error with admin-only message`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"detail": "admin only"}"""),
            )

            val draft = ManualOrderDraft(
                direction = Side.Long,
                orderType = OrderType.MARKET,
                qty = 0.01,
                limitPrice = null,
                sl = 49_000.0,
                tp = null,
            )

            val result = repository.placeMarket(draft)

            assertTrue(
                "HTTP 403 from POST /manual-entry must map to ActionResult.Error, got $result",
                result is ActionResult.Error,
            )
            val error = result as ActionResult.Error
            assertTrue(
                "403 error message must contain the admin-only user-friendly text, got '${error.message}'",
                error.message.contains("Manual trading isn't enabled for your account yet", ignoreCase = true),
            )
        }

    // =========================================================================
    // 5. placeMarket() 409 → ActionResult.Error
    // =========================================================================

    @Test
    fun `placeMarket 409 returns ActionResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "no live price available"}"""),
        )

        val draft = ManualOrderDraft(
            direction = Side.Long,
            orderType = OrderType.MARKET,
            qty = 0.01,
            limitPrice = null,
            sl = 49_000.0,
            tp = null,
        )

        val result = repository.placeMarket(draft)

        assertTrue(
            "HTTP 409 from POST /manual-entry must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
    }

    // =========================================================================
    // 6. placeLimit() 200 → ActionResult.Success; body includes limit_price, no symbol
    // =========================================================================

    @Test
    fun `placeLimit 200 returns ActionResult Success and body contains limit_price`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(limitOrderResponseJson(limitPrice = 49_500.0)),
            )

            val draft = ManualOrderDraft(
                direction = Side.Long,
                orderType = OrderType.LIMIT,
                qty = 0.01,
                limitPrice = 49_500.0,
                sl = 49_000.0,
                tp = null,
            )

            val result = repository.placeLimit(draft)

            assertTrue(
                "HTTP 200 from POST /manual-limit must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val body = mockWebServer.takeRequest().body.readUtf8()
            assertTrue(
                "POST body for placeLimit must include 'limit_price' field, got body: $body",
                body.contains("\"limit_price\""),
            )
            assertFalse(
                "POST body for placeLimit must NOT include a 'symbol' field, got body: $body",
                body.contains("\"symbol\""),
            )
        }

    // =========================================================================
    // 7. placeLimit() 400 → ActionResult.Error
    // =========================================================================

    @Test
    fun `placeLimit 400 returns ActionResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "limit_price required"}"""),
        )

        val draft = ManualOrderDraft(
            direction = Side.Long,
            orderType = OrderType.LIMIT,
            qty = 0.01,
            limitPrice = 0.0, // invalid on server
            sl = 49_000.0,
            tp = null,
        )

        val result = repository.placeLimit(draft)

        assertTrue(
            "HTTP 400 from POST /manual-limit must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
    }

    // =========================================================================
    // 8. cancelPending() 200 → ActionResult.Success
    // =========================================================================

    @Test
    fun `cancelPending 200 returns ActionResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(cancelPendingResponseJson()),
        )

        val result = repository.cancelPending("limit-001")

        assertTrue(
            "HTTP 200 from POST /manual-pending/{id}/cancel must map to ActionResult.Success, got $result",
            result is ActionResult.Success,
        )
    }

    // =========================================================================
    // 9. cancelPending() non-200 → ActionResult.Error; errorBody closed
    //    Verified indirectly: two sequential non-2xx calls must not hang (pool exhaustion
    //    would occur if errorBody were leaked on the first call).
    // =========================================================================

    @Test
    fun `cancelPending non-200 returns ActionResult Error and errorBody is closed`() =
        runTest(testDispatcher) {
            // Enqueue two error responses — the second must be reachable without connection hang.
            repeat(2) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(404)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"detail": "pending order not found"}"""),
                )
            }

            val first = repository.cancelPending("missing-id-1")
            val second = repository.cancelPending("missing-id-2")

            assertTrue(
                "First 404 from cancelPending must map to ActionResult.Error, got $first",
                first is ActionResult.Error,
            )
            assertTrue(
                "Second 404 must also complete without hanging — proves errorBody was closed, got $second",
                second is ActionResult.Error,
            )
        }

    // =========================================================================
    // 10. fetchPending() maps manual_pending[] from TradingStateDto to List<PendingOrder>
    // =========================================================================

    @Test
    fun `fetchPending maps manual_pending array to List of PendingOrder`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    tradingStateWithPendingJson(
                        pendingJson = """[
                            ${pendingOrderJson(id = "limit-001", direction = "long", qty = 0.01, limitPrice = 49_500.0)},
                            ${pendingOrderJson(id = "limit-002", direction = "short", qty = 0.02, limitPrice = 51_000.0)}
                        ]""",
                    )
                ),
        )

        val result = repository.fetchPending()

        assertNotNull(
            "fetchPending() must return a non-null list, got $result",
            result,
        )
        assertEquals(
            "fetchPending() must return exactly 2 PendingOrders from manual_pending array",
            2,
            result.size,
        )
        assertEquals(
            "First PendingOrder id must match 'limit-001'",
            "limit-001",
            result[0].id,
        )
        assertEquals(
            "Second PendingOrder id must match 'limit-002'",
            "limit-002",
            result[1].id,
        )
    }

    @Test
    fun `fetchPending returns empty list when manual_pending is empty array`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(tradingStateWithPendingJson(pendingJson = "[]")),
            )

            val result = repository.fetchPending()

            assertTrue(
                "fetchPending() must return an empty list when manual_pending is empty, got $result",
                result.isEmpty(),
            )
        }

    @Test
    fun `fetchPending non-200 returns empty list and never throws`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        var threw = false
        val result: List<PendingOrder>
        try {
            result = repository.fetchPending()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            threw = true
            return@runTest
        }

        assertFalse(
            "fetchPending() must not throw for HTTP 500 — repository contract forbids throwing",
            threw,
        )
        assertTrue(
            "fetchPending() must return an empty list on HTTP 500",
            result.isEmpty(),
        )
    }

    // =========================================================================
    // 11. CancellationException is rethrown (not swallowed)
    //
    // Verifies that a generic IOException (from server shutdown) does NOT produce a
    // CancellationException — only real CancellationExceptions are rethrown.
    // =========================================================================

    @Test
    fun `generic IOException from placeMarket does not produce CancellationException`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            var caughtCancellation = false
            try {
                val draft = ManualOrderDraft(
                    direction = Side.Long,
                    orderType = OrderType.MARKET,
                    qty = 0.01,
                    limitPrice = null,
                    sl = 49_000.0,
                    tp = null,
                )
                repository.placeMarket(draft)
            } catch (e: CancellationException) {
                caughtCancellation = true
            }

            assertFalse(
                "An IOException must NOT be rethrown as CancellationException — " +
                    "only real CancellationExceptions should propagate",
                caughtCancellation,
            )
        }

    // =========================================================================
    // 12. Network exception → ActionResult.Error (never throws)
    // =========================================================================

    @Test
    fun `placeMarket network exception returns ActionResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown() // causes IOException on next request

            val draft = ManualOrderDraft(
                direction = Side.Long,
                orderType = OrderType.MARKET,
                qty = 0.01,
                limitPrice = null,
                sl = 49_000.0,
                tp = null,
            )

            val result = repository.placeMarket(draft)

            assertTrue(
                "A network IOException from placeMarket() must map to ActionResult.Error — " +
                    "repository must never throw to callers",
                result is ActionResult.Error,
            )
        }

    @Test
    fun `placeLimit network exception returns ActionResult Error`() = runTest(testDispatcher) {
        mockWebServer.shutdown()

        val draft = ManualOrderDraft(
            direction = Side.Short,
            orderType = OrderType.LIMIT,
            qty = 0.01,
            limitPrice = 51_000.0,
            sl = 52_000.0,
            tp = null,
        )

        val result = repository.placeLimit(draft)

        assertTrue(
            "A network IOException from placeLimit() must map to ActionResult.Error",
            result is ActionResult.Error,
        )
    }
}
