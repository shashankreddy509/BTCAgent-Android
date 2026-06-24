package com.gshashank.btcagent.data.network

import com.gshashank.btcagent.BuildConfig
import com.gshashank.btcagent.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton WebSocket client streaming live BTC price ticks from [BuildConfig.WS_URL].
 *
 * The server pushes `{"price": <float>}` frames (~20/sec), no auth, no subscribe handshake
 * (verified backend contract, MOBILE-5). The endpoint is intentionally open.
 *
 * [priceFlow] is a COLD [callbackFlow]: collection opens the WebSocket, cancellation closes it.
 * The sole collector today is [com.gshashank.btcagent.ui.home.DashboardViewModel]. If multiple
 * concurrent collectors are ever needed, share at the repository layer (shareIn on a long-lived
 * scope) — NOT here — so this client stays driveable by runTest in unit tests.
 *
 * Reconnect (exponential backoff, cap 30s) and the staleness watchdog run on DAEMON THREADS, not
 * coroutines: they are deliberately OFF the coroutine test scheduler so a `runTest` body completes
 * without waiting on an infinite watchdog loop, while still being observable in wall-clock time in
 * the reconnect test. (An earlier coroutine-on-test-dispatcher rewrite deadlocked runTest.)
 *
 * [onClosing] echoes the server's ACTUAL close code/reason so [onClosed] fires with the real code
 * — a non-1000 server close (1001/1011) must route to reconnect, not be swallowed.
 */
class PriceWebSocketClient(
    private val okHttpClient: OkHttpClient,
    @Suppress("unused") @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val wsUrl: String = BuildConfig.WS_URL,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun priceFlow(): Flow<Float> = callbackFlow {
        val backoffMs = AtomicLong(INITIAL_BACKOFF_MS)
        val currentWebSocket = AtomicReference<WebSocket?>(null)
        val lastMessageMs = AtomicLong(System.currentTimeMillis())
        val active = AtomicBoolean(true)
        // Guards onClosed + onFailure both firing for one socket → two reconnect threads.
        val reconnecting = AtomicBoolean(false)

        fun scheduleReconnect(connectFn: () -> Unit) {
            if (!active.get()) return
            if (!reconnecting.compareAndSet(false, true)) return
            val delayMs = backoffMs.get()
            backoffMs.set(minOf(delayMs * 2, MAX_BACKOFF_MS))
            Thread({
                try {
                    Thread.sleep(delayMs)
                    // Reset the guard BEFORE reconnecting: if this attempt also fails, onFailure
                    // must be able to schedule another. (Resetting only in onOpen would
                    // permanently block reconnects once a reconnect attempt itself fails.)
                    reconnecting.set(false)
                    if (active.get()) connectFn()
                } catch (_: InterruptedException) {
                    // Interrupted — allow a future reconnect.
                    reconnecting.set(false)
                }
            }, "PriceWsReconnect").also { it.isDaemon = true }.start()
        }

        fun connect() {
            if (!active.get()) return
            val request = Request.Builder().url(wsUrl).build()
            currentWebSocket.set(okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoffMs.set(INITIAL_BACKOFF_MS)
                    reconnecting.set(false)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastMessageMs.set(System.currentTimeMillis())
                    val price = parsePriceOrNull(text) ?: return
                    trySend(price)
                }

                // Echo the server's actual close code/reason (not a hardcoded 1000) so onClosed
                // receives the real code and a non-normal close routes to reconnect.
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (code != NORMAL_CLOSURE) scheduleReconnect(::connect)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scheduleReconnect(::connect)
                }
            }))
        }

        // Staleness watchdog — daemon thread, never blocks the coroutine test scheduler.
        Thread({
            while (active.get()) {
                try {
                    Thread.sleep(STALENESS_TIMEOUT_MS)
                    if (active.get() &&
                        System.currentTimeMillis() - lastMessageMs.get() > STALENESS_TIMEOUT_MS
                    ) {
                        currentWebSocket.get()?.cancel()
                        reconnecting.set(false)
                        scheduleReconnect(::connect)
                    }
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "PriceWsWatchdog").also { it.isDaemon = true }.start()

        connect()

        awaitClose {
            active.set(false)
            currentWebSocket.get()?.cancel()
        }
    }

    private fun parsePriceOrNull(text: String): Float? =
        try {
            val price = json.parseToJsonElement(text)
                .jsonObject["price"]?.jsonPrimitive?.doubleOrNull
            // Reject NaN/Infinity/non-positive — a bad tick must not poison the price card.
            if (price != null && price.isFinite() && price > 0.0) price.toFloat() else null
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val STALENESS_TIMEOUT_MS = 30_000L
        const val NORMAL_CLOSURE = 1000
    }
}
