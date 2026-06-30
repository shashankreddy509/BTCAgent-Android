package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.DashboardApi
import com.gshashank.btcagent.data.network.PriceWebSocketClient
import com.gshashank.btcagent.data.model.BotMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * JVM unit tests for [DashboardRepositoryImpl] — MOBILE-5, MOBILE-39.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are received
 * by [DashboardApi] and then mapped by the repository.
 *
 * [PriceWebSocketClient] is mocked with mockito-kotlin — its [priceFlow] returns an empty
 * flow in all tests here. WS-specific behaviour is covered separately in [PriceWebSocketClientTest].
 *
 * All tests MUST fail (red) until [DashboardRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1.  200 response maps running, mode, position count, unrealised PnL aggregate correctly.
 *   2.  todayPnlPts sums only today's pnl_closed entries.
 *   3.  Yesterday's history entries are excluded from todayPnlPts.
 *   4.  null pnl in position is excluded from unrealised PnL aggregate.
 *   5.  401 response → [DashboardResult.Error] (never throws).
 *   6.  500 response → [DashboardResult.Error] (never throws).
 *   7.  Network exception → [DashboardResult.Error] (never throws).
 *   8.  maps_brokerAccountName_from_dto — broker_account_name maps to DashboardData.brokerName.
 *   9.  maps_scanIntervalMin_from_dto — settings.scan_interval_min maps to DashboardData.scanIntervalMin.
 *   10. computes_longCount_and_shortCount — direction field drives longCount/shortCount.
 *   11. maps_positions_list — positions list is mapped; DashboardData.positions size matches input.
 *   12. null_brokerAccountName_defaults — absent broker_account_name → "Coinbase", never "null".
 *   13. absent_scanIntervalMin_defaults_to_zero — absent scan_interval_min → 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var dashboardApi: DashboardApi
    private val mockPriceWsClient: PriceWebSocketClient = mock()
    private lateinit var repository: DashboardRepositoryImpl

    /** Today's date formatted as ISO-8601 (yyyy-MM-dd) for building test payloads. */
    private val todayIso: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** Yesterday's date for building excluded history entries. */
    private val yesterdayIso: String =
        LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Before
    fun setUp() {
        mockWebServer.start()

        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        dashboardApi = retrofit.create(DashboardApi::class.java)

        // priceFlow() returns empty flow in all repo tests — WS is not the concern here.
        whenever(mockPriceWsClient.priceFlow()).thenReturn(flowOf())

        repository = DashboardRepositoryImpl(
            dashboardApi = dashboardApi,
            priceWebSocketClient = mockPriceWsClient,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. 200 response maps running, mode, position count, unrealised PnL correctly
    // =========================================================================

    @Test
    fun `200 response maps running mode position count and unrealised PnL correctly`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "running": true,
                          "settings": { "mode": "live" },
                          "positions": [
                            { "pnl": 100.0, "direction": "long", "entry_price": 65000.0,
                              "status": "open" },
                            { "pnl": 50.5, "direction": "short", "entry_price": 66000.0,
                              "status": "open" }
                          ],
                          "history": []
                        }
                        """.trimIndent(),
                    ),
            )

            val result = repository.fetchState()

            assertTrue(
                "200 response must map to DashboardResult.Success, got $result",
                result is DashboardResult.Success,
            )
            val data = (result as DashboardResult.Success).data

            assertTrue("botRunning must be true when running=true in response", data.botRunning)
            assertEquals(
                "botMode must be Live when settings.mode is \"live\"",
                BotMode.Live,
                data.botMode,
            )
            assertEquals(
                "openPositionCount must equal the number of positions in the response",
                2,
                data.openPositionCount,
            )
            assertEquals(
                "openUnrealisedPnl must be the sum of all non-null position pnl values",
                150.5,
                data.openUnrealisedPnl,
                0.001,
            )
        }

    // =========================================================================
    // 2. todayPnlPts sums only today's pnl_closed entries
    // =========================================================================

    @Test
    fun `todayPnlPts sums only todays pnl_closed entries`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": false,
                      "settings": { "mode": "paper" },
                      "positions": [],
                      "history": [
                        { "pnl_closed": 30.0, "closed_at": "${todayIso}T10:00:00Z" },
                        { "pnl_closed": 20.0, "closed_at": "${todayIso}T14:30:00Z" }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "todayPnlPts must be the sum of all pnl_closed entries closed today",
            50.0,
            data.todayPnlPts,
            0.001,
        )
    }

    // =========================================================================
    // 3. Yesterday's history entries are excluded from todayPnlPts
    // =========================================================================

    @Test
    fun `todayPnlPts excludes history entries from yesterday`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": false,
                      "settings": { "mode": "paper" },
                      "positions": [],
                      "history": [
                        { "pnl_closed": 100.0, "closed_at": "${yesterdayIso}T22:00:00Z" },
                        { "pnl_closed": 25.0,  "closed_at": "${todayIso}T09:00:00Z" }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "todayPnlPts must only include today's entries; yesterday's 100.0 must be excluded",
            25.0,
            data.todayPnlPts,
            0.001,
        )
    }

    // =========================================================================
    // 4. null pnl in position is excluded from unrealised PnL aggregate
    // =========================================================================

    @Test
    fun `null pnl in position is excluded from unrealised PnL aggregate`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "running": true,
                          "settings": { "mode": "paper" },
                          "positions": [
                            { "pnl": 75.0, "direction": "long", "entry_price": 60000.0,
                              "status": "open" },
                            { "pnl": null, "direction": "short", "entry_price": 61000.0,
                              "status": "open" }
                          ],
                          "history": []
                        }
                        """.trimIndent(),
                    ),
            )

            val result = repository.fetchState()

            assertTrue("Must be Success, got $result", result is DashboardResult.Success)
            val data = (result as DashboardResult.Success).data
            assertEquals(
                "openUnrealisedPnl must only sum non-null pnl values; null entries must be skipped",
                75.0,
                data.openUnrealisedPnl,
                0.001,
            )
            // Both positions (including the null-pnl one) must be counted
            assertEquals(
                "openPositionCount must count ALL positions, including those with null pnl",
                2,
                data.openPositionCount,
            )
        }

    // =========================================================================
    // 5. 401 response → DashboardResult.Error (never throws)
    // =========================================================================

    @Test
    fun `401 response maps to DashboardResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setResponseCode(401))

            val result = repository.fetchState()

            assertTrue(
                "HTTP 401 must map to DashboardResult.Error, got $result",
                result is DashboardResult.Error,
            )
        }

    // =========================================================================
    // 6. 500 response → DashboardResult.Error (never throws)
    // =========================================================================

    @Test
    fun `500 response maps to DashboardResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val result = repository.fetchState()

            assertTrue(
                "HTTP 500 must map to DashboardResult.Error, got $result",
                result is DashboardResult.Error,
            )
        }

    // =========================================================================
    // 7. Network exception → DashboardResult.Error (never throws)
    // =========================================================================

    @Test
    fun `network exception maps to DashboardResult Error and never throws`() =
        runTest(testDispatcher) {
            // Shut down the server so any request throws an IOException.
            mockWebServer.shutdown()

            val result = repository.fetchState()

            assertTrue(
                "A network IOException must map to DashboardResult.Error, got $result",
                result is DashboardResult.Error,
            )
        }

    // =========================================================================
    // 8. maps_brokerAccountName_from_dto
    // =========================================================================

    @Test
    fun `maps_brokerAccountName_from_dto`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": false,
                      "broker_account_name": "Coinbase Pro",
                      "settings": { "mode": "paper" },
                      "positions": [],
                      "history": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "brokerName must be mapped from broker_account_name in the response",
            "Coinbase Pro",
            data.brokerName,
        )
    }

    // =========================================================================
    // 9. maps_scanIntervalMin_from_dto
    // =========================================================================

    @Test
    fun `maps_scanIntervalMin_from_dto`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": false,
                      "settings": { "mode": "paper", "scan_interval_min": 5 },
                      "positions": [],
                      "history": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "scanIntervalMin must be mapped from settings.scan_interval_min in the response",
            5,
            data.scanIntervalMin,
        )
    }

    // =========================================================================
    // 10. computes_longCount_and_shortCount
    // =========================================================================

    @Test
    fun `computes_longCount_and_shortCount`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": true,
                      "settings": { "mode": "live" },
                      "positions": [
                        { "signal_id": "s1", "direction": "long",  "entry_price": 65000.0,
                          "qty": 0.1, "status": "open" },
                        { "signal_id": "s2", "direction": "long",  "entry_price": 64000.0,
                          "qty": 0.2, "status": "open" },
                        { "signal_id": "s3", "direction": "short", "entry_price": 66000.0,
                          "qty": 0.1, "status": "open" }
                      ],
                      "history": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "longCount must be 2 when 2 positions have direction=long",
            2,
            data.longCount,
        )
        assertEquals(
            "shortCount must be 1 when 1 position has direction=short",
            1,
            data.shortCount,
        )
    }

    // =========================================================================
    // 11. maps_positions_list
    // =========================================================================

    @Test
    fun `maps_positions_list`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": true,
                      "settings": { "mode": "paper" },
                      "current_price": 67000.0,
                      "positions": [
                        { "signal_id": "a1", "direction": "long",  "entry_price": 65000.0,
                          "qty": 0.1, "status": "open" },
                        { "signal_id": "a2", "direction": "short", "entry_price": 66000.0,
                          "qty": 0.1, "status": "open" }
                      ],
                      "history": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "positions list in DashboardData must contain the same number of entries as the " +
                "positions array in the API response",
            2,
            data.positions.size,
        )
    }

    // =========================================================================
    // 12. null_brokerAccountName_defaults
    // =========================================================================

    @Test
    fun `null_brokerAccountName_defaults`() = runTest(testDispatcher) {
        // broker_account_name is explicitly null in the response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": false,
                      "broker_account_name": null,
                      "settings": { "mode": "paper" },
                      "positions": [],
                      "history": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "brokerName must default to \"Coinbase\" when broker_account_name is null; " +
                "must never render the literal string \"null\"",
            "Coinbase",
            data.brokerName,
        )
    }

    // =========================================================================
    // 13. absent_scanIntervalMin_defaults_to_zero
    // =========================================================================

    @Test
    fun `absent_scanIntervalMin_defaults_to_zero`() = runTest(testDispatcher) {
        // settings object does not include scan_interval_min at all
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "running": false,
                      "settings": { "mode": "paper" },
                      "positions": [],
                      "history": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.fetchState()

        assertTrue("Must be Success, got $result", result is DashboardResult.Success)
        val data = (result as DashboardResult.Success).data
        assertEquals(
            "scanIntervalMin must default to 0 when scan_interval_min is absent from settings",
            0,
            data.scanIntervalMin,
        )
    }
}
