package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ScanDirection
import com.gshashank.btcagent.data.network.ScannerApi
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [ScannerRepositoryImpl] — MOBILE-8.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by
 * the Retrofit layer and then mapped by the repository.
 *
 * The repository is responsible for:
 *   - Mapping JSON signal fields (tf, pattern, bars_ago, bar_open_price, depo_line) to [ScanSignal].
 *   - Deriving [ScanDirection] from the pattern name:
 *       "Morning Star" → Bullish, "Evening Star" → Bearish, "4-Flag" (or any other) → Neutral.
 *   - Mapping depo_line: non-null JSON value → non-null depoLine field; JSON null → null field.
 *   - NEVER throwing to callers; CancellationException is rethrown.
 *
 * All tests MUST fail (red) until [ScannerRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1.  200 valid JSON → [ScannerResult.Success] with correct signal fields.
 *   2a. "Morning Star" pattern → [ScanDirection.Bullish]  (direction discriminator).
 *   2b. "Evening Star" pattern → [ScanDirection.Bearish]  (direction discriminator).
 *   2c. "4-Flag" pattern → [ScanDirection.Neutral]        (direction discriminator).
 *   3.  depo_line non-null in JSON → depoLine field populated in [ScanSignal].
 *   4.  depo_line null in JSON → depoLine is null in [ScanSignal].
 *   5.  HTTP 401 → [ScannerResult.Error].
 *   6.  HTTP 500 → [ScannerResult.Error].
 *   7.  Empty/malformed body → [ScannerResult.Error] (never throws).
 *   8.  Network exception → [ScannerResult.Error] (never throws).
 *   9.  triggerScan() with {"status":"started"} → [ActionResult.Success].
 *   10. triggerScan() with {"status":"already_running"} → [ActionResult.Success].
 *   11. triggerScan() with error code 403 → [ActionResult.Error].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var scannerApi: ScannerApi
    private lateinit var repository: ScannerRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Builds a minimal valid GET /api/scan JSON body.
     *
     * [resultsJson] is a JSON array of scan result objects.
     */
    private fun scanJson(
        timestamp: String = "2026-04-12T03:40:05Z",
        resultsJson: String = "[]",
    ): String = """
        {
          "timestamp": "$timestamp",
          "results": $resultsJson
        }
    """.trimIndent()

    /**
     * Builds a single scan result JSON object as the backend returns it.
     *
     * Per the verified backend contract:
     *   tf, pattern, bars_ago, bar_open_time, bar_open_price, depo_line (nullable), timestamp.
     * No symbol field (BTC-only scanner). No direction field (derived client-side).
     */
    private fun scanResultJson(
        tf: String = "30m",
        pattern: String = "Morning Star",
        barsAgo: Int = 2,
        barOpenTime: String = "2026-04-11T22:10:00Z",
        barOpenPrice: Double = 73_493.1,
        depoLine: String = "null",          // pass numeric string like "73508.0" or "null"
        timestamp: String = "2026-04-12T03:40:05Z",
    ): String = """
        {
          "tf": "$tf",
          "pattern": "$pattern",
          "bars_ago": $barsAgo,
          "bar_open_time": "$barOpenTime",
          "bar_open_price": $barOpenPrice,
          "depo_line": $depoLine,
          "timestamp": "$timestamp"
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

        scannerApi = retrofit.create(ScannerApi::class.java)

        repository = ScannerRepositoryImpl(
            scannerApi = scannerApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. 200 valid JSON → ScannerResult.Success with correct signal fields
    // =========================================================================

    @Test
    fun `200 valid JSON returns ScannerResult Success with correct signal fields`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        scanJson(
                            timestamp = "2026-04-12T03:40:05Z",
                            resultsJson = """[
                                ${scanResultJson(
                                    tf = "95m",
                                    pattern = "4-Flag",
                                    barsAgo = 3,
                                    barOpenTime = "2026-04-11T22:10:00Z",
                                    barOpenPrice = 73_493.1,
                                    depoLine = "73508.0",
                                )}
                            ]""",
                        )
                    ),
            )

            val result = repository.fetchScan()

            assertTrue("Must be Success, got $result", result is ScannerResult.Success)
            val data = (result as ScannerResult.Success).data

            assertEquals(
                "timestamp must be mapped from the top-level JSON field",
                "2026-04-12T03:40:05Z",
                data.timestamp,
            )
            assertEquals("signals list must have 1 entry", 1, data.signals.size)

            val signal = data.signals[0]
            assertEquals("timeframe must be mapped from tf", "95m", signal.timeframe)
            assertEquals("pattern must be mapped from pattern", "4-Flag", signal.pattern)
            assertEquals("barsAgo must be mapped from bars_ago", 3, signal.barsAgo)
            assertEquals(
                "openPrice must be mapped from bar_open_price",
                73_493.1,
                signal.openPrice,
                0.001,
            )
            assertEquals(
                "depoLine must be 73508.0 when JSON depo_line is non-null",
                73_508.0,
                signal.depoLine!!,
                0.001,
            )
        }

    // =========================================================================
    // 2a. Direction discriminator: "Morning Star" → ScanDirection.Bullish
    // =========================================================================

    @Test
    fun `Morning Star pattern maps to ScanDirection Bullish`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    scanJson(
                        resultsJson = """[${scanResultJson(pattern = "Morning Star")}]"""
                    )
                ),
        )

        val result = repository.fetchScan()

        assertTrue("Must be Success, got $result", result is ScannerResult.Success)
        val signal = (result as ScannerResult.Success).data.signals[0]
        assertEquals(
            "Morning Star must map to ScanDirection.Bullish — this is the critical direction discriminator",
            ScanDirection.Bullish,
            signal.direction,
        )
    }

    // =========================================================================
    // 2b. Direction discriminator: "Evening Star" → ScanDirection.Bearish
    // =========================================================================

    @Test
    fun `Evening Star pattern maps to ScanDirection Bearish`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    scanJson(
                        resultsJson = """[${scanResultJson(pattern = "Evening Star")}]"""
                    )
                ),
        )

        val result = repository.fetchScan()

        assertTrue("Must be Success, got $result", result is ScannerResult.Success)
        val signal = (result as ScannerResult.Success).data.signals[0]
        assertEquals(
            "Evening Star must map to ScanDirection.Bearish — this is the critical direction discriminator",
            ScanDirection.Bearish,
            signal.direction,
        )
    }

    // =========================================================================
    // 2c. Direction discriminator: "4-Flag" → ScanDirection.Neutral
    // =========================================================================

    @Test
    fun `4-Flag pattern maps to ScanDirection Neutral`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    scanJson(
                        resultsJson = """[${scanResultJson(pattern = "4-Flag")}]"""
                    )
                ),
        )

        val result = repository.fetchScan()

        assertTrue("Must be Success, got $result", result is ScannerResult.Success)
        val signal = (result as ScannerResult.Success).data.signals[0]
        assertEquals(
            "4-Flag must map to ScanDirection.Neutral — this is the critical direction discriminator",
            ScanDirection.Neutral,
            signal.direction,
        )
    }

    // =========================================================================
    // 3. depo_line non-null in JSON → depoLine field populated in ScanSignal
    // =========================================================================

    @Test
    fun `non-null depo_line in JSON populates depoLine field`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    scanJson(
                        resultsJson = """[
                            ${scanResultJson(pattern = "4-Flag", depoLine = "73508.0")}
                        ]""",
                    )
                ),
        )

        val result = repository.fetchScan()

        assertTrue("Must be Success, got $result", result is ScannerResult.Success)
        val signal = (result as ScannerResult.Success).data.signals[0]
        assertNotNull(
            "depoLine must be non-null when JSON depo_line is a numeric value",
            signal.depoLine,
        )
        assertEquals(
            "depoLine must equal the JSON depo_line value",
            73_508.0,
            signal.depoLine!!,
            0.001,
        )
    }

    // =========================================================================
    // 4. depo_line null in JSON → depoLine is null in ScanSignal
    // =========================================================================

    @Test
    fun `null depo_line in JSON results in null depoLine field`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    scanJson(
                        resultsJson = """[
                            ${scanResultJson(pattern = "Morning Star", depoLine = "null")}
                        ]""",
                    )
                ),
        )

        val result = repository.fetchScan()

        assertTrue("Must be Success, got $result", result is ScannerResult.Success)
        val signal = (result as ScannerResult.Success).data.signals[0]
        assertNull(
            "depoLine must be null when JSON depo_line is null",
            signal.depoLine,
        )
    }

    // =========================================================================
    // 5. HTTP 401 → ScannerResult.Error
    // =========================================================================

    @Test
    fun `HTTP 401 returns ScannerResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Not authenticated"}"""),
        )

        val result = repository.fetchScan()

        assertTrue(
            "HTTP 401 must map to ScannerResult.Error, got $result",
            result is ScannerResult.Error,
        )
    }

    // =========================================================================
    // 6. HTTP 500 → ScannerResult.Error
    // =========================================================================

    @Test
    fun `HTTP 500 returns ScannerResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.fetchScan()

        assertTrue(
            "HTTP 500 must map to ScannerResult.Error, got $result",
            result is ScannerResult.Error,
        )
    }

    // =========================================================================
    // 7. Malformed JSON → ScannerResult.Error (never throws)
    // =========================================================================

    @Test
    fun `malformed JSON returns ScannerResult Error and never throws`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{ this is not valid JSON !!!"),
        )

        // Must not throw — the repository catches parse exceptions and maps to Error.
        val result = repository.fetchScan()

        assertTrue(
            "Malformed JSON must map to ScannerResult.Error and never throw, got $result",
            result is ScannerResult.Error,
        )
    }

    @Test
    fun `empty body returns ScannerResult Error and never throws`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(""),
        )

        val result = repository.fetchScan()

        assertTrue(
            "Empty body must map to ScannerResult.Error and never throw, got $result",
            result is ScannerResult.Error,
        )
    }

    // =========================================================================
    // 8. Network exception → ScannerResult.Error (never throws)
    // =========================================================================

    @Test
    fun `network exception returns ScannerResult Error and never throws`() = runTest(testDispatcher) {
        // Shut down the server so any HTTP call throws an IOException.
        mockWebServer.shutdown()

        val result = repository.fetchScan()

        assertTrue(
            "A network IOException must map to ScannerResult.Error — repository must never throw",
            result is ScannerResult.Error,
        )
    }

    // =========================================================================
    // 9. triggerScan() with {"status":"started"} → ActionResult.Success
    // =========================================================================

    @Test
    fun `triggerScan with status started returns ActionResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "started"}"""),
        )

        val result = repository.triggerScan()

        assertTrue(
            "triggerScan() with status=started must return ActionResult.Success, got $result",
            result is ActionResult.Success,
        )
    }

    // =========================================================================
    // 10. triggerScan() with {"status":"already_running"} → ActionResult.Success
    // =========================================================================

    @Test
    fun `triggerScan with status already_running returns ActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"status": "already_running"}"""),
            )

            val result = repository.triggerScan()

            assertTrue(
                "triggerScan() with status=already_running must return ActionResult.Success, got $result",
                result is ActionResult.Success,
            )
        }

    // =========================================================================
    // 11. triggerScan() with error code 403 → ActionResult.Error
    // =========================================================================

    @Test
    fun `triggerScan with HTTP 403 returns ActionResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Admin access required"}"""),
        )

        val result = repository.triggerScan()

        assertTrue(
            "triggerScan() with HTTP 403 must return ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
    }
}

// =============================================================================
// ScanDirectionDerivationTest — standalone prominence for the main correctness risk
// =============================================================================

/**
 * Standalone discriminator tests for the client-side [ScanDirection] derivation logic.
 *
 * The backend has NO direction field — the scanner screen derives direction from the pattern name:
 *   "Morning Star"  → [ScanDirection.Bullish]
 *   "Evening Star"  → [ScanDirection.Bearish]
 *   "4-Flag"        → [ScanDirection.Neutral]
 *   (anything else) → [ScanDirection.Neutral] (safe default)
 *
 * This is the primary correctness risk called out in MOBILE-8 — a wrong discriminator would
 * miscolour signal rows and break the Bullish/Bearish filter chips. These tests are kept
 * standalone (not merged into [ScannerRepositoryImplTest]) so a CI failure immediately
 * identifies the discriminator as the faulty component.
 *
 * All tests MUST fail (red) until [ScannerRepositoryImpl] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanDirectionDerivationTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var scannerApi: ScannerApi
    private lateinit var repository: ScannerRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun singlePatternScanBody(pattern: String): String = """
        {
          "timestamp": "2026-04-12T03:40:05Z",
          "results": [
            {
              "tf": "30m",
              "pattern": "$pattern",
              "bars_ago": 1,
              "bar_open_time": "2026-04-12T00:00:00Z",
              "bar_open_price": 70000.0,
              "depo_line": null,
              "timestamp": "2026-04-12T03:40:05Z"
            }
          ]
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

        scannerApi = retrofit.create(ScannerApi::class.java)

        repository = ScannerRepositoryImpl(
            scannerApi = scannerApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `direction discriminator - Morning Star yields Bullish`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(singlePatternScanBody("Morning Star")),
        )

        val result = repository.fetchScan()

        assertTrue("Fetch must succeed", result is ScannerResult.Success)
        assertEquals(
            "DISCRIMINATOR FAILURE: Morning Star must derive ScanDirection.Bullish — " +
                "without this the Bullish filter chip will show no results",
            ScanDirection.Bullish,
            (result as ScannerResult.Success).data.signals[0].direction,
        )
    }

    @Test
    fun `direction discriminator - Evening Star yields Bearish`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(singlePatternScanBody("Evening Star")),
        )

        val result = repository.fetchScan()

        assertTrue("Fetch must succeed", result is ScannerResult.Success)
        assertEquals(
            "DISCRIMINATOR FAILURE: Evening Star must derive ScanDirection.Bearish — " +
                "without this the Bearish filter chip will show no results",
            ScanDirection.Bearish,
            (result as ScannerResult.Success).data.signals[0].direction,
        )
    }

    @Test
    fun `direction discriminator - 4-Flag yields Neutral`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(singlePatternScanBody("4-Flag")),
        )

        val result = repository.fetchScan()

        assertTrue("Fetch must succeed", result is ScannerResult.Success)
        assertEquals(
            "DISCRIMINATOR FAILURE: 4-Flag must derive ScanDirection.Neutral — " +
                "without this continuation signals will appear in directional filter chips",
            ScanDirection.Neutral,
            (result as ScannerResult.Success).data.signals[0].direction,
        )
    }

    @Test
    fun `direction discriminator - unknown pattern falls back to Neutral`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(singlePatternScanBody("Some Future Pattern")),
            )

            val result = repository.fetchScan()

            assertTrue("Fetch must succeed even with an unknown pattern", result is ScannerResult.Success)
            assertEquals(
                "An unrecognised pattern name must fall back to ScanDirection.Neutral (safe default)",
                ScanDirection.Neutral,
                (result as ScannerResult.Success).data.signals[0].direction,
            )
        }

    @Test
    fun `direction discriminator - all three patterns in one response derive independently`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "timestamp": "2026-04-12T03:40:05Z",
                          "results": [
                            {
                              "tf": "30m", "pattern": "Morning Star", "bars_ago": 1,
                              "bar_open_time": "2026-04-12T00:00:00Z",
                              "bar_open_price": 70000.0, "depo_line": null,
                              "timestamp": "2026-04-12T03:40:05Z"
                            },
                            {
                              "tf": "60m", "pattern": "Evening Star", "bars_ago": 2,
                              "bar_open_time": "2026-04-11T22:00:00Z",
                              "bar_open_price": 71000.0, "depo_line": 71100.0,
                              "timestamp": "2026-04-12T03:40:05Z"
                            },
                            {
                              "tf": "240m", "pattern": "4-Flag", "bars_ago": 3,
                              "bar_open_time": "2026-04-11T16:00:00Z",
                              "bar_open_price": 69000.0, "depo_line": null,
                              "timestamp": "2026-04-12T03:40:05Z"
                            }
                          ]
                        }
                        """.trimIndent()
                    ),
            )

            val result = repository.fetchScan()

            assertTrue("Fetch must succeed", result is ScannerResult.Success)
            val signals = (result as ScannerResult.Success).data.signals

            assertEquals("Must have 3 signals", 3, signals.size)

            val byPattern = signals.associateBy { it.pattern }

            assertEquals(
                "Morning Star → Bullish",
                ScanDirection.Bullish,
                byPattern["Morning Star"]?.direction,
            )
            assertEquals(
                "Evening Star → Bearish",
                ScanDirection.Bearish,
                byPattern["Evening Star"]?.direction,
            )
            assertEquals(
                "4-Flag → Neutral",
                ScanDirection.Neutral,
                byPattern["4-Flag"]?.direction,
            )
        }
}
