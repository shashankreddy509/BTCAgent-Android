package com.gshashank.btcagent.data.network

import com.gshashank.btcagent.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for [AuthInterceptor] and [TokenAuthenticator].
 *
 * Uses [MockWebServer] for an in-process HTTP server and a hand-written [FakeTokenProvider].
 * No real Firebase or network calls are made.
 *
 * All tests are expected to FAIL until [AuthInterceptor] and [TokenAuthenticator] are implemented.
 *
 * Test coverage:
 *   1. Bearer header is attached when token is present.
 *   2. No Authorization header when token is null.
 *   3. 401 triggers exactly one forced-refresh retry and succeeds (requestCount == 2).
 *   4. Second 401 after retry gives up — no infinite loop (requestCount == 2, final 401).
 *   5. Authorization header is redacted in HEADERS-level logs (token absent from log output).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthInterceptorTest {

    private val mockWebServer = MockWebServer()

    // Mocked AuthRepository used by TokenAuthenticator.
    private val mockAuthRepository: AuthRepository = mock()

    // Fake token provider — tests configure the token value per case.
    private lateinit var fakeTokenProvider: FakeTokenProvider

    // The OkHttpClient under test; rebuilt per test via buildClient().
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer.start()
        fakeTokenProvider = FakeTokenProvider()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a client wiring [AuthInterceptor] and [TokenAuthenticator]. */
    private fun buildClient(logLevel: HttpLoggingInterceptor.Level? = null): OkHttpClient {
        val interceptor = AuthInterceptor(fakeTokenProvider)
        val authenticator = TokenAuthenticator(mockAuthRepository)

        return OkHttpClient.Builder()
            .apply {
                if (logLevel != null) {
                    val logging = HttpLoggingInterceptor().apply { level = logLevel }
                    logging.redactHeader("Authorization")
                    addInterceptor(logging)
                }
            }
            .addInterceptor(interceptor)
            .authenticator(authenticator)
            .build()
    }

    /** Executes a GET against the MockWebServer and returns the OkHttp Response. */
    private fun executeRequest(client: OkHttpClient): okhttp3.Response {
        val request = Request.Builder()
            .url(mockWebServer.url("/api/access/check"))
            .build()
        return client.newCall(request).execute()
    }

    // -------------------------------------------------------------------------
    // 1. Bearer header is attached when token is present
    // -------------------------------------------------------------------------

    @Test
    fun `bearer header is attached when token is present`() {
        fakeTokenProvider.token = "test-token"
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client = buildClient()
        executeRequest(client).close()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(
            "Authorization header must be Bearer test-token",
            "Bearer test-token",
            recordedRequest.getHeader("Authorization"),
        )
    }

    // -------------------------------------------------------------------------
    // 2. No Authorization header when token is null
    // -------------------------------------------------------------------------

    @Test
    fun `no authorization header when token is null`() {
        fakeTokenProvider.token = null
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client = buildClient()
        executeRequest(client).close()

        val recordedRequest = mockWebServer.takeRequest()
        assertNull(
            "Authorization header must be absent when token is null",
            recordedRequest.getHeader("Authorization"),
        )
    }

    // -------------------------------------------------------------------------
    // 3. 401 triggers exactly one forced-refresh retry, then 200
    // -------------------------------------------------------------------------

    @Test
    fun `401 triggers exactly one forced-refresh retry and succeeds`() {
        // Arrange: first request gets 401, retry gets 200.
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // TokenAuthenticator calls authRepository.getIdToken() to get a refreshed token.
        runBlocking {
            whenever(mockAuthRepository.getIdToken()).thenReturn(Result.success("refreshed-token"))
        }

        fakeTokenProvider.token = "initial-token"
        client = buildClient()

        val response = executeRequest(client)

        // Assert: two requests were made (original + one retry), final code is 200.
        assertEquals(
            "Server must have received exactly 2 requests (1 original + 1 retry)",
            2,
            mockWebServer.requestCount,
        )
        assertEquals(
            "Final response code must be 200 after the retry succeeds",
            200,
            response.code,
        )
        response.close()
    }

    // -------------------------------------------------------------------------
    // 4. Second 401 after retry gives up — no infinite loop
    // -------------------------------------------------------------------------

    @Test
    fun `second 401 after retry gives up — no infinite loop`() {
        // Arrange: server always returns 401 — even after a retry it stays 401.
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        // A third response is queued defensively; if the client requests it the test fails.
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        runBlocking {
            whenever(mockAuthRepository.getIdToken()).thenReturn(Result.success("refreshed-token"))
        }

        fakeTokenProvider.token = "initial-token"
        client = buildClient()

        val response = executeRequest(client)

        // Assert: authenticator must give up after one retry — exactly 2 requests total.
        assertEquals(
            "Client must give up after 1 retry (2 requests total); got ${mockWebServer.requestCount}",
            2,
            mockWebServer.requestCount,
        )
        assertEquals(
            "Final response after giving up must be 401, not an exception",
            401,
            response.code,
        )
        response.close()
    }

    // -------------------------------------------------------------------------
    // 5. Authorization header is redacted in HEADERS-level logs
    // -------------------------------------------------------------------------

    @Test
    fun `authorization header is redacted in headers level logs`() {
        val tokenValue = "super-secret-token"
        fakeTokenProvider.token = tokenValue
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // Capture log output via a custom Logger.
        val logBuilder = StringBuilder()
        val capturingLogger = HttpLoggingInterceptor.Logger { message ->
            logBuilder.append(message).append('\n')
        }

        val interceptor = AuthInterceptor(fakeTokenProvider)
        val logging = HttpLoggingInterceptor(capturingLogger).apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
        }

        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)   // auth interceptor first so it adds the header
            .addInterceptor(logging)       // logging second so it sees the modified request
            .build()

        executeRequest(client).close()

        val capturedLog = logBuilder.toString()
        assertTrue(
            "Token value '$tokenValue' must NOT appear in the captured log output. " +
                "Log was:\n$capturedLog",
            !capturedLog.contains(tokenValue),
        )
        // The header name must still appear (redacted, not removed).
        assertTrue(
            "The 'Authorization' header name must still appear in the log (as redacted).",
            capturedLog.contains("Authorization", ignoreCase = true),
        )
    }
}

// =============================================================================
// Fake collaborator
// =============================================================================

/**
 * Simple fake [TokenProvider] whose returned token is configurable per test.
 *
 * The @get:JvmName annotation renames the property getter on the JVM to avoid a
 * platform-declaration clash with the explicitly declared [getToken] override: Kotlin
 * generates a JVM getter named `getToken` for a property named `token`, which would
 * collide with the `override fun getToken()` method at the bytecode level.
 */
private class FakeTokenProvider : TokenProvider {
    @get:JvmName("_token")
    var token: String? = null

    override fun getToken(): String? = token
}
