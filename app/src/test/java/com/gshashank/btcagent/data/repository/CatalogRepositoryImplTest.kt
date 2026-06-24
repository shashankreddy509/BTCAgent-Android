package com.gshashank.btcagent.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gshashank.btcagent.data.network.CatalogApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.io.File
import java.util.UUID

/**
 * JVM unit tests for [CatalogRepositoryImpl] — MOBILE-29.
 *
 * Rewrites the MOBILE-26 tests to match the versioned platform+id API:
 *   GET /api/catalogs?platform=<P>&version=<V>
 *
 * Responses are either:
 *   {"changed":false,"version":N}                       — no-op
 *   {"changed":true,"version":N,"catalogs":{"id":bool}} — full snapshot replacement
 *
 * Flag identifiers are numeric Ints (android = 1xxxxx).
 *
 * Setup pattern:
 *   - [MockWebServer] for HTTP — same as the MOBILE-26 suite.
 *   - [StandardTestDispatcher] — the init{} poll loop does NOT fire eagerly; tests call
 *     [CatalogRepositoryImpl.refresh] manually.
 *   - Test-scoped [DataStore]<[Preferences]> via [PreferenceDataStoreFactory.create] pointed at
 *     a unique temporary directory per test, cleared in [tearDown].
 *
 * All 11 tests are expected to FAIL (red) until [CatalogRepositoryImpl] is rewritten for MOBILE-29.
 * The current implementation:
 *   - has no [DataStore] constructor parameter
 *   - exposes [catalogOn(String)] not [isEnabled(Int)]
 *   - calls [CatalogApi.getCatalogs()] with no query params (old flat-map contract)
 * None of those match the new contract, so every test in this file will fail to compile or
 * fail at runtime until the full MOBILE-29 implementation lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogRepositoryImplTest {

    private val mockWebServer = MockWebServer()

    // StandardTestDispatcher: coroutines scheduled but NOT run eagerly.
    // Tests call refresh() manually and drive time via advanceUntilIdle().
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Mirrors the production Json provided by NetworkModule (ignoreUnknownKeys = true).
    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private lateinit var catalogApi: CatalogApi
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: CatalogRepositoryImpl

    // Unique temp dir per test so DataStore files never bleed between tests.
    private val tempDir: File = File(
        System.getProperty("java.io.tmpdir"),
        "catalog_test_${UUID.randomUUID()}",
    ).also { it.mkdirs() }

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

        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempDir, "catalog_prefs.preferences_pb") },
        )

        repository = CatalogRepositoryImpl(
            dataStore = dataStore,
            catalogApi = catalogApi,
            json = testJson,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 1. changed:true response replaces flag map and persists version
    // =========================================================================

    @Test
    fun `changed true response replaces flag map and persists version`() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":7,"catalogs":{"100001":true,"100002":false}}"""),
        )

        repository.refresh()

        assertTrue(
            "isEnabled(100001) must be true when server returned \"100001\":true",
            repository.isEnabled(100001),
        )
        assertFalse(
            "isEnabled(100002) must be false when server returned \"100002\":false",
            repository.isEnabled(100002),
        )

        // Verify DataStore persistence — the impl must write version and JSON map.
        val prefs = dataStore.data.first()
        val persistedVersion = prefs[intPreferencesKey("catalog_version")]
        val persistedMapJson = prefs[stringPreferencesKey("catalog_map_json")]

        assertTrue(
            "DataStore must persist catalog_version == 7, got $persistedVersion",
            persistedVersion == 7,
        )
        assertTrue(
            "DataStore catalog_map_json must contain key 100001, got $persistedMapJson",
            persistedMapJson?.contains("100001") == true,
        )
        assertTrue(
            "DataStore catalog_map_json must contain key 100002, got $persistedMapJson",
            persistedMapJson?.contains("100002") == true,
        )
    }

    // =========================================================================
    // 2. changed:false response is a no-op
    // =========================================================================

    @Test
    fun `changed false response is a no-op`() = testScope.runTest {
        // First refresh: populate map with 100001=true, version=5.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":5,"catalogs":{"100001":true}}"""),
        )
        repository.refresh()

        assertTrue(
            "Precondition: isEnabled(100001) must be true after first refresh",
            repository.isEnabled(100001),
        )

        // Second refresh: server says nothing changed.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":false,"version":5}"""),
        )
        repository.refresh()

        // Map must remain unchanged.
        assertTrue(
            "isEnabled(100001) must still be true after a changed:false no-op response",
            repository.isEnabled(100001),
        )

        // DataStore version must still be 5 — no overwrite should have occurred.
        val prefs = dataStore.data.first()
        val persistedVersion = prefs[intPreferencesKey("catalog_version")]
        assertTrue(
            "DataStore catalog_version must still be 5 after a no-op response, got $persistedVersion",
            persistedVersion == 5,
        )
    }

    // =========================================================================
    // 3. changed:true never merges — replaces whole map
    // =========================================================================

    @Test
    fun `changed true replaces entire map not merges`() = testScope.runTest {
        // First refresh: map has 100001=true and 100002=true.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":1,"catalogs":{"100001":true,"100002":true}}"""),
        )
        repository.refresh()

        assertTrue("Precondition: 100001 must be true", repository.isEnabled(100001))
        assertTrue("Precondition: 100002 must be true", repository.isEnabled(100002))

        // Second refresh: changed:true but only 100001 is present (100002 is gone).
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":2,"catalogs":{"100001":false}}"""),
        )
        repository.refresh()

        assertFalse(
            "100001 must be false after the second refresh set it explicitly to false",
            repository.isEnabled(100001),
        )
        assertFalse(
            "100002 must be false — absent from second refresh (whole-map replace, not merge)",
            repository.isEnabled(100002),
        )
    }

    // =========================================================================
    // 4. missing id returns false (standard single-arg overload)
    // =========================================================================

    @Test
    fun `missing id returns false with single-arg isEnabled`() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":1,"catalogs":{"100001":true}}"""),
        )
        repository.refresh()

        assertFalse(
            "isEnabled(99999) must be false when 99999 is absent from the server map",
            repository.isEnabled(99999),
        )
    }

    // =========================================================================
    // 5. isEnabled(id, default=true) with explicit server false — explicit value wins
    // =========================================================================

    @Test
    fun `isEnabled with default true but explicit server false returns false`() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":1,"catalogs":{"100001":false}}"""),
        )
        repository.refresh()

        assertFalse(
            "An explicit server-side false must win over a caller default=true",
            repository.isEnabled(100001, default = true),
        )
    }

    // =========================================================================
    // 6. isEnabled(id, default=true) with absent key — returns default (Option-A)
    // =========================================================================

    @Test
    fun `isEnabled with absent key returns caller default Option-A`() = testScope.runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                // Only 100001 is present; id=0 is intentionally absent.
                .setBody("""{"changed":true,"version":1,"catalogs":{"100001":true}}"""),
        )
        repository.refresh()

        assertTrue(
            "isEnabled(0, default=true) must return true — absent id falls back to caller default",
            repository.isEnabled(0, default = true),
        )
        assertFalse(
            "isEnabled(0, default=false) must return false — absent id falls back to caller default",
            repository.isEnabled(0, default = false),
        )
    }

    // =========================================================================
    // 7. network failure keeps last-known map and never throws
    // =========================================================================

    @Test
    fun `network failure keeps last-known map and never throws`() = testScope.runTest {
        // First refresh: successful — 100001=true.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":3,"catalogs":{"100001":true}}"""),
        )
        repository.refresh()

        assertTrue(
            "Precondition: isEnabled(100001) must be true after successful refresh",
            repository.isEnabled(100001),
        )

        // Shut down the server — next call will throw IOException.
        mockWebServer.shutdown()

        // Must not throw — fail-open contract swallows IOException.
        repository.refresh()

        // Last-known map must still be intact.
        assertTrue(
            "isEnabled(100001) must still be true after a network failure (last-known-good)",
            repository.isEnabled(100001),
        )
    }

    // =========================================================================
    // 8. HTTP 500 on second refresh keeps last-known map
    // =========================================================================

    @Test
    fun `HTTP 500 on second refresh keeps last-known map`() = testScope.runTest {
        // First refresh: successful.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":4,"catalogs":{"100001":true}}"""),
        )
        repository.refresh()

        assertTrue(
            "Precondition: isEnabled(100001) must be true after successful first refresh",
            repository.isEnabled(100001),
        )

        // Second refresh: HTTP 500.
        mockWebServer.enqueue(
            MockResponse().setResponseCode(500),
        )
        repository.refresh()

        assertTrue(
            "isEnabled(100001) must still be true after an HTTP 500 on second refresh",
            repository.isEnabled(100001),
        )
    }

    // =========================================================================
    // 9. cold-start loads persisted map before first network fetch
    //    (last-known-good on restart)
    // =========================================================================

    @Test
    fun `cold-start loads persisted map before first network fetch`() = testScope.runTest {
        // Manually seed DataStore — simulates a previously written state surviving process death.
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(intPreferencesKey("catalog_version"), 1)
                set(stringPreferencesKey("catalog_map_json"), """{"100001":true}""")
            }
        }

        // Construct a NEW instance without calling refresh(), simulating a cold start.
        // The init{} block must read the persisted DataStore and seed the in-memory StateFlow.
        val coldStartRepo = CatalogRepositoryImpl(
            dataStore = dataStore,
            catalogApi = catalogApi,
            json = testJson,
            ioDispatcher = testDispatcher,
        )

        // Advance the dispatcher so the init{} seed coroutine can execute.
        advanceUntilIdle()

        assertTrue(
            "isEnabled(100001) must be true on cold start — seeded from persisted DataStore",
            coldStartRepo.isEnabled(100001),
        )
    }

    // =========================================================================
    // 10. initial state with empty DataStore returns false for all ids
    // =========================================================================

    @Test
    fun `initial state with empty DataStore returns false for all ids`() = testScope.runTest {
        // repo was constructed in setUp() with an empty DataStore; refresh() not called.
        // The poll loop coroutine is scheduled but not yet executed (StandardTestDispatcher).
        assertFalse(
            "isEnabled(100001) must be false before any refresh or DataStore seed",
            repository.isEnabled(100001),
        )
        assertFalse(
            "isEnabled(0) must be false before any refresh or DataStore seed",
            repository.isEnabled(0),
        )
    }

    // =========================================================================
    // 11. changed:true with null catalogs field is malformed → keep last-known-good (no wipe, no crash)
    // =========================================================================

    @Test
    fun `changed true with null catalogs keeps last-known-good map`() = testScope.runTest {
        // First: a valid snapshot establishes last-known-good.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":8,"catalogs":{"100001":true}}"""),
        )
        repository.refresh()

        // Then: malformed changed:true with the "catalogs" key omitted. A null snapshot on
        // changed:true is malformed; the impl must NOT wipe the cached map to empty.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true,"version":9}"""),
        )

        // Must not throw.
        repository.refresh()

        assertTrue(
            "isEnabled(100001) must stay true — malformed null catalogs must not wipe last-known-good",
            repository.isEnabled(100001),
        )
    }
}
