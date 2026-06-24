package com.gshashank.btcagent.data.network

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM unit tests for [PriceWebSocketClient] — MOBILE-5.
 *
 * Uses [MockWebServer] WebSocket support: enqueuing a [MockResponse] with
 * [MockResponse.withWebSocketUpgrade] hands the upgrade to a real [okhttp3.WebSocket] server-side
 * listener, from which we can send messages to the client under test.
 *
 * All tests MUST fail (red) until [PriceWebSocketClient] is implemented.
 *
 * Test coverage:
 *   1. Valid {"price": 67432.5} message is emitted as 67432.5f from priceFlow().
 *   2. Unknown/extra JSON fields do not crash — message is still emitted.
 *   3. {"price": null} → no emission, no crash.
 *   4. Invalid (non-JSON) text → no emission, no crash.
 *   5. onFailure callback triggers a reconnect — a second WebSocket connect attempt is made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PriceWebSocketClientTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    /** OkHttpClient used by the [PriceWebSocketClient] under test. No timeouts to keep tests fast. */
    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(2, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    /**
     * Creates a [PriceWebSocketClient] pointed at the [MockWebServer] URL.
     * The [ioDispatcher] is injected so the test controls virtual time.
     */
    private fun createClient(): PriceWebSocketClient =
        PriceWebSocketClient(
            okHttpClient = okHttpClient,
            wsUrl = mockWebServer.url("/ws/price").toString(),
            ioDispatcher = testDispatcher,
        )

    // =========================================================================
    // 1. Valid {"price": 67432.5} message is emitted as 67432.5f
    // =========================================================================

    @Test
    fun `valid price message is emitted as Float from priceFlow`() = runTest(testDispatcher) {
        // Server-side listener: when the client connects, immediately send a price message.
        val serverListener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send("""{"price": 67432.5}""")
            }
        }

        mockWebServer.enqueue(
            MockResponse().withWebSocketUpgrade(serverListener),
        )

        val client = createClient()

        client.priceFlow().test {
            advanceUntilIdle()

            val price = awaitItem()
            assertEquals(
                "priceFlow must emit the parsed float value from the JSON message",
                67432.5f,
                price,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 2. Unknown/extra JSON fields do not crash — message is still emitted
    // =========================================================================

    @Test
    fun `unknown JSON fields in price message do not crash and price is still emitted`() =
        runTest(testDispatcher) {
            val serverListener = object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    // Extra unknown fields alongside "price"
                    webSocket.send("""{"price": 50000.0, "timestamp": 1234567890, "source": "binance"}""")
                }
            }

            mockWebServer.enqueue(
                MockResponse().withWebSocketUpgrade(serverListener),
            )

            val client = createClient()

            client.priceFlow().test {
                advanceUntilIdle()

                val price = awaitItem()
                assertEquals(
                    "Price must be emitted correctly even when the message contains unknown extra fields",
                    50000.0f,
                    price,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 3. {"price": null} → no emission, no crash
    // =========================================================================

    @Test
    fun `price null message produces no emission and no crash`() = runTest(testDispatcher) {
        // After the null-price message, send a sentinel valid price so we can distinguish
        // "null was silently ignored" from "flow closed with error".
        val serverListener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send("""{"price": null}""")
                // Give the client a valid price next so Turbine has something to await.
                webSocket.send("""{"price": 42000.0}""")
            }
        }

        mockWebServer.enqueue(
            MockResponse().withWebSocketUpgrade(serverListener),
        )

        val client = createClient()

        client.priceFlow().test {
            advanceUntilIdle()

            // The first emission must be 42000.0 — the null message must have been skipped.
            val price = awaitItem()
            assertEquals(
                "The null-price message must be skipped; first emission must be the subsequent valid price",
                42000.0f,
                price,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. Invalid (non-JSON) text → no emission, no crash
    // =========================================================================

    @Test
    fun `invalid JSON message produces no emission and no crash`() = runTest(testDispatcher) {
        val serverListener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send("this is not json at all")
                // Follow with a valid price so Turbine can verify the bad message was skipped.
                webSocket.send("""{"price": 55000.0}""")
            }
        }

        mockWebServer.enqueue(
            MockResponse().withWebSocketUpgrade(serverListener),
        )

        val client = createClient()

        client.priceFlow().test {
            advanceUntilIdle()

            // Must receive the valid price — the invalid JSON message must have been silently skipped.
            val price = awaitItem()
            assertEquals(
                "Invalid JSON must be skipped; first emission must be the subsequent valid price",
                55000.0f,
                price,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. onFailure triggers a reconnect — a second WebSocket connect attempt is made
    // =========================================================================

    @Test
    fun `onFailure triggers a reconnect so a second connect attempt is observed`() =
        runTest(testDispatcher) {
            // Count how many times the server observes a WebSocket upgrade request.
            val connectCount = AtomicInteger(0)
            val secondConnectLatch = CountDownLatch(2)

            val failingListener = object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    connectCount.incrementAndGet()
                    secondConnectLatch.countDown()
                    // Immediately close with a non-normal code to trigger onFailure/onClosed.
                    webSocket.close(1011, "server error")
                }
            }

            // Enqueue two responses: one for the initial connect, one for the reconnect.
            mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(failingListener))
            mockWebServer.enqueue(MockResponse().withWebSocketUpgrade(failingListener))

            val client = createClient()

            // Collect the flow (it will reconnect in the background).
            val collectJob = launch {
                client.priceFlow().collect { /* consume prices if any */ }
            }

            advanceUntilIdle()

            // Allow real time for the background OkHttp threads (WS runs on real threads).
            secondConnectLatch.await(5, TimeUnit.SECONDS)

            val observed = connectCount.get()
            assertTrue(
                "A second WebSocket connection attempt must be made after onFailure/onClosed " +
                    "(reconnect contract). Observed connect count: $observed",
                observed >= 2,
            )

            collectJob.cancel()
        }
}
