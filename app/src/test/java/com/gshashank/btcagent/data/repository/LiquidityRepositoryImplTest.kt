package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.LiquidityApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
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
 * JVM unit tests for [LiquidityRepositoryImpl] — MOBILE-15.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed
 * by the Retrofit layer and then mapped by the repository.
 *
 * Endpoint: GET /api/liquidity — auth: _require_allowed (403 for non-allowlisted)
 *
 * Repository behavior under test:
 *  - 200 with valid rows → Success; rows sorted by price DESC in result.
 *  - 403 → Forbidden.
 *  - {"rows":[],"status":"no_data"} → Success with isEmpty == true (NOT Error).
 *  - {"rows":[],"status":"ok"}      → Success with isEmpty == true.
 *  - 500 → Error.
 *  - Network exception (server closed) → Error (never throws).
 *  - CancellationException is rethrown, not swallowed.
 *  - Null/garbage body → Error.
 *
 * All tests MUST fail (red) until [LiquidityRepositoryImpl] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var liquidityApi: LiquidityApi
    private lateinit var repository: LiquidityRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private fun rowJson(
        timestamp: String = "2026-06-25 14:30:00 UTC",
        color: String = "RED",
        yPixel: String = "125",
        yRange: String = "125-125",
        leverage: String = "8.5M",
        price: String = "43200.5",
    ): String = """
        {
          "timestamp": "$timestamp",
          "color": "$color",
          "y_pixel": "$yPixel",
          "y_range": "$yRange",
          "leverage": "$leverage",
          "price": "$price"
        }
    """.trimIndent()

    private fun liquidityResponseJson(
        rows: List<String>,
        status: String = "ok",
    ): String {
        val rowsJson = rows.joinToString(",\n")
        return """
            {
              "rows": [$rowsJson],
              "status": "$status"
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

        liquidityApi = retrofit.create(LiquidityApi::class.java)

        repository = LiquidityRepositoryImpl(
            liquidityApi = liquidityApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. 200 with valid rows → Success; sorted by price DESC
    // =========================================================================

    @Test
    fun `200 with valid rows returns Success with rows sorted by price DESC`() =
        runTest(testDispatcher) {
            val lowPriceRow = rowJson(
                color = "GREEN",
                leverage = "5M",
                price = "40000.0",
                timestamp = "2026-06-25 14:30:00 UTC",
            )
            val highPriceRow = rowJson(
                color = "RED",
                leverage = "10M",
                price = "50000.0",
                timestamp = "2026-06-25 14:31:00 UTC",
            )
            val midPriceRow = rowJson(
                color = "BLUE",
                leverage = "3M",
                price = "45000.0",
                timestamp = "2026-06-25 14:32:00 UTC",
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(liquidityResponseJson(listOf(lowPriceRow, highPriceRow, midPriceRow))),
            )

            val result = repository.fetch()

            assertTrue(
                "HTTP 200 with valid rows must return LiquidityResult.Success, got $result",
                result is LiquidityResult.Success,
            )
            val data = (result as LiquidityResult.Success).data
            assertEquals("Must have 3 levels", 3, data.levels.size)
            assertTrue(
                "First level price (${data.levels[0].price}) must be highest (price DESC sort)",
                data.levels[0].price >= data.levels[1].price,
            )
            assertTrue(
                "Second level price (${data.levels[1].price}) must be >= third (${data.levels[2].price})",
                data.levels[1].price >= data.levels[2].price,
            )
            assertEquals("Highest price must be 50000.0", 50000.0, data.levels[0].price, 0.001)
            assertEquals("Lowest price must be 40000.0", 40000.0, data.levels[2].price, 0.001)
        }

    // =========================================================================
    // 2. 403 → Forbidden
    // =========================================================================

    @Test
    fun `HTTP 403 returns LiquidityResult Forbidden`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail":"Not allowed"}"""),
        )

        val result = repository.fetch()

        assertTrue(
            "HTTP 403 must return LiquidityResult.Forbidden, got $result",
            result is LiquidityResult.Forbidden,
        )
    }

    // =========================================================================
    // 3. {"rows":[],"status":"no_data"} → Success with isEmpty == true (NOT Error)
    // =========================================================================

    @Test
    fun `status no_data with empty rows returns Success with isEmpty true`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"rows":[],"status":"no_data"}"""),
            )

            val result = repository.fetch()

            assertTrue(
                "status=no_data with empty rows must return LiquidityResult.Success (NOT Error), got $result",
                result is LiquidityResult.Success,
            )
            val data = (result as LiquidityResult.Success).data
            assertTrue(
                "LiquidityMapData.isEmpty must be true when rows are empty",
                data.isEmpty,
            )
        }

    // =========================================================================
    // 4. {"rows":[],"status":"ok"} → Success with isEmpty == true
    // =========================================================================

    @Test
    fun `status ok with empty rows returns Success with isEmpty true`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"rows":[],"status":"ok"}"""),
        )

        val result = repository.fetch()

        assertTrue(
            "status=ok with empty rows must return LiquidityResult.Success, got $result",
            result is LiquidityResult.Success,
        )
        val data = (result as LiquidityResult.Success).data
        assertTrue(
            "LiquidityMapData.isEmpty must be true when rows are empty",
            data.isEmpty,
        )
    }

    // =========================================================================
    // 5. HTTP 500 → Error
    // =========================================================================

    @Test
    fun `HTTP 500 returns LiquidityResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.fetch()

        assertTrue(
            "HTTP 500 must return LiquidityResult.Error, got $result",
            result is LiquidityResult.Error,
        )
    }

    // =========================================================================
    // 6. Network exception → Error (never throws)
    // =========================================================================

    @Test
    fun `network exception returns LiquidityResult Error and never throws`() =
        runTest(testDispatcher) {
            // Shut down server so any HTTP call throws an IOException.
            mockWebServer.shutdown()

            val result = repository.fetch()

            assertTrue(
                "A network IOException must return LiquidityResult.Error — repository must never throw",
                result is LiquidityResult.Error,
            )
        }

    // =========================================================================
    // 7. Null / garbage body → Error
    // =========================================================================

    @Test
    fun `empty body returns LiquidityResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(""),
        )

        val result = repository.fetch()

        assertTrue(
            "An empty response body must return LiquidityResult.Error, got $result",
            result is LiquidityResult.Error,
        )
    }

    @Test
    fun `malformed JSON returns LiquidityResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{ this is not valid json !!!"),
            )

            val result = repository.fetch()

            assertTrue(
                "Malformed JSON must return LiquidityResult.Error and never throw, got $result",
                result is LiquidityResult.Error,
            )
        }

    // =========================================================================
    // 8. CancellationException is rethrown (not swallowed as Error)
    // =========================================================================

    @Test
    fun `CancellationException is rethrown and not swallowed as Error`() =
        runTest(testDispatcher) {
            val cancellingApi = object : LiquidityApi {
                override suspend fun getLiquidity(): retrofit2.Response<com.gshashank.btcagent.data.network.LiquidityDto> {
                    throw CancellationException("cancelled mid-flight")
                }
            }
            val cancellingRepo = LiquidityRepositoryImpl(
                liquidityApi = cancellingApi,
                ioDispatcher = testDispatcher,
            )

            var rethrew = false
            try {
                cancellingRepo.fetch()
            } catch (e: CancellationException) {
                rethrew = true
            }
            assertTrue(
                "fetch() must rethrow CancellationException, not return LiquidityResult.Error",
                rethrew,
            )
        }

    // =========================================================================
    // 9. 200 single valid row → correct field mapping (color→side, leverage→notional)
    // =========================================================================

    @Test
    fun `200 with single row maps color to side and leverage to notional correctly`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        liquidityResponseJson(
                            listOf(
                                rowJson(
                                    color = "RED",
                                    leverage = "8.5M",
                                    price = "43200.5",
                                    timestamp = "2026-06-25 14:30:00 UTC",
                                ),
                            ),
                        ),
                    ),
            )

            val result = repository.fetch()

            assertTrue("Must be Success, got $result", result is LiquidityResult.Success)
            val data = (result as LiquidityResult.Success).data
            assertEquals("Must have 1 level", 1, data.levels.size)

            val level = data.levels[0]
            assertEquals(
                "RED color must map to HeatTier.HOT",
                com.gshashank.btcagent.data.model.HeatTier.HOT,
                level.tier,
            )
            assertEquals(
                "leverage 8.5M must map to notional 8_500_000.0",
                8_500_000.0,
                level.notional,
                0.001,
            )
            assertEquals(
                "price must be 43200.5",
                43200.5,
                level.price,
                0.001,
            )
        }

    // =========================================================================
    // 10. Non-cancellation error path stays as Error without throwing
    // =========================================================================

    @Test
    fun `non-cancellation exception path returns Error and does not throw`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Server Error"),
            )
            val result = repository.fetch()
            assertTrue(
                "A non-cancellation failure must return LiquidityResult.Error, not throw",
                result is LiquidityResult.Error,
            )
        }

    // =========================================================================
    // 11. Error message contains HTTP status code
    // =========================================================================

    @Test
    fun `HTTP error response message contains the status code`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable"),
        )

        val result = repository.fetch()

        assertTrue("Must be LiquidityResult.Error, got $result", result is LiquidityResult.Error)
        val error = result as LiquidityResult.Error
        assertTrue(
            "Error message must contain '503', got: ${error.message}",
            error.message?.contains("503") == true,
        )
    }
}
