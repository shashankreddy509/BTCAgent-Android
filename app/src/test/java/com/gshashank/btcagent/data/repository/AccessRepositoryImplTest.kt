package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.AccessApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [AccessRepositoryImpl].
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP status codes are received by
 * [AccessApi] and then mapped to [AccessResult] variants by the repository.
 * [UnconfinedTestDispatcher] is substituted for the `@IoDispatcher` to avoid Dispatchers.IO.
 *
 * MOBILE-27: [AccessRepositoryImpl] now accepts a [CatalogRepository] parameter. Tests use
 * [FakeCatalogRepository] — a hand-rolled fake with a configurable [flagOn] value. The default
 * in [setUp] is `flagOn = true` (flag ON) so all six original body-based tests remain unchanged.
 * Three new tests cover the flag-OFF and flag-MISSING (Option-A) paths.
 *
 * OPTION-A CONTRACT (encoded in test 9):
 *   When the flag `gate_access_check_body` is MISSING from the catalog map, [AccessRepositoryImpl]
 *   MUST treat it as ON — using the SAFE/body-based path, NOT the old status-based path.
 *   A 200 with `allowed=false` must map to [AccessResult.Pending], not [AccessResult.Allowed].
 *   The [FakeCatalogRepository] for this case uses the MISSING sentinel (null map entry) to
 *   prove the inversion is wired at the call site, not just toggling a boolean.
 *
 * All tests are expected to FAIL until [AccessRepositoryImpl] is fully implemented (MOBILE-27).
 *
 * Test coverage:
 *   — Flag ON (body-based path):
 *     1. 200 allowed=true  → Allowed(admin=true)
 *     2. 200 allowed=false → Pending
 *     3. HTTP 401          → Unauthorized
 *     4. HTTP 500          → Error
 *     5. IOException       → Error with non-null cause
 *     6. 200 bad body      → Error
 *   — Flag OFF (status-based / rollback path):
 *     7. 200 (body: allowed=false) → Allowed(admin=false)  — ANY 200 is Allowed under OLD path
 *     8. 401               → Unauthorized
 *     9a. 500              → Error
 *   — Flag MISSING (Option-A safe default — body-based):
 *     9b. 200 allowed=false → Pending (NOT Allowed) — proves missing == ON, not OFF
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
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        accessApi = retrofit.create(AccessApi::class.java)

        // Default: flag ON so all six original body-based tests remain unaffected.
        repository = AccessRepositoryImpl(
            accessApi = accessApi,
            ioDispatcher = testDispatcher,
            catalogRepository = FakeCatalogRepository(flagOn = true),
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // FLAG ON — body-based path (original six tests, unchanged assertions)
    // =========================================================================

    // -------------------------------------------------------------------------
    // 1. 200 allowed=true → Allowed(admin)
    // -------------------------------------------------------------------------

    @Test
    fun `200 allowed true maps to AccessResult Allowed with admin`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"allowed":true,"admin":true}"""),
        )

        val result = repository.checkAccess()

        assertEquals(
            "allowed=true must map to AccessResult.Allowed(admin=true)",
            AccessResult.Allowed(admin = true),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // 2. 200 allowed=false → Pending (the non-approved user — the original bug)
    // -------------------------------------------------------------------------

    @Test
    fun `200 allowed false maps to AccessResult Pending`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"allowed":false,"admin":false}"""),
        )

        val result = repository.checkAccess()

        assertEquals(
            "allowed=false must map to AccessResult.Pending (never Allowed)",
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

    // -------------------------------------------------------------------------
    // 6. 200 with unparsable body → Error (must never silently fail open)
    // -------------------------------------------------------------------------

    @Test
    fun `200 with unparsable body maps to AccessResult Error`() = runTest(testDispatcher) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not json"),
        )

        val result = repository.checkAccess()

        assertTrue(
            "A 200 with an unparsable body must map to Error, never Allowed; got $result",
            result is AccessResult.Error,
        )
    }

    // =========================================================================
    // FLAG OFF — status-based rollback path (MOBILE-27, three new tests)
    // =========================================================================

    // -------------------------------------------------------------------------
    // 7. Flag OFF + 200 → Allowed(admin=false) regardless of body content
    //    Critical regression test: under the OLD behavior any 200 was Allowed.
    // -------------------------------------------------------------------------

    @Test
    fun `flag OFF and 200 maps to Allowed admin false regardless of body`() =
        runTest(testDispatcher) {
            // Build a repo with the flag explicitly OFF (present=false in the catalog).
            val repoFlagOff = AccessRepositoryImpl(
                accessApi = accessApi,
                ioDispatcher = testDispatcher,
                catalogRepository = FakeCatalogRepository(flagOn = false),
            )

            // Body says allowed=false — but the OLD status-based path ignores the body.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"allowed":false,"admin":false}"""),
            )

            val result = repoFlagOff.checkAccess()

            assertEquals(
                "Flag OFF: HTTP 200 must map to Allowed(admin=false) regardless of body content " +
                    "(status-based rollback path). Got: $result",
                AccessResult.Allowed(admin = false),
                result,
            )
        }

    // -------------------------------------------------------------------------
    // 8. Flag OFF + 401 → Unauthorized
    // -------------------------------------------------------------------------

    @Test
    fun `flag OFF and 401 maps to Unauthorized`() = runTest(testDispatcher) {
        val repoFlagOff = AccessRepositoryImpl(
            accessApi = accessApi,
            ioDispatcher = testDispatcher,
            catalogRepository = FakeCatalogRepository(flagOn = false),
        )

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val result = repoFlagOff.checkAccess()

        assertEquals(
            "Flag OFF: HTTP 401 must still map to AccessResult.Unauthorized",
            AccessResult.Unauthorized,
            result,
        )
    }

    // -------------------------------------------------------------------------
    // 9a. Flag OFF + 500 → Error
    // -------------------------------------------------------------------------

    @Test
    fun `flag OFF and 500 maps to Error`() = runTest(testDispatcher) {
        val repoFlagOff = AccessRepositoryImpl(
            accessApi = accessApi,
            ioDispatcher = testDispatcher,
            catalogRepository = FakeCatalogRepository(flagOn = false),
        )

        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repoFlagOff.checkAccess()

        assertTrue(
            "Flag OFF: HTTP 500 must map to AccessResult.Error, got $result",
            result is AccessResult.Error,
        )
    }

    // =========================================================================
    // FLAG MISSING — Option-A safe default (body-based, NOT old status-based)
    //
    // This is the key MOBILE-27 Option-A contract test. When gate_access_check_body
    // is absent from the catalog map entirely (FakeCatalogRepository.flagMissing = true),
    // AccessRepositoryImpl MUST use the body-based path (safe default), NOT the old
    // status-based path.
    //
    // Proof: a 200 with allowed=false must return Pending (body-based), NOT Allowed(admin=false)
    // (status-based). If the impl naively calls catalogOn() which returns false for missing keys,
    // and routes that false to the old path, this test will catch it.
    // =========================================================================

    // -------------------------------------------------------------------------
    // 9b. Flag MISSING → body-based (safe/new path) — Option-A invariant
    // -------------------------------------------------------------------------

    @Test
    fun `flag MISSING falls back to body-based path not old status-based path`() =
        runTest(testDispatcher) {
            // FakeCatalogRepository with flagMissing=true returns null from its internal map,
            // simulating a catalog that has never seen this key (e.g. first launch, cache empty).
            val repoFlagMissing = AccessRepositoryImpl(
                accessApi = accessApi,
                ioDispatcher = testDispatcher,
                catalogRepository = FakeCatalogRepository(flagMissing = true),
            )

            // Body says allowed=false.
            // Body-based path (safe/new) → Pending.
            // Status-based path (old, wrong) → Allowed(admin=false).
            // The test distinguishes the two paths conclusively.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"allowed":false,"admin":false}"""),
            )

            val result = repoFlagMissing.checkAccess()

            assertEquals(
                "OPTION-A CONTRACT: flag MISSING must fall back to body-based (safe) path. " +
                    "200 with allowed=false must be Pending, NOT Allowed(admin=false). " +
                    "Got: $result",
                AccessResult.Pending,
                result,
            )
        }
}

// =============================================================================
// Test double — FakeCatalogRepository
//
// A hand-rolled fake (no mocking library) consistent with the manual-fake pattern used in
// AuthRepositoryImplTest and recommended by the PLAN.
//
// Modes:
//   FakeCatalogRepository(flagOn = true)    → always returns true  (flag ON, present)
//   FakeCatalogRepository(flagOn = false)   → always returns false (flag OFF, present=false)
//   FakeCatalogRepository(flagMissing=true) → simulates a catalog where the key is absent.
//     Under Option A, AccessRepositoryImpl must NOT route this through the old status-based path.
//     The fake itself returns false (same as the real catalogOn() for absent keys) — the
//     difference is the IMPL must call a separate helper / use getOrDefault with true sentinel,
//     not naively call catalogOn() and treat false as OFF.
//
// NOTE: The flagMissing mode intentionally exposes the same external value (false) as flagOn=false
// from the fake's perspective. The test exercises that AccessRepositoryImpl distinguishes between
// "flag explicitly set to false" and "flag absent" internally — which requires the Option-A
// inversion at the call site in AccessRepositoryImpl (e.g. using a different API than catalogOn).
// =============================================================================
private class FakeCatalogRepository(
    /** When true, the flag is present and ON. When false, present and explicitly OFF. */
    private val flagOn: Boolean = false,
    /**
     * When true, simulates a completely empty catalog (the key is ABSENT).
     * The 1-arg [catalogOn] returns false (interface contract for missing keys), but the
     * 2-arg overload returns the caller's [default] — exactly like the real getOrDefault.
     * AccessRepositoryImpl relies on the 2-arg default=true (Option A) so a missing flag
     * routes to the SAFE body-based path, never the old status-based one.
     */
    private val flagMissing: Boolean = false,
) : CatalogRepository {

    init {
        require(!(flagMissing && flagOn)) {
            "flagMissing and flagOn cannot both be true in FakeCatalogRepository"
        }
    }

    override suspend fun refresh() = Unit

    override fun catalogOn(flag: String): Boolean =
        if (flagMissing) false else flagOn

    override fun catalogOn(flag: String, default: Boolean): Boolean =
        if (flagMissing) default else flagOn
}
