package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.AccessApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * JVM unit tests for [AccessRepositoryImpl].
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP status codes are received by
 * [AccessApi] and then mapped to [AccessResult] variants by the repository.
 * [UnconfinedTestDispatcher] is substituted for the `@IoDispatcher` to avoid Dispatchers.IO.
 *
 * All tests are expected to FAIL until [AccessRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1. HTTP 200 → AccessResult.Allowed
 *   2. HTTP 403 → AccessResult.Pending
 *   3. HTTP 401 → AccessResult.Unauthorized
 *   4. HTTP 500 → AccessResult.Error
 *   5. IOException (server shut down before call) → AccessResult.Error with non-null cause
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var accessApi: AccessApi
    private lateinit var repository: AccessRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer.start()

        // Build a real Retrofit + OkHttp stack pointed at MockWebServer.
        // No auth interceptors needed — we are testing only the repository mapping.
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .build()

        accessApi = retrofit.create(AccessApi::class.java)
        repository = AccessRepositoryImpl(
            accessApi = accessApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // -------------------------------------------------------------------------
    // 1. HTTP 200 → Allowed
    // -------------------------------------------------------------------------

    @Test
    fun `200 response maps to AccessResult Allowed`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = repository.checkAccess()

        assertEquals(
            "HTTP 200 must map to AccessResult.Allowed",
            AccessResult.Allowed,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // 2. HTTP 403 → Pending
    // -------------------------------------------------------------------------

    @Test
    fun `403 response maps to AccessResult Pending`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        val result = repository.checkAccess()

        assertEquals(
            "HTTP 403 must map to AccessResult.Pending",
            AccessResult.Pending,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // 3. HTTP 401 → Unauthorized
    // -------------------------------------------------------------------------

    @Test
    fun `401 response maps to AccessResult Unauthorized`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repository.checkAccess()

        assertEquals(
            "HTTP 401 must map to AccessResult.Unauthorized",
            AccessResult.Unauthorized,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // 4. HTTP 500 → Error
    // -------------------------------------------------------------------------

    @Test
    fun `500 response maps to AccessResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.checkAccess()

        assertTrue(
            "HTTP 500 must map to AccessResult.Error, got $result",
            result is AccessResult.Error,
        )
    }

    // -------------------------------------------------------------------------
    // 5. IOException (server shut down) → Error with non-null cause
    // -------------------------------------------------------------------------

    @Test
    fun `IOException maps to AccessResult Error with non-null cause`() = runTest(testDispatcher) {
        // Shut down the server before the call so the socket connection fails immediately.
        mockWebServer.shutdown()

        val result = repository.checkAccess()

        assertTrue(
            "IOException must map to AccessResult.Error, got $result",
            result is AccessResult.Error,
        )
        assertNotNull(
            "AccessResult.Error.cause must be non-null when an IOException occurs",
            (result as AccessResult.Error).cause,
        )
    }
}
