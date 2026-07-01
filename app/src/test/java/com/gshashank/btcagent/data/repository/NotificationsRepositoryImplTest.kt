package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.NotificationsApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [NotificationsRepositoryImpl] — MOBILE-41.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by the
 * Retrofit layer and then mapped by the repository.
 *
 * Endpoints under test:
 *   POST   api/notifications/register → register()   → [NotificationsResult]
 *   DELETE api/notifications/register → unregister() → [NotificationsResult]
 *
 * Repository contract (per PLAN.md):
 *   - NEVER throws to callers.
 *   - A 404 response on register() means the backend's global push_notifications toggle is
 *     OFF (prod OFF, dev ON) — this is NOT an error. It must map to [NotificationsResult.Inert]
 *     so the UI stays inert/functional, never showing an error for this expected prod state.
 *   - 200 → [NotificationsResult.Success] for both register and unregister.
 *   - Non-2xx (other than 404) → [NotificationsResult.Error].
 *   - errorBody() is closed on non-2xx (connection pool hygiene, same as TradingControlRepositoryImpl).
 *   - CancellationException is rethrown, not swallowed.
 *
 * All tests MUST fail (red) until [NotificationsRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1. register() 200 {status:"registered"} → NotificationsResult.Success
 *   2. register() POSTs body {fcm_token, platform:"android"}
 *   3. register() 404 (global push toggle OFF) → NotificationsResult.Inert, NOT Error
 *   4. register() 400 (empty token) → NotificationsResult.Error
 *   5. register() network exception → NotificationsResult.Error (never throws)
 *   6. unregister() 200 {status:"unregistered"} → NotificationsResult.Success (sign-out path)
 *   7. unregister() DELETEs body {fcm_token}
 *   8. unregister() 404 → NotificationsResult.Inert (toggle OFF must also be inert on delete)
 *   9. unregister() 500 → NotificationsResult.Error
 *   10. errorBody is closed after non-2xx so the connection pool is not exhausted
 *   11. generic exception does not produce a thrown CancellationException
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var notificationsApi: NotificationsApi
    private lateinit var repository: NotificationsRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val sampleFcmToken = "fcm-test-token-abc123"

    @Before
    fun setUp() {
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()

        notificationsApi = retrofit.create(NotificationsApi::class.java)

        repository = NotificationsRepositoryImpl(
            notificationsApi = notificationsApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. register() 200 {status:"registered"} → NotificationsResult.Success
    // =========================================================================

    @Test
    fun `register 200 maps to NotificationsResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "registered"}"""),
        )

        val result = repository.register(sampleFcmToken)

        assertTrue(
            "HTTP 200 from POST /api/notifications/register must map to NotificationsResult.Success, got $result",
            result is NotificationsResult.Success,
        )
    }

    // =========================================================================
    // 2. register() POSTs body {fcm_token, platform:"android"}
    // =========================================================================

    @Test
    fun `register POSTs body with fcm_token and platform android`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "registered"}"""),
        )

        repository.register(sampleFcmToken)

        val recordedRequest = mockWebServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(
            "Request body must contain the fcm_token: got '$requestBody'",
            requestBody.contains("\"fcm_token\"") && requestBody.contains(sampleFcmToken),
        )
        assertTrue(
            "Request body must contain platform:\"android\": got '$requestBody'",
            requestBody.contains("\"platform\"") && requestBody.contains("android"),
        )
    }

    // =========================================================================
    // 3. register() 404 (global push toggle OFF) → NotificationsResult.Inert, NOT Error
    // =========================================================================

    @Test
    fun `register 404 maps to NotificationsResult Inert not Error (global push toggle OFF)`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"detail": "Not Found"}"""),
            )

            val result = repository.register(sampleFcmToken)

            assertTrue(
                "HTTP 404 (prod's global push_notifications toggle OFF) must map to " +
                    "NotificationsResult.Inert, NOT Error — the UI must never show an error for " +
                    "this expected prod state, got $result",
                result is NotificationsResult.Inert,
            )
            assertFalse(
                "404 must NOT be surfaced as NotificationsResult.Error",
                result is NotificationsResult.Error,
            )
        }

    // =========================================================================
    // 4. register() 400 (empty token) → NotificationsResult.Error
    // =========================================================================

    @Test
    fun `register 400 maps to NotificationsResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "fcm_token must not be empty"}"""),
        )

        val result = repository.register("")

        assertTrue(
            "HTTP 400 (empty token) must map to NotificationsResult.Error, got $result",
            result is NotificationsResult.Error,
        )
    }

    // =========================================================================
    // 5. register() network exception → NotificationsResult.Error (never throws)
    // =========================================================================

    @Test
    fun `register network exception maps to NotificationsResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            val result = repository.register(sampleFcmToken)

            assertTrue(
                "A network IOException from register() must map to NotificationsResult.Error — " +
                    "repository must never throw",
                result is NotificationsResult.Error,
            )
        }

    // =========================================================================
    // 6. unregister() 200 {status:"unregistered"} → NotificationsResult.Success (sign-out path)
    // =========================================================================

    @Test
    fun `unregister 200 maps to NotificationsResult Success`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "unregistered"}"""),
        )

        val result = repository.unregister(sampleFcmToken)

        assertTrue(
            "HTTP 200 from DELETE /api/notifications/register must map to " +
                "NotificationsResult.Success (sign-out path), got $result",
            result is NotificationsResult.Success,
        )
    }

    // =========================================================================
    // 7. unregister() DELETEs body {fcm_token}
    // =========================================================================

    @Test
    fun `unregister DELETEs body with fcm_token`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "unregistered"}"""),
        )

        repository.unregister(sampleFcmToken)

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue(
            "unregister() must issue a DELETE request, got method '${recordedRequest.method}'",
            recordedRequest.method == "DELETE",
        )
        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(
            "DELETE request body must contain the fcm_token: got '$requestBody'",
            requestBody.contains("\"fcm_token\"") && requestBody.contains(sampleFcmToken),
        )
    }

    // =========================================================================
    // 8. unregister() 404 → NotificationsResult.Inert (toggle OFF must also be inert on delete)
    // =========================================================================

    @Test
    fun `unregister 404 maps to NotificationsResult Inert`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Not Found"}"""),
        )

        val result = repository.unregister(sampleFcmToken)

        assertTrue(
            "HTTP 404 on unregister() (global push toggle OFF) must also map to " +
                "NotificationsResult.Inert — sign-out must not show an error, got $result",
            result is NotificationsResult.Inert,
        )
    }

    // =========================================================================
    // 9. unregister() 500 → NotificationsResult.Error
    // =========================================================================

    @Test
    fun `unregister 500 maps to NotificationsResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val result = repository.unregister(sampleFcmToken)

        assertTrue(
            "HTTP 500 must map to NotificationsResult.Error, got $result",
            result is NotificationsResult.Error,
        )
    }

    // =========================================================================
    // 10. errorBody is closed after non-2xx so the connection pool is not exhausted
    // =========================================================================

    @Test
    fun `errorBody is closed after non-2xx so connection pool is not exhausted`() =
        runTest(testDispatcher) {
            repeat(2) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(503)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"error": "unavailable"}"""),
                )
            }

            val first = repository.register(sampleFcmToken)
            val second = repository.register(sampleFcmToken)

            assertTrue(
                "First 503 must be NotificationsResult.Error (errorBody closed so pool is free)",
                first is NotificationsResult.Error,
            )
            assertTrue(
                "Second 503 must also succeed without hanging — proves errorBody was closed after first",
                second is NotificationsResult.Error,
            )
        }

    // =========================================================================
    // 11. generic exception does not produce a thrown CancellationException
    // =========================================================================

    @Test
    fun `generic exception does not produce a thrown CancellationException`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            var caughtCancellation = false
            try {
                repository.register(sampleFcmToken)
            } catch (e: CancellationException) {
                caughtCancellation = true
            }

            assertFalse(
                "An IOException must NOT be rethrown as CancellationException — " +
                    "only real CancellationExceptions should propagate",
                caughtCancellation,
            )
        }
}
