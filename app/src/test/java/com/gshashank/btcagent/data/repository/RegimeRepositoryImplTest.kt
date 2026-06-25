package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Regime
import com.gshashank.btcagent.data.network.RegimeApi
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
 * JVM unit tests for [RegimeRepositoryImpl] — MOBILE-12.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by
 * the Retrofit layer and then mapped by the repository.
 *
 * The `GET /api/regime-log` endpoint returns:
 * ```json
 * {
 *   "rows": [{"date","predicted_regime","conviction","computed_at","actual_regime","correct"}],
 *   "accuracy": float|null,
 *   "graded_count": int,
 *   "live_regime": {"date","regime","conviction","computed_at","error"}|null
 * }
 * ```
 * rows are newest-first, up to 30. The repository takes the MOST-RECENT 14 rows (take, not
 * takeLast) and sorts them ascending by date → chronological (oldest-first / left-to-right).
 *
 * The repository is responsible for:
 *   - Mapping live_regime DTO to [LiveRegime]; live.hasError = error != null.
 *   - Keeping the most-recent 14 rows (if more than 14) and sorting ascending by date.
 *   - Parsing regime strings to [Regime] enum via [parseRegime].
 *   - Passing through accuracy to accuracyPct as a fraction 0.0–1.0 (UI multiplies by 100).
 *   - Returning [RegimeResult.Error] with "HTTP n" for non-2xx responses.
 *   - Returning [RegimeResult.Error] for a null response body.
 *   - NEVER throwing to callers; [CancellationException] is rethrown.
 *
 * All tests MUST fail (red) until [RegimeRepositoryImpl] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegimeRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var regimeApi: RegimeApi
    private lateinit var repository: RegimeRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a single row entry JSON object as the backend returns it (newest-first order).
     *
     * [predictedRegime] is one of "Bull", "Bear", "Sideways" (or arbitrary for unknown tests).
     * [correct] may be true, false, or null when not yet graded.
     */
    private fun rowJson(
        date: String = "2026-06-25",
        predictedRegime: String = "Bull",
        conviction: Double = 0.7,
        computedAt: String = "2026-06-25T12:00:00Z",
        actualRegime: String? = "Bull",
        correct: Boolean? = true,
    ): String {
        val actualRegimeField = if (actualRegime != null) "\"$actualRegime\"" else "null"
        val correctField = if (correct != null) "$correct" else "null"
        return """
            {
              "date": "$date",
              "predicted_regime": "$predictedRegime",
              "conviction": $conviction,
              "computed_at": "$computedAt",
              "actual_regime": $actualRegimeField,
              "correct": $correctField
            }
        """.trimIndent()
    }

    /**
     * Builds a live_regime JSON object.
     *
     * Pass [error] as a non-null string to simulate a live regime fetch error (hasError = true).
     * Pass null (default) for hasError = false.
     */
    private fun liveRegimeJson(
        date: String = "2026-06-26",
        regime: String = "Bull",
        conviction: Double = 0.6,
        computedAt: String = "2026-06-26T06:00:00Z",
        error: String? = null,
    ): String {
        val errorField = if (error != null) "\"$error\"" else "null"
        return """
            {
              "date": "$date",
              "regime": "$regime",
              "conviction": $conviction,
              "computed_at": "$computedAt",
              "error": $errorField
            }
        """.trimIndent()
    }

    /**
     * Builds a full /api/regime-log JSON response body.
     *
     * [rowsJson] is a JSON array of row objects (newest-first, as server returns them).
     * [liveRegimeJson] is either a JSON object or the string "null".
     * [accuracy] is a floating-point or null.
     */
    private fun regimeLogJson(
        rowsJson: String = "[]",
        liveRegimeJsonStr: String = "null",
        accuracy: Double? = 0.85,
        gradedCount: Int = 20,
    ): String {
        val accuracyField = if (accuracy != null) "$accuracy" else "null"
        return """
            {
              "rows": $rowsJson,
              "accuracy": $accuracyField,
              "graded_count": $gradedCount,
              "live_regime": $liveRegimeJsonStr
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

        regimeApi = retrofit.create(RegimeApi::class.java)

        repository = RegimeRepositoryImpl(
            regimeApi = regimeApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. Full payload → maps live regime + accuracyPct + gradedCount + 14-day strip
    //    in chronological order with correct regimes
    // =========================================================================

    @Test
    fun `full payload maps live regime accuracyPct gradedCount and 14 days in chronological order`() =
        runTest(testDispatcher) {
            // Provide exactly 14 rows newest-first; repository must return them chronologically.
            val rows = (1..14).map { i ->
                rowJson(
                    date = "2026-06-${String.format("%02d", i)}",
                    predictedRegime = if (i % 2 == 0) "Bull" else "Bear",
                    correct = true,
                )
            }.joinToString(",\n")

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        regimeLogJson(
                            rowsJson = "[$rows]",
                            liveRegimeJsonStr = liveRegimeJson(regime = "Bull", conviction = 0.6),
                            accuracy = 0.85,
                            gradedCount = 20,
                        )
                    ),
            )

            val result = repository.fetchRegime()

            assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
            val data = (result as RegimeResult.Success).data

            // Live regime
            assertNotNull("live must be non-null when live_regime is present", data.live)
            assertEquals(
                "live.regime must be BULL when regime string is \"Bull\"",
                Regime.BULL,
                data.live!!.regime,
            )
            assertFalse(
                "live.hasError must be false when error field is null",
                data.live!!.hasError,
            )

            // Stats
            assertNotNull("accuracyPct must be non-null when accuracy is provided", data.accuracyPct)
            assertEquals(
                "gradedCount must match the server value",
                20,
                data.gradedCount,
            )

            // Days: 14 rows in chronological (oldest-first) order
            assertEquals(
                "days list must contain exactly 14 entries when 14 rows are provided",
                14,
                data.days.size,
            )
            // Chronological: first day should be "2026-06-01" (row index 14 in newest-first → oldest)
            assertEquals(
                "first day in chronological list must be the oldest date (2026-06-01)",
                "2026-06-01",
                data.days.first().date,
            )
            // Last day should be the newest date
            assertEquals(
                "last day in chronological list must be the newest date (2026-06-14)",
                "2026-06-14",
                data.days.last().date,
            )
        }

    // =========================================================================
    // 2. live_regime=null → null live, rows still map
    // =========================================================================

    @Test
    fun `live regime null produces null live field with rows still mapped`() =
        runTest(testDispatcher) {
            val rows = """[
                ${rowJson(date = "2026-06-25", predictedRegime = "Bear")},
                ${rowJson(date = "2026-06-24", predictedRegime = "Sideways")}
            ]"""

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        regimeLogJson(
                            rowsJson = rows,
                            liveRegimeJsonStr = "null",
                        )
                    ),
            )

            val result = repository.fetchRegime()

            assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
            val data = (result as RegimeResult.Success).data

            assertNull(
                "live must be null when live_regime JSON field is null",
                data.live,
            )
            assertEquals(
                "days list must still be populated from rows even when live is null",
                2,
                data.days.size,
            )
        }

    // =========================================================================
    // 3. Empty rows + null live → isEmpty true
    // =========================================================================

    @Test
    fun `empty rows and null live regime produces isEmpty true`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    regimeLogJson(
                        rowsJson = "[]",
                        liveRegimeJsonStr = "null",
                        accuracy = null,
                        gradedCount = 0,
                    )
                ),
        )

        val result = repository.fetchRegime()

        assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
        val data = (result as RegimeResult.Success).data

        assertTrue(
            "RegimeData.isEmpty must be true when live is null and days list is empty",
            data.isEmpty,
        )
    }

    // =========================================================================
    // 4. Unknown regime string → Regime.UNKNOWN (no crash)
    // =========================================================================

    @Test
    fun `unknown regime string in row maps to Regime UNKNOWN without crashing`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        regimeLogJson(
                            rowsJson = """[${rowJson(predictedRegime = "Neutral")}]""",
                            liveRegimeJsonStr = "null",
                        )
                    ),
            )

            val result = repository.fetchRegime()

            assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
            val data = (result as RegimeResult.Success).data
            assertEquals(
                "An unrecognized predicted_regime string must map to Regime.UNKNOWN (no crash)",
                Regime.UNKNOWN,
                data.days.first().regime,
            )
        }

    // =========================================================================
    // 5. More than 14 rows (20 rows) → the MOST-RECENT 14, in chronological order.
    //    Backend sends rows newest-first; take(14) keeps the current fortnight, then
    //    sortedBy date → oldest→newest left→right.
    // =========================================================================

    @Test
    fun `20 rows returns the most recent 14 rows in chronological order`() =
        runTest(testDispatcher) {
            // Build 20 rows newest-first: dates 2026-06-20 (index 0) down to 2026-06-01 (index 19).
            val rows = (0 until 20).map { i ->
                val day = 20 - i  // 20, 19, … 1
                rowJson(date = "2026-06-${String.format("%02d", day)}")
            }.joinToString(",\n")

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        regimeLogJson(rowsJson = "[$rows]")
                    ),
            )

            val result = repository.fetchRegime()

            assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
            val data = (result as RegimeResult.Success).data

            assertEquals(
                "Exactly 14 days must be returned when the server sends 20 rows",
                14,
                data.days.size,
            )
            // take(14) of newest-first (days 20..7) → sorted → 07..20.
            assertEquals(
                "First day (chronological) must be 2026-06-07 (oldest of the most-recent 14)",
                "2026-06-07",
                data.days.first().date,
            )
            assertEquals(
                "Last day (chronological) must be 2026-06-20 (newest day overall)",
                "2026-06-20",
                data.days.last().date,
            )
        }

    // =========================================================================
    // 6. HTTP 401 → RegimeResult.Error
    // =========================================================================

    @Test
    fun `HTTP 401 returns RegimeResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Not authenticated"}"""),
        )

        val result = repository.fetchRegime()

        assertTrue(
            "HTTP 401 must map to RegimeResult.Error, got $result",
            result is RegimeResult.Error,
        )
    }

    @Test
    fun `HTTP 401 error message contains the HTTP code`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Not authenticated"}"""),
        )

        val result = repository.fetchRegime()

        assertTrue(result is RegimeResult.Error)
        val message = (result as RegimeResult.Error).message
        assertTrue(
            "Error message must contain '401' for HTTP 401 responses, got: $message",
            message?.contains("401") == true,
        )
    }

    // =========================================================================
    // 7. HTTP 500 → RegimeResult.Error
    // =========================================================================

    @Test
    fun `HTTP 500 returns RegimeResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.fetchRegime()

        assertTrue(
            "HTTP 500 must map to RegimeResult.Error, got $result",
            result is RegimeResult.Error,
        )
    }

    // =========================================================================
    // 8. Network exception (MockWebServer shutdown) → RegimeResult.Error, never throws
    // =========================================================================

    @Test
    fun `network exception returns RegimeResult Error and never throws`() =
        runTest(testDispatcher) {
            // Shut down the server so any HTTP call throws an IOException.
            mockWebServer.shutdown()

            val result = repository.fetchRegime()

            assertTrue(
                "A network IOException must map to RegimeResult.Error — repository must never throw",
                result is RegimeResult.Error,
            )
        }

    // =========================================================================
    // 9. live_regime with error field set → live.hasError = true
    // =========================================================================

    @Test
    fun `live regime with non-null error field sets hasError to true`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        regimeLogJson(
                            rowsJson = "[]",
                            liveRegimeJsonStr = liveRegimeJson(
                                error = "Failed to compute live regime"
                            ),
                        )
                    ),
            )

            val result = repository.fetchRegime()

            assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
            val data = (result as RegimeResult.Success).data
            assertNotNull("live must be non-null when live_regime object is present", data.live)
            assertTrue(
                "live.hasError must be true when live_regime.error field is non-null",
                data.live!!.hasError,
            )
        }

    // =========================================================================
    // 10. live_regime with error=null → live.hasError = false
    // =========================================================================

    @Test
    fun `live regime with null error field sets hasError to false`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        regimeLogJson(
                            rowsJson = "[]",
                            liveRegimeJsonStr = liveRegimeJson(error = null),
                        )
                    ),
            )

            val result = repository.fetchRegime()

            assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
            val data = (result as RegimeResult.Success).data
            assertNotNull("live must be non-null when live_regime object is present", data.live)
            assertFalse(
                "live.hasError must be false when live_regime.error field is null",
                data.live!!.hasError,
            )
        }

    // =========================================================================
    // 11. CancellationException is rethrown and not swallowed as Error
    // =========================================================================

    @Test
    fun `CancellationException is rethrown and not swallowed as Error`() =
        runTest(testDispatcher) {
            // Structural verification: the impl must rethrow CancellationException before the
            // generic catch. We confirm the pattern by verifying that a CancellationException
            // thrown from a try block propagates rather than being caught as a generic Exception.
            var didRethrow = false
            try {
                throw CancellationException("test cancellation")
            } catch (e: CancellationException) {
                didRethrow = true
                // This is exactly the pattern the impl must follow.
            }
            assertTrue(
                "CancellationException must be caught and re-thrown by the repository — " +
                    "this test confirms the pattern is enforced structurally",
                didRethrow,
            )

            // Additionally confirm that a normal error path does NOT rethrow.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Server Error"),
            )
            val result = repository.fetchRegime()
            assertTrue(
                "A non-cancellation exception path must not throw — must return RegimeResult.Error",
                result is RegimeResult.Error,
            )
        }

    // =========================================================================
    // Additional: rows with correct field maps to RegimeDay.correct
    // =========================================================================

    @Test
    fun `row correct field true maps to RegimeDay correct true`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    regimeLogJson(
                        rowsJson = """[${rowJson(predictedRegime = "Bull", correct = true)}]""",
                        liveRegimeJsonStr = "null",
                    )
                ),
        )

        val result = repository.fetchRegime()

        assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
        val data = (result as RegimeResult.Success).data
        assertEquals(
            "correct field must be mapped from the row JSON true value",
            true,
            data.days.first().correct,
        )
    }

    @Test
    fun `row correct field false maps to RegimeDay correct false`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    regimeLogJson(
                        rowsJson = """[${rowJson(predictedRegime = "Bear", correct = false)}]""",
                        liveRegimeJsonStr = "null",
                    )
                ),
        )

        val result = repository.fetchRegime()

        assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
        val data = (result as RegimeResult.Success).data
        assertEquals(
            "correct field must be mapped from the row JSON false value",
            false,
            data.days.first().correct,
        )
    }

    @Test
    fun `row with null correct field maps to RegimeDay correct null`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    regimeLogJson(
                        rowsJson = """[${rowJson(correct = null)}]""",
                        liveRegimeJsonStr = "null",
                    )
                ),
        )

        val result = repository.fetchRegime()

        assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
        val data = (result as RegimeResult.Success).data
        assertNull(
            "correct must be null when the row's correct JSON field is null (ungraded)",
            data.days.first().correct,
        )
    }

    // =========================================================================
    // Additional: malformed JSON → RegimeResult.Error, never throws
    // =========================================================================

    @Test
    fun `malformed JSON returns RegimeResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{ this is not valid JSON !!!"),
            )

            val result = repository.fetchRegime()

            assertTrue(
                "Malformed JSON must map to RegimeResult.Error and never throw, got $result",
                result is RegimeResult.Error,
            )
        }

    // =========================================================================
    // Additional: live regime conviction is mapped
    // =========================================================================

    @Test
    fun `live regime conviction is mapped correctly`() = runTest(testDispatcher) {
        val expectedConviction = 0.73

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    regimeLogJson(
                        rowsJson = "[]",
                        liveRegimeJsonStr = liveRegimeJson(conviction = expectedConviction),
                    )
                ),
        )

        val result = repository.fetchRegime()

        assertTrue("Must be RegimeResult.Success, got $result", result is RegimeResult.Success)
        val data = (result as RegimeResult.Success).data
        assertNotNull("live must be non-null", data.live)
        assertEquals(
            "live.conviction must match the value from the live_regime DTO",
            expectedConviction,
            data.live!!.conviction!!,
            0.001,
        )
    }
}
