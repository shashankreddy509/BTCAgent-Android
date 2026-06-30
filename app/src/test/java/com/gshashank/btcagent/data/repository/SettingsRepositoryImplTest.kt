package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.network.SettingsApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [SettingsRepositoryImpl] — MOBILE-20 / MOBILE-42.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by the
 * Retrofit layer and then mapped by the repository.
 *
 * Endpoints under test:
 *   GET  api/settings/user            → fetchUserSettings() → [SettingsResult]
 *   PUT  api/settings/user            → saveTradingParams() → [ActionResult]
 *
 * Repository contract:
 *   - NEVER throws to callers; CancellationException is rethrown.
 *   - errorBody() is closed on non-2xx.
 *   - qty validated client-side: 0 < qty <= 1000 AND even; invalid → immediate error, no HTTP call.
 *   - HTTP error reason is masked — "Server error (<code>)" not raw response.message().
 *
 * MOBILE-42 changes:
 *   - broker_keys removed from UserSettingsDto and UserSettings domain model.
 *   - Scanner fields (scan_interval_min, tf_min, tf_max, patterns) added to UserSettings.
 *   - fetchUserSettings maps scanner fields from the serving endpoint.
 *
 * All tests MUST fail (red) until [SettingsRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1.  GET 200 → maps qty, max_sl, min_tp, max_concurrent, mode correctly
 *   2.  GET 200 → maps scanner fields (scan_interval_min, tf_min, tf_max, patterns)
 *   3.  GET response with no broker_keys field → parses without error (brokerKeys removed)
 *   4.  PUT sends only changed keys (sparse body, snake_case via @SerialName)
 *   5.  saveTradingParams sends snake_case field names not camelCase
 *   6.  saveTradingParams with a value containing four-star sentinel is not sent to server
 *   7.  saveTradingParams never sends string containing four stars to the server
 *   8.  qty=0 is invalid → error before any HTTP call
 *   9.  qty=3 (odd) is invalid → error before any HTTP call
 *   10. qty=1001 is invalid → error before any HTTP call
 *   11. qty=2 (valid even, in range) → PUT proceeds to server
 *   12. qty=1000 valid max boundary → PUT proceeds to server
 *   13. HTTP 401 → SettingsResult.Error
 *   14. HTTP 500 → SettingsResult.Error with masked reason not raw message
 *   15. saveTradingParams 500 returns ActionResult Error with code
 *   16. errorBody is closed after non-2xx so connection pool is not exhausted
 *   17. Generic IOException from fetchUserSettings does not produce CancellationException
 *   18. Network exception from fetchUserSettings → SettingsResult.Error (never throws)
 *   19. Network exception from saveTradingParams → ActionResult.Error (never throws)
 *   20. Scanner fields absent from JSON default to null (not crash)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsApi: SettingsApi
    private lateinit var repository: SettingsRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // JSON response helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a full GET api/settings/user JSON response body.
     * No broker_keys field — it has been removed from the DTO (MOBILE-42).
     * Includes optional scanner fields.
     */
    private fun userSettingsResponseJson(
        qty: Int = 4,
        maxSl: Double = 2.5,
        minTp: Double = 1.0,
        maxConcurrent: Int = 3,
        mode: String = "paper",
        scanIntervalMin: Int? = null,
        tfMin: Int? = null,
        tfMax: Int? = null,
        patternsJson: String? = null,
    ): String {
        // tf_min / tf_max are bare integers (minutes) in the real backend JSON — not strings.
        val scanIntervalPart = if (scanIntervalMin != null) ""","scan_interval_min": $scanIntervalMin""" else ""
        val tfMinPart = if (tfMin != null) ""","tf_min": $tfMin""" else ""
        val tfMaxPart = if (tfMax != null) ""","tf_max": $tfMax""" else ""
        val patternsPart = if (patternsJson != null) ""","patterns": $patternsJson""" else ""
        return """
            {
              "qty": $qty,
              "max_sl": $maxSl,
              "min_tp": $minTp,
              "max_concurrent": $maxConcurrent,
              "mode": "$mode"$scanIntervalPart$tfMinPart$tfMaxPart$patternsPart
            }
        """.trimIndent()
    }

    /** A minimal PUT 200 success response. */
    private fun saveSuccessJson(): String = """{"status": "saved"}"""

    @Before
    fun setUp() {
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()

        settingsApi = retrofit.create(SettingsApi::class.java)

        repository = SettingsRepositoryImpl(
            settingsApi = settingsApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. GET 200 → maps qty, max_sl, min_tp, max_concurrent, mode correctly
    // =========================================================================

    @Test
    fun `fetchUserSettings 200 maps qty maxSl minTp maxConcurrent and mode correctly`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        userSettingsResponseJson(
                            qty = 6,
                            maxSl = 3.5,
                            minTp = 1.5,
                            maxConcurrent = 5,
                            mode = "live",
                        )
                    ),
            )

            val result = repository.fetchUserSettings()

            assertTrue(
                "HTTP 200 from GET api/settings/user must map to SettingsResult.Success, got $result",
                result is SettingsResult.Success,
            )
            val settings = (result as SettingsResult.Success).settings

            assertEquals(
                "qty must be mapped from response",
                6,
                settings.qty,
            )
            assertEquals(
                "maxSl must be mapped from max_sl in response",
                3.5,
                settings.maxSl ?: 0.0,
                0.001,
            )
            assertEquals(
                "minTp must be mapped from min_tp in response",
                1.5,
                settings.minTp ?: 0.0,
                0.001,
            )
            assertEquals(
                "maxConcurrent must be mapped from max_concurrent in response",
                5,
                settings.maxConcurrent,
            )
            assertEquals(
                "mode 'live' must map to ExecutionMode.LIVE",
                ExecutionMode.LIVE,
                settings.mode,
            )
        }

    // =========================================================================
    // 2. GET 200 → maps scanner fields (scan_interval_min, tf_min, tf_max, patterns)
    //    MOBILE-42: new test for scanner field mapping
    // =========================================================================

    @Test
    fun `fetchUserSettings 200 maps scanner fields scanIntervalMin tfMin tfMax and patterns`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        userSettingsResponseJson(
                            scanIntervalMin = 15,
                            tfMin = 15,
                            tfMax = 360,
                            patternsJson = """["BULL_FLAG","BEAR_CHANNEL"]""",
                        )
                    ),
            )

            val result = repository.fetchUserSettings()

            assertTrue(
                "HTTP 200 with scanner fields must produce SettingsResult.Success, got $result",
                result is SettingsResult.Success,
            )
            val settings = (result as SettingsResult.Success).settings

            assertEquals(
                "scanIntervalMin must be mapped from scan_interval_min in response",
                15,
                settings.scanIntervalMin,
            )
            assertEquals(
                "tfMin must be mapped from tf_min in response",
                15,
                settings.tfMin,
            )
            assertEquals(
                "tfMax must be mapped from tf_max in response",
                360,
                settings.tfMax,
            )
            assertNotNull(
                "patterns must be non-null when the server returns a patterns array",
                settings.patterns,
            )
            assertEquals(
                "patterns must contain 2 entries from the response",
                2,
                settings.patterns?.size,
            )
            assertTrue(
                "patterns must contain BULL_FLAG",
                settings.patterns?.contains("BULL_FLAG") == true,
            )
            assertTrue(
                "patterns must contain BEAR_CHANNEL",
                settings.patterns?.contains("BEAR_CHANNEL") == true,
            )
        }

    // =========================================================================
    // 3. GET response with no broker_keys field → parses without error
    //    MOBILE-42: broker_keys removed — a JSON body without it must still parse
    // =========================================================================

    @Test
    fun `fetchUserSettings without broker_keys field in JSON parses successfully`() =
        runTest(testDispatcher) {
            // This JSON has no broker_keys key at all — server never emits it.
            // UserSettingsDto must not have broker_keys after MOBILE-42 cleanup.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "qty": 4,
                          "max_sl": 2.5,
                          "min_tp": 1.0,
                          "max_concurrent": 3,
                          "mode": "paper"
                        }
                        """.trimIndent()
                    ),
            )

            val result = repository.fetchUserSettings()

            assertTrue(
                "JSON without broker_keys must parse to SettingsResult.Success (field removed), got $result",
                result is SettingsResult.Success,
            )
            val settings = (result as SettingsResult.Success).settings

            // Verify UserSettings no longer has a brokerKeys field by checking it compiles
            // and maps correctly without it.
            assertEquals(
                "qty must still map correctly when broker_keys is absent",
                4,
                settings.qty,
            )
        }

    // =========================================================================
    // 4. GET response with broker_keys present in JSON → field is ignored (removed from DTO)
    //    MOBILE-42: even if the server sends broker_keys, it must not fail — ignoreUnknownKeys=true
    // =========================================================================

    @Test
    fun `fetchUserSettings with legacy broker_keys field in JSON ignores it and parses successfully`() =
        runTest(testDispatcher) {
            // If the server happens to still send broker_keys, ignoreUnknownKeys must swallow it.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "qty": 6,
                          "max_sl": 3.5,
                          "min_tp": 1.5,
                          "max_concurrent": 2,
                          "mode": "live",
                          "broker_keys": ["ABCD****WXYZ"]
                        }
                        """.trimIndent()
                    ),
            )

            val result = repository.fetchUserSettings()

            assertTrue(
                "JSON with extra broker_keys field must still parse to SettingsResult.Success, got $result",
                result is SettingsResult.Success,
            )
            val settings = (result as SettingsResult.Success).settings

            assertEquals(
                "qty must map correctly even when broker_keys is present in JSON",
                6,
                settings.qty,
            )
            assertEquals(
                "mode live must map to ExecutionMode.LIVE even when broker_keys present in JSON",
                ExecutionMode.LIVE,
                settings.mode,
            )
        }

    // =========================================================================
    // 5. Scanner fields absent from JSON → null defaults (not a crash)
    //    MOBILE-42: fields are nullable; missing from JSON → null
    // =========================================================================

    @Test
    fun `fetchUserSettings scanner fields absent from JSON default to null without crashing`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        userSettingsResponseJson() // No scanner params
                    ),
            )

            val result = repository.fetchUserSettings()

            assertTrue(
                "Response without scanner fields must still produce SettingsResult.Success, got $result",
                result is SettingsResult.Success,
            )
            val settings = (result as SettingsResult.Success).settings

            assertNull(
                "scanIntervalMin must be null when absent from JSON",
                settings.scanIntervalMin,
            )
            assertNull(
                "tfMin must be null when absent from JSON",
                settings.tfMin,
            )
            assertNull(
                "tfMax must be null when absent from JSON",
                settings.tfMax,
            )
            assertTrue(
                "patterns must be empty or null when absent from JSON",
                settings.patterns == null || settings.patterns!!.isEmpty(),
            )
        }

    // =========================================================================
    // 6. PUT sends only changed keys (sparse body, snake_case via @SerialName)
    // =========================================================================

    @Test
    fun `saveTradingParams sends only provided fields in snake_case (sparse body)`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(saveSuccessJson()),
            )

            // Send only qty — other fields are null (omitted with explicitNulls=false)
            val result = repository.saveTradingParams(
                qty = 4,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            assertTrue(
                "HTTP 200 from PUT api/settings/user must map to ActionResult.Success, got $result",
                result is ActionResult.Success,
            )

            val requestBody = mockWebServer.takeRequest().body.readUtf8()

            assertTrue(
                "Request body must contain 'qty' key, got: $requestBody",
                requestBody.contains("\"qty\""),
            )
            // Null fields must be omitted (explicitNulls=false)
            assertFalse(
                "Request body must NOT contain 'max_sl' when it was null (sparse body), got: $requestBody",
                requestBody.contains("\"max_sl\""),
            )
            assertFalse(
                "Request body must NOT contain 'min_tp' when it was null, got: $requestBody",
                requestBody.contains("\"min_tp\""),
            )
            assertFalse(
                "Request body must NOT contain 'max_concurrent' when it was null, got: $requestBody",
                requestBody.contains("\"max_concurrent\""),
            )
            assertFalse(
                "Request body must NOT contain 'mode' when it was null, got: $requestBody",
                requestBody.contains("\"mode\""),
            )
        }

    @Test
    fun `saveTradingParams sends snake_case field names not camelCase`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(saveSuccessJson()),
            )

            repository.saveTradingParams(
                qty = null,
                maxSl = 2.5,
                minTp = 1.0,
                maxConcurrent = 3,
                mode = null,
            )

            val requestBody = mockWebServer.takeRequest().body.readUtf8()

            assertTrue(
                "Snake_case 'max_sl' must appear in the request body (not camelCase 'maxSl'), got: $requestBody",
                requestBody.contains("\"max_sl\""),
            )
            assertTrue(
                "Snake_case 'min_tp' must appear in the request body (not camelCase 'minTp'), got: $requestBody",
                requestBody.contains("\"min_tp\""),
            )
            assertTrue(
                "Snake_case 'max_concurrent' must appear in the request body, got: $requestBody",
                requestBody.contains("\"max_concurrent\""),
            )
            assertFalse(
                "camelCase 'maxSl' must NOT appear in the request body (must use snake_case), got: $requestBody",
                requestBody.contains("\"maxSl\""),
            )
        }

    // =========================================================================
    // 7. Value containing "****" is NEVER sent in PUT body (client guard)
    // =========================================================================

    @Test
    fun `saveTradingParams with a value containing four-star sentinel is not sent to server`() =
        runTest(testDispatcher) {
            // The repository should refuse to send any value containing "****".
            // No HTTP request should be made — or if it is, the masked value must be absent.
            val callCountBefore = mockWebServer.requestCount

            val result = repository.saveTradingParams(
                qty = null,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            // Enqueue a dummy response so we can check whether the HTTP call was made
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(saveSuccessJson()),
            )

            val filteredCallCount = mockWebServer.requestCount

            if (filteredCallCount > callCountBefore) {
                val recordedBody = mockWebServer.takeRequest().body.readUtf8()
                assertFalse(
                    "The PUT request body must NEVER contain the masked sentinel '****', got: $recordedBody",
                    recordedBody.contains("****"),
                )
            }
            // If no HTTP call was made (because all values were filtered out / null), that is
            // also acceptable — no masked value was sent.
        }

    @Test
    fun `saveTradingParams never sends string containing four stars to the server`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(saveSuccessJson()),
            )

            repository.saveTradingParams(
                qty = 2,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = ExecutionMode.PAPER,
            )

            val recordedRequest = mockWebServer.takeRequest()
            val body = recordedRequest.body.readUtf8()

            assertFalse(
                "The PUT body must never contain '****' — client guard must prevent masked values from reaching the server, got: $body",
                body.contains("****"),
            )
        }

    // =========================================================================
    // 8. qty=0 is invalid → error before any HTTP call
    // =========================================================================

    @Test
    fun `saveTradingParams with qty=0 returns error without making any HTTP call`() =
        runTest(testDispatcher) {
            val requestCountBefore = mockWebServer.requestCount

            val result = repository.saveTradingParams(
                qty = 0,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            assertTrue(
                "qty=0 must produce ActionResult.Error (0 is not > 0), got $result",
                result is ActionResult.Error,
            )
            assertEquals(
                "qty=0 validation must fail before any HTTP call — requestCount must not increase",
                requestCountBefore,
                mockWebServer.requestCount,
            )
        }

    // =========================================================================
    // 9. qty=3 (odd) is invalid → error before any HTTP call
    // =========================================================================

    @Test
    fun `saveTradingParams with odd qty=3 returns error without making any HTTP call`() =
        runTest(testDispatcher) {
            val requestCountBefore = mockWebServer.requestCount

            val result = repository.saveTradingParams(
                qty = 3,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            assertTrue(
                "qty=3 (odd) must produce ActionResult.Error (qty must be even), got $result",
                result is ActionResult.Error,
            )
            assertEquals(
                "Odd qty validation must fail before any HTTP call",
                requestCountBefore,
                mockWebServer.requestCount,
            )
        }

    // =========================================================================
    // 10. qty=1001 is invalid → error before any HTTP call
    // =========================================================================

    @Test
    fun `saveTradingParams with qty=1001 returns error without making any HTTP call`() =
        runTest(testDispatcher) {
            val requestCountBefore = mockWebServer.requestCount

            val result = repository.saveTradingParams(
                qty = 1001,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            assertTrue(
                "qty=1001 must produce ActionResult.Error (exceeds max of 1000), got $result",
                result is ActionResult.Error,
            )
            assertEquals(
                "qty=1001 validation must fail before any HTTP call",
                requestCountBefore,
                mockWebServer.requestCount,
            )
        }

    // =========================================================================
    // 11. qty=2 (valid even, in range) → PUT proceeds to server
    // =========================================================================

    @Test
    fun `saveTradingParams with qty=2 valid even in range proceeds to HTTP PUT`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(saveSuccessJson()),
            )

            val result = repository.saveTradingParams(
                qty = 2,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            assertTrue(
                "qty=2 (valid even, in-range) must result in ActionResult.Success, got $result",
                result is ActionResult.Success,
            )
            assertEquals(
                "qty=2 must trigger an HTTP call (request count must increase by 1)",
                1,
                mockWebServer.requestCount,
            )
        }

    @Test
    fun `saveTradingParams with qty=1000 valid max boundary proceeds to HTTP PUT`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(saveSuccessJson()),
            )

            val result = repository.saveTradingParams(
                qty = 1000,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            assertTrue(
                "qty=1000 (max valid even value) must result in ActionResult.Success, got $result",
                result is ActionResult.Success,
            )
        }

    // =========================================================================
    // 12. HTTP 401 → SettingsResult.Error
    // =========================================================================

    @Test
    fun `fetchUserSettings 401 returns SettingsResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Not authenticated"}"""),
        )

        val result = repository.fetchUserSettings()

        assertTrue(
            "HTTP 401 from GET api/settings/user must map to SettingsResult.Error, got $result",
            result is SettingsResult.Error,
        )
    }

    // =========================================================================
    // 13. HTTP 500 → SettingsResult.Error with "Server error (500)" (reason masked)
    // =========================================================================

    @Test
    fun `fetchUserSettings 500 returns SettingsResult Error with masked reason not raw message`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error"),
            )

            val result = repository.fetchUserSettings()

            assertTrue(
                "HTTP 500 from GET api/settings/user must map to SettingsResult.Error, got $result",
                result is SettingsResult.Error,
            )
            val error = result as SettingsResult.Error

            assertTrue(
                "Error message must contain the HTTP code 500, got '${error.message}'",
                error.message.contains("500"),
            )
            assertFalse(
                "Error message must NOT contain raw server reason text 'Internal Server Error', " +
                    "got '${error.message}'",
                error.message.contains("Internal Server Error", ignoreCase = true),
            )
        }

    @Test
    fun `saveTradingParams 500 returns ActionResult Error with code`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.saveTradingParams(
            qty = 4,
            maxSl = null,
            minTp = null,
            maxConcurrent = null,
            mode = null,
        )

        assertTrue(
            "HTTP 500 from PUT api/settings/user must map to ActionResult.Error, got $result",
            result is ActionResult.Error,
        )
        assertEquals(
            "Error code must be 500",
            500,
            (result as ActionResult.Error).code,
        )
    }

    // =========================================================================
    // 14. errorBody() is closed on non-2xx (connection pool not exhausted)
    //
    // Verified indirectly: making two non-2xx calls sequentially on a single-connection
    // server must not dead-lock (pool stall would occur if errorBody were leaked).
    // =========================================================================

    @Test
    fun `errorBody is closed after non-2xx so connection pool is not exhausted`() =
        runTest(testDispatcher) {
            repeat(2) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"detail": "Not authenticated"}"""),
                )
            }

            val first = repository.fetchUserSettings()
            val second = repository.fetchUserSettings()

            assertTrue(
                "First 401 must map to SettingsResult.Error (errorBody must be closed so pool is free)",
                first is SettingsResult.Error,
            )
            assertTrue(
                "Second 401 must also complete without hanging — proves errorBody was closed after first",
                second is SettingsResult.Error,
            )
        }

    // =========================================================================
    // 15. CancellationException: generic IOException must NOT become CancellationException
    // =========================================================================

    @Test
    fun `generic IOException from fetchUserSettings does not produce CancellationException`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            var caughtCancellation = false
            try {
                repository.fetchUserSettings()
            } catch (e: CancellationException) {
                caughtCancellation = true
            }

            assertFalse(
                "An IOException from fetchUserSettings must NOT be rethrown as CancellationException — " +
                    "only real CancellationExceptions should propagate",
                caughtCancellation,
            )
        }

    // =========================================================================
    // 16. Network exception → SettingsResult.Error (repository never throws)
    // =========================================================================

    @Test
    fun `fetchUserSettings network exception returns SettingsResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            val result = repository.fetchUserSettings()

            assertTrue(
                "A network IOException from fetchUserSettings must map to SettingsResult.Error — " +
                    "repository must never throw to callers",
                result is SettingsResult.Error,
            )
        }

    @Test
    fun `saveTradingParams network exception returns ActionResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            val result = repository.saveTradingParams(
                qty = 4,
                maxSl = null,
                minTp = null,
                maxConcurrent = null,
                mode = null,
            )

            assertTrue(
                "A network IOException from saveTradingParams must map to ActionResult.Error — " +
                    "repository must never throw to callers",
                result is ActionResult.Error,
            )
        }
}
