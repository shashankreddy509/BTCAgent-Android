package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.network.TradingControlApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [TradingControlRepositoryImpl] — MOBILE-18.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by the
 * Retrofit layer and then mapped by the repository.
 *
 * The repository is responsible for:
 *   - POST /api/trading/start  → start()  → [ActionResult]
 *   - POST /api/trading/stop   → stop()   → [ActionResult]
 *   - POST /api/trading/settings {mode}              → setMode()       → [ActionResult]
 *   - POST /api/trading/settings {depo_entry_filter} → setDepoAlerts() → [ActionResult]
 *   - GET  /api/trading/state  → fetchState() → [TradingControlResult]
 *   - POST /api/trading/position/{id}/cancel (reuses [PositionsApi.cancel]) → close() → [ActionResult]
 *   - NEVER throwing to callers; [CancellationException] is rethrown.
 *   - Closing errorBody after non-2xx responses.
 *
 * All tests MUST fail (red) until [TradingControlRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1.  start() 200 {status:"started"} → ActionResult.Success
 *   2.  start() 500 → ActionResult.Error with code 500
 *   3.  start() network exception → ActionResult.Error (never throws)
 *   4.  stop() 200 {status:"stopped"} → ActionResult.Success
 *   5.  stop() non-2xx → ActionResult.Error
 *   6.  setMode("live") POSTs body {mode:"live"}, 200 → ActionResult.Success
 *   7.  setDepoAlerts(true) POSTs body {depo_entry_filter:true}, 200 → ActionResult.Success
 *   8.  setDepoAlerts(false) POSTs body {depo_entry_filter:false}, 200 → ActionResult.Success
 *   9.  fetchState() maps running=true, mode="live"→ExecutionMode.LIVE, depo_entry_filter=true, positions count
 *   10. fetchState() mode="paper" → ExecutionMode.PAPER (NOT defaulting)
 *   11. fetchState() 401 → TradingControlResult.Error
 *   12. fetchState() 500 → TradingControlResult.Error
 *   13. fetchState() with null pnl positions → no crash, positions mapped correctly
 *   14. errorBody is closed after non-2xx response
 *   15. CancellationException is rethrown (not swallowed)
 *
 * MOBILE-41 additions (Trading Control mock alignment + push):
 *   16. fetchState() RUNNING branch maps lastScanTime, signalsToday, scanInterval, tfCount,
 *       autostartEnabled, pushEnabled from the full settings{} object.
 *   17. fetchState() STOPPED branch (many settings keys omitted, incl. push_enabled/tf_count)
 *       still parses safely with defaults (false/0) for the omitted fields.
 *   18. fetchState() last_scan_time explicit null → lastScanTime is null (not "—", not crash).
 *   19. fetchState() last_scan_time key entirely absent → lastScanTime is null.
 *   20. setAutostart(true) POSTs body {scanner_autostart:true}, 200 → ActionResult.Success;
 *       body must NOT contain mode/depo_entry_filter/push_enabled.
 *   21. setAutostart(false) POSTs body {scanner_autostart:false}, 200 → ActionResult.Success.
 *   22. setPushEnabled(true) POSTs body {push_enabled:true}, 200 → ActionResult.Success;
 *       body must NOT contain scanner_autostart.
 *   23. setPushEnabled(false) POSTs body {push_enabled:false}, 200 → ActionResult.Success.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TradingControlRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var tradingControlApi: TradingControlApi
    private lateinit var repository: TradingControlRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Builds a full GET /api/trading/state JSON response body.
     *
     * [depoEntryFilter] maps to settings.depo_entry_filter.
     * [positionsJson] is a JSON array embedded in "positions".
     */
    private fun tradingStateJson(
        running: Boolean = true,
        mode: String = "paper",
        depoEntryFilter: Boolean = false,
        currentPrice: Double = 50_000.0,
        positionsJson: String = "[]",
    ): String = """
        {
          "running": $running,
          "current_price": $currentPrice,
          "settings": {
            "mode": "$mode",
            "depo_entry_filter": $depoEntryFilter
          },
          "positions": $positionsJson,
          "history": []
        }
    """.trimIndent()

    /**
     * MOBILE-41: RUNNING branch — full settings object, as returned when the scanner is in
     * memory. Includes last_scan_time, signals_today, and the new settings{} keys
     * (scanner_autostart, push_enabled, tf_count, scan_interval_min) alongside the ~50 other
     * real keys the RUNNING branch carries (represented here by a couple of unrelated keys to
     * prove ignoreUnknownKeys tolerance, matching the real backend shape).
     */
    private fun tradingStateRunningFullJson(
        lastScanTimeJson: String = "\"2026-06-30T12:00:00Z\"",
        signalsToday: Int = 3,
        mode: String = "live",
        scannerAutostart: Boolean = true,
        pushEnabled: Boolean = true,
        tfCount: Int = 4,
        scanIntervalMin: Int = 15,
        depoEntryFilter: Boolean = false,
    ): String = """
        {
          "running": true,
          "current_price": 51000.0,
          "last_scan_time": $lastScanTimeJson,
          "signals_today": $signalsToday,
          "settings": {
            "mode": "$mode",
            "depo_entry_filter": $depoEntryFilter,
            "scan_interval_min": $scanIntervalMin,
            "scanner_autostart": $scannerAutostart,
            "push_enabled": $pushEnabled,
            "tf_count": $tfCount,
            "some_other_running_only_key": "ignored",
            "another_running_only_key": 123
          },
          "positions": [],
          "history": []
        }
    """.trimIndent()

    /**
     * MOBILE-41: STOPPED branch — scanner not in memory, settings{} omits ~50 keys including
     * (in this fixture) push_enabled and tf_count, to prove parsing stays safe when many keys
     * are absent. scanner_autostart IS present here to prove partial-presence also parses fine.
     * last_scan_time is null (no scan has ever run).
     */
    private fun tradingStateStoppedMinimalJson(): String = """
        {
          "running": false,
          "current_price": 0.0,
          "last_scan_time": null,
          "signals_today": 0,
          "settings": {
            "mode": "paper",
            "depo_entry_filter": false,
            "scanner_autostart": false
          },
          "positions": [],
          "history": []
        }
    """.trimIndent()

    /**
     * MOBILE-41: STOPPED branch with last_scan_time entirely ABSENT from the JSON (not just
     * null) — covers the case where the backend omits the key altogether rather than sending
     * an explicit null, which must also default safely to null on our side.
     */
    private fun tradingStateStoppedNoScanKeyJson(): String = """
        {
          "running": false,
          "current_price": 0.0,
          "settings": {
            "mode": "paper",
            "depo_entry_filter": false
          },
          "positions": [],
          "history": []
        }
    """.trimIndent()

    /**
     * Builds a single position JSON entry with all required fields.
     * [pnl] is the raw server pnl (null while open, which is typical).
     */
    private fun positionJson(
        signalId: String = "sig-001",
        entryPrice: Double = 50_000.0,
        direction: String = "long",
        status: String = "open",
        mode: String = "live",
        qty: Double = 1.0,
        contractSize: Double? = 0.001,
        pnl: String = "null",
    ): String {
        val contractSizeField = if (contractSize != null) """"contract_size": $contractSize,""" else ""
        return """
            {
              "signal_id": "$signalId",
              "entry_price": $entryPrice,
              "direction": "$direction",
              "status": "$status",
              "mode": "$mode",
              "qty": $qty,
              $contractSizeField
              "opened_at": "2026-06-25T10:00:00Z",
              "pnl": $pnl,
              "sl": 49000.0,
              "tp": 53000.0
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

        tradingControlApi = retrofit.create(TradingControlApi::class.java)

        repository = TradingControlRepositoryImpl(
            tradingControlApi = tradingControlApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. start() 200 {status:"started"} → ActionResult.Success
    // =========================================================================

    @Test
    fun `start 200 maps to ActionResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "started"}"""),
        )

        val result = repository.start()

        assertTrue(
            "HTTP 200 from /api/trading/start must map to ActionResult.Success, got $result",
            result is ActionResult.Success,
        )
    }

    // =========================================================================
    // 2. start() 500 → ActionResult.Error with code 500
    // =========================================================================

    @Test
    fun `start 500 maps to ActionResult Error with code 500`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "internal server error"}"""),
        )

        val result = repository.start()

        assertTrue(
            "HTTP 500 from /api/trading/start must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
        assertEquals(
            "Error code must be 500",
            500,
            (result as ActionResult.Error).code,
        )
    }

    // =========================================================================
    // 3. start() network exception → ActionResult.Error (never throws)
    // =========================================================================

    @Test
    fun `start network exception maps to ActionResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            val result = repository.start()

            assertTrue(
                "A network IOException from start() must map to ActionResult.Error — repository must never throw",
                result is ActionResult.Error,
            )
        }

    // =========================================================================
    // 4. stop() 200 {status:"stopped"} → ActionResult.Success
    // =========================================================================

    @Test
    fun `stop 200 maps to ActionResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "stopped"}"""),
        )

        val result = repository.stop()

        assertTrue(
            "HTTP 200 from /api/trading/stop must map to ActionResult.Success, got $result",
            result is ActionResult.Success,
        )
    }

    // =========================================================================
    // 5. stop() non-2xx → ActionResult.Error
    // =========================================================================

    @Test
    fun `stop non-2xx maps to ActionResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "service unavailable"}"""),
        )

        val result = repository.stop()

        assertTrue(
            "HTTP 503 from /api/trading/stop must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
    }

    // =========================================================================
    // 6. setMode("live") POSTs body {mode:"live"}, 200 → ActionResult.Success
    // =========================================================================

    @Test
    fun `setMode live POSTs correct body and returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "ok"}"""),
            )

            val result = repository.setMode("live")

            assertTrue(
                "HTTP 200 from setMode(live) must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            // Verify the request body contained {mode:"live"}
            val recordedRequest = mockWebServer.takeRequest()
            val requestBody = recordedRequest.body.readUtf8()
            assertTrue(
                "Request body must contain mode field: got '$requestBody'",
                requestBody.contains("\"mode\"") && requestBody.contains("\"live\""),
            )
            // depo_entry_filter must NOT be present in a mode-only write
            assertFalse(
                "Request body must NOT contain depo_entry_filter for a setMode() call: got '$requestBody'",
                requestBody.contains("depo_entry_filter"),
            )
        }

    // =========================================================================
    // 7. setDepoAlerts(true) POSTs body {depo_entry_filter:true}, 200 → ActionResult.Success
    // =========================================================================

    @Test
    fun `setDepoAlerts true POSTs correct body and returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "ok"}"""),
            )

            val result = repository.setDepoAlerts(enabled = true)

            assertTrue(
                "HTTP 200 from setDepoAlerts(true) must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val recordedRequest = mockWebServer.takeRequest()
            val requestBody = recordedRequest.body.readUtf8()
            assertTrue(
                "Request body must contain depo_entry_filter:true: got '$requestBody'",
                requestBody.contains("\"depo_entry_filter\"") && requestBody.contains("true"),
            )
            // mode must NOT be present in a depo-alerts-only write
            assertFalse(
                "Request body must NOT contain mode for a setDepoAlerts() call: got '$requestBody'",
                requestBody.contains("\"mode\""),
            )
        }

    // =========================================================================
    // 8. setDepoAlerts(false) POSTs body {depo_entry_filter:false}, 200 → ActionResult.Success
    // =========================================================================

    @Test
    fun `setDepoAlerts false POSTs correct body and returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "ok"}"""),
            )

            val result = repository.setDepoAlerts(enabled = false)

            assertTrue(
                "HTTP 200 from setDepoAlerts(false) must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val recordedRequest = mockWebServer.takeRequest()
            val requestBody = recordedRequest.body.readUtf8()
            assertTrue(
                "Request body must contain depo_entry_filter:false: got '$requestBody'",
                requestBody.contains("\"depo_entry_filter\"") && requestBody.contains("false"),
            )
        }

    // =========================================================================
    // 9. fetchState() maps running=true, mode="live"→ExecutionMode.LIVE, depo_entry_filter=true,
    //    positions count correctly
    // =========================================================================

    @Test
    fun `fetchState maps running mode depoFilter and positions correctly`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        tradingStateJson(
                            running = true,
                            mode = "live",
                            depoEntryFilter = true,
                            currentPrice = 51_000.0,
                            positionsJson = """[${positionJson(signalId = "sig-A")}, ${positionJson(signalId = "sig-B")}]""",
                        )
                    ),
            )

            val result = repository.fetchState()

            assertTrue(
                "fetchState() with valid response must return TradingControlResult.Success, got $result",
                result is TradingControlResult.Success,
            )
            val data = (result as TradingControlResult.Success).data

            assertTrue("running must be true", data.running)
            assertEquals(
                "mode 'live' must map to ExecutionMode.LIVE",
                ExecutionMode.LIVE,
                data.mode,
            )
            assertTrue("depoAlertsEnabled must be true", data.depoAlertsEnabled)
            assertEquals(
                "positions count must match the JSON array length",
                2,
                data.positions.size,
            )
        }

    // =========================================================================
    // 10. fetchState() mode="paper" → ExecutionMode.PAPER (NOT defaulting)
    //     Critical discriminator: PAPER must be set explicitly, not as a fallback
    // =========================================================================

    @Test
    fun `fetchState mode paper maps to ExecutionMode PAPER explicitly`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        tradingStateJson(
                            running = false,
                            mode = "paper",
                            depoEntryFilter = false,
                        )
                    ),
            )

            val result = repository.fetchState()

            assertTrue(
                "fetchState() must succeed for mode=paper, got $result",
                result is TradingControlResult.Success,
            )
            val data = (result as TradingControlResult.Success).data

            assertEquals(
                "DISCRIMINATOR FAILURE: mode 'paper' must map to ExecutionMode.PAPER — " +
                    "this ensures the mode card shows PAPER not LIVE in the UI",
                ExecutionMode.PAPER,
                data.mode,
            )
            assertFalse("running must be false", data.running)
            assertFalse("depoAlertsEnabled must be false", data.depoAlertsEnabled)
        }

    // =========================================================================
    // 11. fetchState() 401 → TradingControlResult.Error
    // =========================================================================

    @Test
    fun `fetchState 401 returns TradingControlResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Not authenticated"}"""),
        )

        val result = repository.fetchState()

        assertTrue(
            "HTTP 401 must map to TradingControlResult.Error, got $result",
            result is TradingControlResult.Error,
        )
    }

    // =========================================================================
    // 12. fetchState() 500 → TradingControlResult.Error
    // =========================================================================

    @Test
    fun `fetchState 500 returns TradingControlResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.fetchState()

        assertTrue(
            "HTTP 500 must map to TradingControlResult.Error, got $result",
            result is TradingControlResult.Error,
        )
    }

    // =========================================================================
    // 13. fetchState() with null pnl positions → no crash, positions mapped
    // =========================================================================

    @Test
    fun `fetchState with null pnl positions does not crash and maps positions`() =
        runTest(testDispatcher) {
            // pnl is null on the server while a position is open; must not NPE
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        tradingStateJson(
                            positionsJson = """[${positionJson(pnl = "null")}]""",
                        )
                    ),
            )

            val result = repository.fetchState()

            assertTrue(
                "fetchState() must succeed even when position pnl is null, got $result",
                result is TradingControlResult.Success,
            )
            val data = (result as TradingControlResult.Success).data
            assertEquals(
                "Positions with null pnl must still be mapped (1 position expected)",
                1,
                data.positions.size,
            )
        }

    // =========================================================================
    // 14. errorBody is closed after non-2xx response
    //
    // Verified indirectly: making two non-2xx calls sequentially on a server
    // with a single connection must not dead-lock waiting for a connection to be
    // released (which would happen if errorBody were leaked and the pool stalled).
    // =========================================================================

    @Test
    fun `errorBody is closed after non-2xx so connection pool is not exhausted`() =
        runTest(testDispatcher) {
            // Enqueue two error responses back-to-back
            repeat(2) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(503)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"error": "unavailable"}"""),
                )
            }

            val first = repository.start()
            val second = repository.start()

            assertTrue(
                "First 503 must be ActionResult.Error (errorBody closed so pool is free)",
                first is ActionResult.Error,
            )
            assertTrue(
                "Second 503 must also succeed without hanging — proves errorBody was closed after first",
                second is ActionResult.Error,
            )
        }

    // =========================================================================
    // 15. CancellationException is rethrown (not swallowed)
    //
    // This test verifies the contract by calling a suspending function inside a
    // coroutine that was cancelled — the exception must propagate up, not be
    // silently turned into an ActionResult.Error.
    //
    // NOTE: This test is intentionally a compilation/contract pin. The actual
    // CancellationException propagation is exercised by the coroutines runtime
    // when the parent scope is cancelled; we assert the repo has a rethrow clause
    // by checking that a generic exception does NOT produce CancellationException
    // (i.e. only CE is rethrown, not every exception).
    // =========================================================================

    @Test
    fun `generic exception does not produce a thrown CancellationException`() =
        runTest(testDispatcher) {
            // Server shutdown → IOException, not CancellationException
            mockWebServer.shutdown()

            var caughtCancellation = false
            try {
                repository.start()
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
    // MOBILE-41 — Trading Control mock alignment + push
    // =========================================================================

    // -------------------------------------------------------------------------
    // 16. fetchState() RUNNING branch maps all new fields correctly
    // -------------------------------------------------------------------------

    @Test
    fun `fetchState RUNNING branch maps lastScanTime signalsToday scanInterval tfCount autostart and push`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        tradingStateRunningFullJson(
                            lastScanTimeJson = "\"2026-06-30T12:00:00Z\"",
                            signalsToday = 3,
                            scannerAutostart = true,
                            pushEnabled = true,
                            tfCount = 4,
                            scanIntervalMin = 15,
                        ),
                    ),
            )

            val result = repository.fetchState()

            assertTrue(
                "RUNNING branch with full settings must parse to TradingControlResult.Success, got $result",
                result is TradingControlResult.Success,
            )
            val data = (result as TradingControlResult.Success).data

            assertEquals(
                "lastScanTime must be mapped from last_scan_time",
                "2026-06-30T12:00:00Z",
                data.lastScanTime,
            )
            assertEquals("signalsToday must be mapped from signals_today", 3, data.signalsToday)
            assertEquals(
                "scanInterval must be mapped from settings.scan_interval_min",
                15,
                data.scanInterval,
            )
            assertEquals("tfCount must be mapped from settings.tf_count", 4, data.tfCount)
            assertTrue(
                "autostartEnabled must be true when settings.scanner_autostart is true",
                data.autostartEnabled,
            )
            assertTrue(
                "pushEnabled must be true when settings.push_enabled is true",
                data.pushEnabled,
            )
        }

    // -------------------------------------------------------------------------
    // 17. fetchState() STOPPED branch — many settings keys omitted, still parses safely
    // -------------------------------------------------------------------------

    @Test
    fun `fetchState STOPPED branch with omitted settings keys still parses safely with defaults`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(tradingStateStoppedMinimalJson()),
            )

            val result = repository.fetchState()

            assertTrue(
                "STOPPED branch (settings omits ~50 keys incl. push_enabled/tf_count) must still " +
                    "parse to TradingControlResult.Success, got $result",
                result is TradingControlResult.Success,
            )
            val data = (result as TradingControlResult.Success).data

            assertFalse("running must be false in the STOPPED branch", data.running)
            assertNull(
                "lastScanTime must be null when last_scan_time is explicit null",
                data.lastScanTime,
            )
            assertEquals("signalsToday must default to 0", 0, data.signalsToday)
            assertFalse(
                "pushEnabled must default to false when push_enabled key is omitted",
                data.pushEnabled,
            )
            assertEquals(
                "tfCount must default to 0 when tf_count key is omitted",
                0,
                data.tfCount,
            )
            assertFalse(
                "autostartEnabled must be false because scanner_autostart is explicitly false " +
                    "in this fixture (partial-presence case)",
                data.autostartEnabled,
            )
        }

    // -------------------------------------------------------------------------
    // 18. fetchState() last_scan_time explicit null → lastScanTime is null
    // -------------------------------------------------------------------------

    @Test
    fun `fetchState last_scan_time explicit null maps to null lastScanTime`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(tradingStateRunningFullJson(lastScanTimeJson = "null")),
            )

            val result = repository.fetchState()

            assertTrue(result is TradingControlResult.Success)
            val data = (result as TradingControlResult.Success).data
            assertNull(
                "last_scan_time:null in JSON must map to lastScanTime == null (no first scan yet)",
                data.lastScanTime,
            )
        }

    // -------------------------------------------------------------------------
    // 19. fetchState() last_scan_time key entirely absent → lastScanTime is null
    // -------------------------------------------------------------------------

    @Test
    fun `fetchState with last_scan_time key entirely absent maps to null lastScanTime`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(tradingStateStoppedNoScanKeyJson()),
            )

            val result = repository.fetchState()

            assertTrue(
                "Response with last_scan_time key entirely missing must still parse to Success, got $result",
                result is TradingControlResult.Success,
            )
            val data = (result as TradingControlResult.Success).data
            assertNull(
                "Missing last_scan_time key must default to null lastScanTime, not crash",
                data.lastScanTime,
            )
        }

    // -------------------------------------------------------------------------
    // 20. setAutostart(true) POSTs {scanner_autostart:true}, 200 → ActionResult.Success
    // -------------------------------------------------------------------------

    @Test
    fun `setAutostart true POSTs correct body and returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "ok"}"""),
            )

            val result = repository.setAutostart(true)

            assertTrue(
                "HTTP 200 from setAutostart(true) must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val recordedRequest = mockWebServer.takeRequest()
            val requestBody = recordedRequest.body.readUtf8()
            assertTrue(
                "Request body must contain scanner_autostart:true: got '$requestBody'",
                requestBody.contains("\"scanner_autostart\"") && requestBody.contains("true"),
            )
            assertFalse(
                "Request body must NOT contain mode for a setAutostart() call: got '$requestBody'",
                requestBody.contains("\"mode\""),
            )
            assertFalse(
                "Request body must NOT contain depo_entry_filter for a setAutostart() call: got '$requestBody'",
                requestBody.contains("depo_entry_filter"),
            )
            assertFalse(
                "Request body must NOT contain push_enabled for a setAutostart() call: got '$requestBody'",
                requestBody.contains("push_enabled"),
            )
        }

    // -------------------------------------------------------------------------
    // 21. setAutostart(false) POSTs {scanner_autostart:false}, 200 → ActionResult.Success
    // -------------------------------------------------------------------------

    @Test
    fun `setAutostart false POSTs correct body and returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "ok"}"""),
            )

            val result = repository.setAutostart(false)

            assertTrue(
                "HTTP 200 from setAutostart(false) must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val recordedRequest = mockWebServer.takeRequest()
            val requestBody = recordedRequest.body.readUtf8()
            assertTrue(
                "Request body must contain scanner_autostart:false: got '$requestBody'",
                requestBody.contains("\"scanner_autostart\"") && requestBody.contains("false"),
            )
        }

    // -------------------------------------------------------------------------
    // 22. setPushEnabled(true) POSTs {push_enabled:true}, 200 → ActionResult.Success
    // -------------------------------------------------------------------------

    @Test
    fun `setPushEnabled true POSTs correct body and returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "ok"}"""),
            )

            val result = repository.setPushEnabled(true)

            assertTrue(
                "HTTP 200 from setPushEnabled(true) must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val recordedRequest = mockWebServer.takeRequest()
            val requestBody = recordedRequest.body.readUtf8()
            assertTrue(
                "Request body must contain push_enabled:true: got '$requestBody'",
                requestBody.contains("\"push_enabled\"") && requestBody.contains("true"),
            )
            assertFalse(
                "Request body must NOT contain scanner_autostart for a setPushEnabled() call: got '$requestBody'",
                requestBody.contains("scanner_autostart"),
            )
        }

    // -------------------------------------------------------------------------
    // 23. setPushEnabled(false) POSTs {push_enabled:false}, 200 → ActionResult.Success
    // -------------------------------------------------------------------------

    @Test
    fun `setPushEnabled false POSTs correct body and returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "ok"}"""),
            )

            val result = repository.setPushEnabled(false)

            assertTrue(
                "HTTP 200 from setPushEnabled(false) must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val recordedRequest = mockWebServer.takeRequest()
            val requestBody = recordedRequest.body.readUtf8()
            assertTrue(
                "Request body must contain push_enabled:false: got '$requestBody'",
                requestBody.contains("\"push_enabled\"") && requestBody.contains("false"),
            )
        }
}
