package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Regime
import com.gshashank.btcagent.data.network.MarkovApi
import com.gshashank.btcagent.data.network.MarkovTickersDto
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
 * JVM unit tests for [MarkovRepositoryImpl] — MOBILE-13.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed
 * by the Retrofit layer and then mapped by the repository.
 *
 * The `GET /api/markov/tickers` endpoint (Firebase-auth + allowlist) returns:
 * ```json
 * { "tickers": [ { "ticker","market","date","regime","conviction",
 *                  "stationary":[bear,sideways,bull],"error","accuracy","graded_count" } ],
 *   "last_refresh": str|null }
 * ```
 * Regime exact strings "Bull"/"Bear"/"Sideways". `stationary` = long-run [Bear,Sideways,Bull] probs.
 *
 * The repository is responsible for:
 *   - Mapping tickers, regime enum, conviction, StationaryDist from list of 3 doubles.
 *   - stationary list size != 3 → StationaryDist is null (no crash).
 *   - Unknown regime string → Regime.UNKNOWN (uses parseRegime from RegimeData.kt).
 *   - error field set on a ticker → hasError = true.
 *   - Empty tickers list → MarkovData.isEmpty == true, result is Success.
 *   - HTTP 404 → MarkovResult.Error.
 *   - HTTP 500 → MarkovResult.Error.
 *   - Null/empty body → MarkovResult.Error.
 *   - Network exception → MarkovResult.Error (never throws).
 *   - CancellationException is rethrown (not swallowed).
 *
 * All tests MUST fail (red) until [MarkovRepositoryImpl] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkovRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var markovApi: MarkovApi
    private lateinit var repository: MarkovRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private fun tickerJson(
        ticker: String = "BTC-USD",
        market: String = "crypto",
        date: String = "2026-06-25",
        regime: String? = "Bull",
        conviction: Double? = 0.82,
        stationary: List<Double>? = listOf(0.2, 0.3, 0.5),
        error: String? = null,
        accuracy: Double? = 0.75,
        gradedCount: Int = 30,
    ): String {
        val regimeField = if (regime != null) "\"regime\": \"$regime\"" else "\"regime\": null"
        val convictionField = if (conviction != null) "\"conviction\": $conviction" else "\"conviction\": null"
        val stationaryField = if (stationary != null) {
            "\"stationary\": [${stationary.joinToString(",")}]"
        } else {
            "\"stationary\": []"
        }
        val errorField = if (error != null) "\"error\": \"$error\"" else "\"error\": null"
        val accuracyField = if (accuracy != null) "\"accuracy\": $accuracy" else "\"accuracy\": null"
        return """
            {
              "ticker": "$ticker",
              "market": "$market",
              "date": "$date",
              $regimeField,
              $convictionField,
              $stationaryField,
              $errorField,
              $accuracyField,
              "graded_count": $gradedCount
            }
        """.trimIndent()
    }

    private fun markovResponseJson(
        tickers: List<String>,
        lastRefresh: String? = "2026-06-25T10:00:00Z",
    ): String {
        val tickersJson = tickers.joinToString(",\n")
        val lastRefreshField = if (lastRefresh != null) "\"$lastRefresh\"" else "null"
        return """
            {
              "tickers": [$tickersJson],
              "last_refresh": $lastRefreshField
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

        markovApi = retrofit.create(MarkovApi::class.java)

        repository = MarkovRepositoryImpl(
            markovApi = markovApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. Full payload with known regime strings → maps tickers, regime enum correctly,
    //    conviction, StationaryDist from list of 3 doubles
    // =========================================================================

    @Test
    fun `full payload maps tickers regime conviction and StationaryDist from 3-element list`() =
        runTest(testDispatcher) {
            val btcTicker = tickerJson(
                ticker = "BTC-USD",
                market = "crypto",
                regime = "Bull",
                conviction = 0.82,
                stationary = listOf(0.15, 0.25, 0.60),
                accuracy = 0.75,
                gradedCount = 30,
            )
            val ethTicker = tickerJson(
                ticker = "ETH-USD",
                market = "crypto",
                regime = "Bear",
                conviction = 0.65,
                stationary = listOf(0.50, 0.30, 0.20),
                accuracy = 0.68,
                gradedCount = 28,
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(markovResponseJson(listOf(btcTicker, ethTicker))),
            )

            val result = repository.fetchTickers()

            assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
            val data = (result as MarkovResult.Success).data

            assertEquals("Must have 2 tickers", 2, data.tickers.size)

            val btc = data.tickers[0]
            assertEquals("BTC-USD ticker must match", "BTC-USD", btc.ticker)
            assertEquals("BTC-USD regime must be BULL", Regime.BULL, btc.regime)
            assertNotNull("BTC-USD conviction must be non-null", btc.conviction)
            assertEquals("BTC-USD conviction must match", 0.82, btc.conviction!!, 0.001)
            assertNotNull("BTC-USD stationary must be non-null for 3-element list", btc.stationary)
            assertEquals("BTC-USD bear stationary prob must match", 0.15, btc.stationary!!.bear, 0.001)
            assertEquals("BTC-USD sideways stationary prob must match", 0.25, btc.stationary!!.sideways, 0.001)
            assertEquals("BTC-USD bull stationary prob must match", 0.60, btc.stationary!!.bull, 0.001)
            assertFalse("BTC-USD hasError must be false when no error", btc.hasError)

            val eth = data.tickers[1]
            assertEquals("ETH-USD ticker must match", "ETH-USD", eth.ticker)
            assertEquals("ETH-USD regime must be BEAR", Regime.BEAR, eth.regime)
        }

    // =========================================================================
    // 2. stationary list size != 3 → StationaryDist is null (no crash)
    // =========================================================================

    @Test
    fun `stationary list with size not 3 maps to null StationaryDist without crashing`() =
        runTest(testDispatcher) {
            // Use a 2-element list — should produce null StationaryDist.
            val tickerWithShortStationary = tickerJson(
                ticker = "BTC-USD",
                stationary = listOf(0.5, 0.5),
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(markovResponseJson(listOf(tickerWithShortStationary))),
            )

            val result = repository.fetchTickers()

            assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
            val data = (result as MarkovResult.Success).data
            val ticker = data.tickers[0]
            assertNull(
                "stationary must be null when the list does not have exactly 3 elements",
                ticker.stationary,
            )
        }

    // =========================================================================
    // 3. Unknown regime string → Regime.UNKNOWN (uses parseRegime from RegimeData.kt)
    // =========================================================================

    @Test
    fun `unknown regime string maps to Regime UNKNOWN without crashing`() =
        runTest(testDispatcher) {
            val tickerWithUnknownRegime = tickerJson(
                ticker = "SOL-USD",
                regime = "Sideways_Trending", // not a known enum value
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(markovResponseJson(listOf(tickerWithUnknownRegime))),
            )

            val result = repository.fetchTickers()

            assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
            val data = (result as MarkovResult.Success).data
            assertEquals(
                "An unrecognized regime string must map to Regime.UNKNOWN",
                Regime.UNKNOWN,
                data.tickers[0].regime,
            )
        }

    // =========================================================================
    // 4. error field set on a ticker → hasError = true
    // =========================================================================

    @Test
    fun `ticker with error field set maps hasError to true`() = runTest(testDispatcher) {
        val tickerWithError = tickerJson(
            ticker = "BTC-USD",
            regime = "Bull",
            error = "Model inference failed: timeout",
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(markovResponseJson(listOf(tickerWithError))),
        )

        val result = repository.fetchTickers()

        assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
        val data = (result as MarkovResult.Success).data
        assertTrue(
            "hasError must be true when error field is non-null",
            data.tickers[0].hasError,
        )
    }

    // =========================================================================
    // 5. Empty tickers list → MarkovData.isEmpty == true, result is Success
    // =========================================================================

    @Test
    fun `empty tickers list returns Success with MarkovData isEmpty true`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(markovResponseJson(emptyList())),
            )

            val result = repository.fetchTickers()

            assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
            val data = (result as MarkovResult.Success).data
            assertTrue(
                "MarkovData.isEmpty must be true when tickers list is empty",
                data.isEmpty,
            )
        }

    // =========================================================================
    // 6. HTTP 404 → MarkovResult.Error
    // =========================================================================

    @Test
    fun `HTTP 404 returns MarkovResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Not found"}"""),
        )

        val result = repository.fetchTickers()

        assertTrue(
            "HTTP 404 must map to MarkovResult.Error, got $result",
            result is MarkovResult.Error,
        )
    }

    // =========================================================================
    // 7. HTTP 500 → MarkovResult.Error
    // =========================================================================

    @Test
    fun `HTTP 500 returns MarkovResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.fetchTickers()

        assertTrue(
            "HTTP 500 must map to MarkovResult.Error, got $result",
            result is MarkovResult.Error,
        )
    }

    // =========================================================================
    // 8. Null/empty body → MarkovResult.Error
    // =========================================================================

    @Test
    fun `empty body returns MarkovResult Error`() = runTest(testDispatcher) {
        // A 200 with empty body will fail Kotlin serialization deserialization.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(""),
        )

        val result = repository.fetchTickers()

        assertTrue(
            "A null/empty response body must map to MarkovResult.Error, got $result",
            result is MarkovResult.Error,
        )
    }

    // =========================================================================
    // 9. Network exception → MarkovResult.Error (never throws)
    // =========================================================================

    @Test
    fun `network exception returns MarkovResult Error and never throws`() =
        runTest(testDispatcher) {
            // Shut down the server so any HTTP call throws an IOException.
            mockWebServer.shutdown()

            val result = repository.fetchTickers()

            assertTrue(
                "A network IOException must map to MarkovResult.Error — repository must never throw",
                result is MarkovResult.Error,
            )
        }

    // =========================================================================
    // 10. CancellationException is rethrown and not swallowed as Error
    // =========================================================================

    @Test
    fun `CancellationException from the API is rethrown not swallowed as Error`() =
        runTest(testDispatcher) {
            // Real behavioural test: an API that throws CancellationException must propagate out
            // of the repository (the `catch (CancellationException) { throw e }` arm), NOT be
            // converted to MarkovResult.Error by the generic catch.
            val cancellingApi = object : MarkovApi {
                override suspend fun getTickers(): retrofit2.Response<MarkovTickersDto> {
                    throw CancellationException("cancelled mid-flight")
                }
            }
            val cancellingRepo = MarkovRepositoryImpl(
                markovApi = cancellingApi,
                ioDispatcher = testDispatcher,
            )

            var rethrew = false
            try {
                cancellingRepo.fetchTickers()
            } catch (e: CancellationException) {
                rethrew = true
            }
            assertTrue(
                "fetchTickers() must rethrow CancellationException, not return MarkovResult.Error",
                rethrew,
            )
        }

    @Test
    fun `non-cancellation error path returns Error and does not throw`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Server Error"),
            )
            val result = repository.fetchTickers()
            assertTrue(
                "A non-cancellation exception path must not throw — must return MarkovResult.Error",
                result is MarkovResult.Error,
            )
        }

    // =========================================================================
    // Additional: Sideways regime maps correctly
    // =========================================================================

    @Test
    fun `Sideways regime string maps to Regime SIDEWAYS`() = runTest(testDispatcher) {
        val sidewaysTicker = tickerJson(ticker = "ETH-USD", regime = "Sideways")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(markovResponseJson(listOf(sidewaysTicker))),
        )

        val result = repository.fetchTickers()

        assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
        assertEquals(
            "\"Sideways\" must map to Regime.SIDEWAYS",
            Regime.SIDEWAYS,
            (result as MarkovResult.Success).data.tickers[0].regime,
        )
    }

    // =========================================================================
    // Additional: conviction null in ticker → null in domain model (no crash)
    // =========================================================================

    @Test
    fun `null conviction in ticker maps to null conviction in domain model`() =
        runTest(testDispatcher) {
            val noConvictionTicker = tickerJson(ticker = "BTC-USD", conviction = null)

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(markovResponseJson(listOf(noConvictionTicker))),
            )

            val result = repository.fetchTickers()

            assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
            assertNull(
                "conviction must be null when null in the JSON",
                (result as MarkovResult.Success).data.tickers[0].conviction,
            )
        }

    // =========================================================================
    // Additional: malformed JSON → MarkovResult.Error, never throws
    // =========================================================================

    @Test
    fun `malformed JSON returns MarkovResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{ this is not valid JSON !!!"),
            )

            val result = repository.fetchTickers()

            assertTrue(
                "Malformed JSON must map to MarkovResult.Error and never throw, got $result",
                result is MarkovResult.Error,
            )
        }

    // =========================================================================
    // Additional: non-empty tickers → MarkovData.isEmpty false
    // =========================================================================

    @Test
    fun `non-empty tickers list yields MarkovData isEmpty false`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(markovResponseJson(listOf(tickerJson()))),
        )

        val result = repository.fetchTickers()

        assertTrue("Must be MarkovResult.Success, got $result", result is MarkovResult.Success)
        assertFalse(
            "MarkovData.isEmpty must be false when tickers list is non-empty",
            (result as MarkovResult.Success).data.isEmpty,
        )
    }
}
