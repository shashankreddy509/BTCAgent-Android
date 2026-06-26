package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Timeframe
import com.gshashank.btcagent.data.network.VolumeProfileApi
import com.gshashank.btcagent.data.network.VolumeProfileDto
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [VolumeProfileRepositoryImpl] — MOBILE-14.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed
 * by the Retrofit + kotlinx.serialization layer and then mapped by the repository.
 *
 * Endpoint: GET /api/volume-profiles — PUBLIC, no auth.
 * 404 while server feature toggle is OFF: `{"detail":"volume_profiles feature is disabled"}`.
 *
 * Repository behavior under test:
 *  - 200 with non-empty profiles → [VolumeProfileResult.Success]; reversal applied (newest first).
 *  - 200 with all empty lists → [VolumeProfileResult.Success] with isEmpty == true.
 *  - 404 → [VolumeProfileResult.Error] with message containing "404".
 *  - 500 → [VolumeProfileResult.Error] with message containing "500".
 *  - Network exception (server closed) → [VolumeProfileResult.Error] (never throws).
 *  - [CancellationException] is rethrown, not swallowed.
 *
 * All tests MUST fail (red) until [VolumeProfileRepositoryImpl] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeProfileRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var volumeProfileApi: VolumeProfileApi
    private lateinit var repository: VolumeProfileRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private fun snapshotJson(
        poc: Double = 50000.0,
        vah: Double = 51000.0,
        vaLow: Double = 49000.0,
        lo: Double = 48000.0,
        hi: Double = 52000.0,
        start: String = "2026-06-25T00:00:00+00:00",
        current: Boolean = false,
    ): String = """
        {
          "poc": $poc,
          "vah": $vah,
          "val": $vaLow,
          "lo": $lo,
          "hi": $hi,
          "start": "$start",
          "current": $current
        }
    """.trimIndent()

    private fun volumeProfileResponseJson(
        changed: Boolean = true,
        version: Int = 1,
        h4Snapshots: List<String> = emptyList(),
        h12Snapshots: List<String> = emptyList(),
        d1Snapshots: List<String> = emptyList(),
    ): String {
        val h4Json = h4Snapshots.joinToString(",\n")
        val h12Json = h12Snapshots.joinToString(",\n")
        val d1Json = d1Snapshots.joinToString(",\n")
        return """
            {
              "changed": $changed,
              "version": $version,
              "profiles": {
                "4h": [$h4Json],
                "12h": [$h12Json],
                "1d": [$d1Json]
              }
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

        volumeProfileApi = retrofit.create(VolumeProfileApi::class.java)

        repository = VolumeProfileRepositoryImpl(
            api = volumeProfileApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. 200 with 2 non-empty h4 snapshots → Success; newest-first after reversal
    // =========================================================================

    @Test
    fun `success 200 with snapshots returns Success with reversed profiles newest first`() =
        runTest(testDispatcher) {
            val olderSnapshot = snapshotJson(
                poc = 50000.0, vah = 51000.0, vaLow = 49000.0, lo = 48000.0, hi = 52000.0,
                start = "2026-06-23T00:00:00+00:00",
            )
            val newerSnapshot = snapshotJson(
                poc = 51000.0, vah = 52000.0, vaLow = 50000.0, lo = 49000.0, hi = 53000.0,
                start = "2026-06-24T00:00:00+00:00",
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        volumeProfileResponseJson(
                            changed = true,
                            version = 1,
                            h4Snapshots = listOf(olderSnapshot, newerSnapshot),
                        ),
                    ),
            )

            val result = repository.fetch()

            assertTrue(
                "HTTP 200 with non-empty profiles must return VolumeProfileResult.Success, got $result",
                result is VolumeProfileResult.Success,
            )
            val data = (result as VolumeProfileResult.Success).data
            val h4 = data.timeframes[Timeframe.H4]
            assertTrue("H4 sessions must not be null or empty", h4 != null && h4.isNotEmpty())
            assertEquals("H4 must have 2 sessions", 2, h4!!.size)
            assertEquals(
                "First session (after reversal) must be the newer one (2026-06-24)",
                "2026-06-24T00:00:00+00:00",
                h4[0].start,
            )
            assertEquals(
                "Second session (after reversal) must be the older one (2026-06-23)",
                "2026-06-23T00:00:00+00:00",
                h4[1].start,
            )
        }

    // =========================================================================
    // 2. 200 with all empty lists → Success with isEmpty == true
    // =========================================================================

    @Test
    fun `success 200 with all empty profile lists returns Success with isEmpty true`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        volumeProfileResponseJson(
                            changed = true,
                            version = 0,
                        ),
                    ),
            )

            val result = repository.fetch()

            assertTrue(
                "HTTP 200 with all empty profiles must return VolumeProfileResult.Success, got $result",
                result is VolumeProfileResult.Success,
            )
            val data = (result as VolumeProfileResult.Success).data
            assertTrue(
                "VolumeProfileData.isEmpty must be true when all timeframe lists are empty",
                data.isEmpty,
            )
        }

    // =========================================================================
    // 3. HTTP 404 → Error with message containing "404"
    // =========================================================================

    @Test
    fun `HTTP 404 returns VolumeProfileResult Error with message containing 404`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"detail":"volume_profiles feature is disabled"}"""),
            )

            val result = repository.fetch()

            assertTrue(
                "HTTP 404 must return VolumeProfileResult.Error, got $result",
                result is VolumeProfileResult.Error,
            )
            val error = result as VolumeProfileResult.Error
            assertTrue(
                "Error message must contain \"404\", got: ${error.message}",
                error.message?.contains("404") == true,
            )
        }

    // =========================================================================
    // 4. HTTP 500 → Error with message containing "500"
    // =========================================================================

    @Test
    fun `HTTP 500 returns VolumeProfileResult Error with message containing 500`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error"),
            )

            val result = repository.fetch()

            assertTrue(
                "HTTP 500 must return VolumeProfileResult.Error, got $result",
                result is VolumeProfileResult.Error,
            )
            val error = result as VolumeProfileResult.Error
            assertTrue(
                "Error message must contain \"500\", got: ${error.message}",
                error.message?.contains("500") == true,
            )
        }

    // =========================================================================
    // 5. Network exception (server closed) → Error (never throws)
    // =========================================================================

    @Test
    fun `network exception returns VolumeProfileResult Error and never throws`() =
        runTest(testDispatcher) {
            // Shut down the server so any HTTP call throws an IOException.
            mockWebServer.shutdown()

            val result = repository.fetch()

            assertTrue(
                "A network IOException must return VolumeProfileResult.Error — " +
                    "repository must never throw to its callers",
                result is VolumeProfileResult.Error,
            )
        }

    // =========================================================================
    // 6. CancellationException is rethrown, not swallowed
    // =========================================================================

    @Test
    fun `CancellationException from the API is rethrown and not swallowed as Error`() =
        runTest(testDispatcher) {
            val cancellingApi = object : VolumeProfileApi {
                override suspend fun get(): retrofit2.Response<VolumeProfileDto> {
                    throw CancellationException("cancelled mid-flight")
                }
            }
            val cancellingRepo = VolumeProfileRepositoryImpl(
                api = cancellingApi,
                ioDispatcher = testDispatcher,
            )

            var rethrew = false
            try {
                cancellingRepo.fetch()
            } catch (e: CancellationException) {
                rethrew = true
            }
            assertTrue(
                "fetch() must rethrow CancellationException, not return VolumeProfileResult.Error",
                rethrew,
            )
        }

    // =========================================================================
    // 7. Malformed JSON → Error (never throws)
    // =========================================================================

    @Test
    fun `malformed JSON returns VolumeProfileResult Error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{ not valid json !!!"),
            )

            val result = repository.fetch()

            assertTrue(
                "Malformed JSON must return VolumeProfileResult.Error and never throw, got $result",
                result is VolumeProfileResult.Error,
            )
        }

    // =========================================================================
    // 8. 200 single h4 snapshot → correct field mapping (poc, vah, vaLow, lo, hi, start)
    // =========================================================================

    @Test
    fun `200 with single h4 snapshot maps all fields correctly including vaLow via SerialName`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        volumeProfileResponseJson(
                            changed = true,
                            version = 7,
                            h4Snapshots = listOf(
                                snapshotJson(
                                    poc = 50000.0,
                                    vah = 51000.0,
                                    vaLow = 49000.0,
                                    lo = 48000.0,
                                    hi = 52000.0,
                                    start = "2026-06-25T00:00:00+00:00",
                                ),
                            ),
                        ),
                    ),
            )

            val result = repository.fetch()

            assertTrue("Must be Success, got $result", result is VolumeProfileResult.Success)
            val data = (result as VolumeProfileResult.Success).data
            val h4 = data.timeframes[Timeframe.H4]
            assertTrue("H4 must not be null or empty", h4 != null && h4.isNotEmpty())
            val session = h4!![0]
            assertEquals("poc must be 50000.0", 50000.0, session.poc!!, 0.001)
            assertEquals("vah must be 51000.0", 51000.0, session.vah!!, 0.001)
            assertEquals(
                "vaLow must be 49000.0 — @SerialName(\"val\") must deserialize correctly",
                49000.0,
                session.vaLow!!,
                0.001,
            )
            assertEquals("lo must be 48000.0", 48000.0, session.lo!!, 0.001)
            assertEquals("hi must be 52000.0", 52000.0, session.hi!!, 0.001)
            assertEquals(
                "start must be preserved from the JSON",
                "2026-06-25T00:00:00+00:00",
                session.start,
            )
            assertEquals("version must be carried through to the domain model", 7, data.version)
        }
}
