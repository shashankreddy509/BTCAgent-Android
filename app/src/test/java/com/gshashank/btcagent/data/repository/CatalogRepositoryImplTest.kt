package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.CatalogApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
 * JVM unit tests for [CatalogRepositoryImpl] — MOBILE-26.
 *
 * Uses [MockWebServer] as the in-process HTTP server (same pattern as [AccessRepositoryImplTest]).
 * Uses [StandardTestDispatcher] so the `init {}` polling loop in [CatalogRepositoryImpl] does NOT
 * fire eagerly on construction. Tests call [CatalogRepositoryImpl.refresh] manually and drive
 * virtual time with [advanceUntilIdle] — this sidesteps the UnconfinedTestDispatcher caveat
 * described in PLAN.md §Risks.
 *
 * Construction strategy: each test constructs a fresh [CatalogRepositoryImpl] with the test
 * dispatcher. Because [StandardTestDispatcher] does not run coroutines until explicitly advanced,
 * the `init {}` loop does not execute spontaneously — tests call `refresh()` directly inside
 * `runTest` instead of relying on the background loop.
 *
 * All tests are expected to FAIL until [CatalogRepositoryImpl] is implemented (MOBILE-26).
 *
 * Test coverage:
 *   1. refresh() with `{"a":true,"b":false}` → catalogOn("a")==true, catalogOn("b")==false
 *   2. catalogOn("missing") == false after refresh of map not containing that key
 *   3. Fail-open: second refresh errors → last-known map preserved, no exception propagated
 *   4. Initial state (no refresh called) → all catalogOn == false (emptyMap default)
 *   5. HTTP 500 response on second refresh → last-known map preserved
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogRepositoryImplTest {

    private val mockWebServer = MockWebServer()

    // StandardTestDispatcher: coroutines are NOT run eagerly — tests call refresh() manually.
    // This prevents the init{} polling loop from consuming server responses before the test body
    // has a chance to enqueue them (PLAN.md §Risks — "Polling loop fires on init{} caveat").
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var catalogApi: CatalogApi
    private lateinit var repository: CatalogRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer.start()

        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        catalogApi = retrofit.create(CatalogApi::class.java)

        // Construct the repo. With StandardTestDispatcher the init{} launch is scheduled but
        // NOT yet executed — the test body controls when coroutines run via advanceUntilIdle().
        repository = CatalogRepositoryImpl(
            catalogApi = catalogApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // -------------------------------------------------------------------------
    // 1. refresh() parses true and false flags correctly
    // -------------------------------------------------------------------------

    @Test
    fun `getCatalogs parses true and false flags correctly`() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"a":true,"b":false}"""),
        )

        repository.refresh()

        assertTrue(
            "catalogOn(\"a\") must be true when the server returned \"a\":true",
            repository.catalogOn("a"),
        )
        assertFalse(
            "catalogOn(\"b\") must be false when the server returned \"b\":false",
            repository.catalogOn("b"),
        )
    }

    // -------------------------------------------------------------------------
    // 1b. catalogOn(flag, default) — explicit server false beats a default=true;
    //     a missing key returns the caller's default (the Option-A mechanism).
    // -------------------------------------------------------------------------

    @Test
    fun `catalogOn with default honours explicit false and falls back only when absent`() =
        testScope.runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"b":false}"""),
            )

            repository.refresh()

            assertFalse(
                "An explicit server-side false must win over a caller default=true",
                repository.catalogOn("b", default = true),
            )
            assertTrue(
                "A genuinely absent key must return the caller's default=true (Option A)",
                repository.catalogOn("absent", default = true),
            )
            assertFalse(
                "A genuinely absent key must return the caller's default=false",
                repository.catalogOn("absent", default = false),
            )
        }

    // -------------------------------------------------------------------------
    // 2. catalogOn returns false for a key absent from the response
    // -------------------------------------------------------------------------

    @Test
    fun `catalogOn returns false for a missing flag`() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"x":true}"""),
        )

        repository.refresh()

        assertFalse(
            "catalogOn(\"missing\") must be false when the key is absent from the server map",
            repository.catalogOn("missing"),
        )
    }

    // -------------------------------------------------------------------------
    // 3. Fail-open: second refresh that errors keeps last-known map and never throws
    // -------------------------------------------------------------------------

    @Test
    fun `refresh failure keeps last-known map and never throws`() = testScope.runTest {
        // First refresh: successful — populate the map with {"a":true}.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"a":true}"""),
        )
        repository.refresh()

        assertTrue(
            "Precondition: catalogOn(\"a\") must be true after a successful refresh",
            repository.catalogOn("a"),
        )

        // Second refresh: server is shut down → IOException. refresh() must not propagate it.
        mockWebServer.shutdown()

        // Must not throw — the fail-open contract swallows all non-cancellation exceptions.
        repository.refresh()

        // The last-known map must be intact.
        assertTrue(
            "catalogOn(\"a\") must still be true after a failed second refresh (fail-open)",
            repository.catalogOn("a"),
        )
    }

    // -------------------------------------------------------------------------
    // 4. Initial state: before any refresh all flags are false (emptyMap default)
    // -------------------------------------------------------------------------

    @Test
    fun `initial state before any refresh returns all false`() = testScope.runTest {
        // No refresh() called. The init{} loop is scheduled but not yet executed because
        // StandardTestDispatcher does not run eagerly. We verify the cold-start default.
        // advanceUntilIdle is deliberately NOT called — we test the snapshot at construction time.
        assertFalse(
            "catalogOn(\"anything\") must be false before any refresh has succeeded",
            repository.catalogOn("anything"),
        )
    }

    // -------------------------------------------------------------------------
    // 5. HTTP 500 on second refresh keeps last-known map
    // -------------------------------------------------------------------------

    @Test
    fun `refresh with HTTP error response keeps last-known map`() = testScope.runTest {
        // First refresh: successful.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"flag":true}"""),
        )
        repository.refresh()

        assertTrue(
            "Precondition: catalogOn(\"flag\") must be true after a successful refresh",
            repository.catalogOn("flag"),
        )

        // Second refresh: server returns HTTP 500. refresh() must swallow the HttpException.
        mockWebServer.enqueue(
            MockResponse().setResponseCode(500),
        )
        repository.refresh()

        assertTrue(
            "catalogOn(\"flag\") must still be true after a 500 response on the second refresh",
            repository.catalogOn("flag"),
        )
    }
}
