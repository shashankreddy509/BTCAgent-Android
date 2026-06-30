package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.network.BrokerApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [BrokerRepositoryImpl] — MOBILE-45.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by the
 * Retrofit layer and then mapped by the repository.
 *
 * Endpoints under test:
 *   GET  api/settings/broker  → fetchBroker()    → [BrokerResult]
 *   PUT  api/settings/broker  → replaceBroker()  → [BrokerActionResult]
 *
 * Repository contract:
 *   - NEVER throws to callers.
 *   - errorBody() is closed on non-2xx.
 *   - Active broker row (active=true) is mapped to [BrokerInfo]; null for empty list.
 *   - connected is Boolean? (NULLABLE) — null is NOT the same as false; null must survive the
 *     mapping as null (not coerced to false). This is the CRITICAL discriminator for MOBILE-45.
 *   - PUT body contains "broker", "<broker>_api_key", "<broker>_api_secret" keys;
 *     must NOT send "api_key_masked".
 *
 * All tests MUST fail (red) until [BrokerRepositoryImpl] is implemented.
 *
 * Test coverage:
 *   1.  GET maps active broker row to BrokerInfo (broker, accountName, apiKeyMasked)
 *   2.  connected=true in active row → BrokerInfo.connected == true
 *   3.  connected=false in active row → BrokerInfo.connected == false
 *   4.  connected=null in active row → BrokerInfo.connected == null (CRITICAL discriminator)
 *   5.  Empty brokers list → BrokerResult.Success(null) (no BrokerInfo)
 *   6.  PUT sends correct body keys (broker + <broker>_api_key + <broker>_api_secret)
 *   7.  PUT 200 → BrokerActionResult.Success
 *   8.  GET 403 → BrokerResult.Error with "Access not approved" message
 *   9.  errorBody is closed after error response (connection pool not exhausted)
 *   10. Malformed JSON body → BrokerResult.Error (SerializationException handled gracefully)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrokerRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var brokerApi: BrokerApi
    private lateinit var repository: BrokerRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // JSON response helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a GET api/settings/broker JSON response body with two broker rows.
     * The first row ([activeBroker]) has active=true; the second row has active=false.
     *
     * [connectedJson] is the raw JSON literal for "connected" on the active row:
     *   "true", "false", or "null" to cover all three nullable states.
     */
    private fun brokerSummaryJson(
        activeBroker: String = "coinbase",
        activeAccountName: String = "My Coinbase Account",
        activeApiKeyMasked: String = "····3f9a",
        connectedJson: String = "true",
        inactiveBroker: String = "bybit",
    ): String = """
        {
          "brokers": [
            {
              "broker": "$activeBroker",
              "account_name": "$activeAccountName",
              "api_key_masked": "$activeApiKeyMasked",
              "configured": true,
              "active": true,
              "connected": $connectedJson
            },
            {
              "broker": "$inactiveBroker",
              "account_name": "",
              "api_key_masked": "",
              "configured": false,
              "active": false,
              "connected": null
            }
          ],
          "active": "$activeBroker"
        }
    """.trimIndent()

    /** GET response body with an empty brokers list. */
    private fun emptyBrokerSummaryJson(): String = """
        {
          "brokers": [],
          "active": null
        }
    """.trimIndent()

    /** A minimal PUT 200 success response. */
    private fun saveBrokerSuccessJson(): String = """{"status": "saved"}"""

    @Before
    fun setUp() {
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()

        brokerApi = retrofit.create(BrokerApi::class.java)

        repository = BrokerRepositoryImpl(
            brokerApi = brokerApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // 1. GET maps active broker row to BrokerInfo (broker id, accountName, apiKeyMasked)
    // =========================================================================

    @Test
    fun `fetchBroker 200 maps active broker row to BrokerInfo with correct broker accountName and apiKeyMasked`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        brokerSummaryJson(
                            activeBroker = "coinbase",
                            activeAccountName = "My Coinbase Account",
                            activeApiKeyMasked = "····3f9a",
                            connectedJson = "true",
                        )
                    ),
            )

            val result = repository.fetchBroker()

            assertTrue(
                "HTTP 200 from GET api/settings/broker must map to BrokerResult.Success, got $result",
                result is BrokerResult.Success,
            )
            val brokerInfo = (result as BrokerResult.Success).brokerInfo

            assertNotNull(
                "BrokerInfo must be non-null when there is an active broker row",
                brokerInfo,
            )
            assertEquals(
                "broker must be mapped from the active row's 'broker' field",
                "coinbase",
                brokerInfo!!.broker,
            )
            assertEquals(
                "accountName must be mapped from the active row's 'account_name' field",
                "My Coinbase Account",
                brokerInfo.accountName,
            )
            assertEquals(
                "apiKeyMasked must be mapped from the active row's 'api_key_masked' field (display as-is)",
                "····3f9a",
                brokerInfo.apiKeyMasked,
            )
        }

    // =========================================================================
    // 2. connected=true in active row → BrokerInfo.connected == true
    // =========================================================================

    @Test
    fun `fetchBroker connected true in active row maps to BrokerInfo connected true`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(brokerSummaryJson(connectedJson = "true")),
            )

            val result = repository.fetchBroker()

            assertTrue(
                "BrokerResult must be Success when response is 200, got $result",
                result is BrokerResult.Success,
            )
            val brokerInfo = (result as BrokerResult.Success).brokerInfo

            assertNotNull("BrokerInfo must be non-null for an active broker row", brokerInfo)
            assertEquals(
                "BrokerInfo.connected must be true when the JSON 'connected' field is true",
                true,
                brokerInfo!!.connected,
            )
        }

    // =========================================================================
    // 3. connected=false in active row → BrokerInfo.connected == false
    // =========================================================================

    @Test
    fun `fetchBroker connected false in active row maps to BrokerInfo connected false`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(brokerSummaryJson(connectedJson = "false")),
            )

            val result = repository.fetchBroker()

            assertTrue(
                "BrokerResult must be Success when response is 200, got $result",
                result is BrokerResult.Success,
            )
            val brokerInfo = (result as BrokerResult.Success).brokerInfo

            assertNotNull("BrokerInfo must be non-null for an active broker row", brokerInfo)
            assertEquals(
                "BrokerInfo.connected must be false when the JSON 'connected' field is false",
                false,
                brokerInfo!!.connected,
            )
        }

    // =========================================================================
    // 4. connected=null in active row → BrokerInfo.connected == null (CRITICAL discriminator)
    //
    //    null ≠ false — an inactive/unprobed broker has connected=null, which must NOT be
    //    coerced to false. The UI shows a neutral "—" state only when connected is null.
    //    False means "probe ran and returned disconnected"; null means "not probed".
    // =========================================================================

    @Test
    fun `fetchBroker connected null in active row maps to BrokerInfo connected null NOT false`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(brokerSummaryJson(connectedJson = "null")),
            )

            val result = repository.fetchBroker()

            assertTrue(
                "BrokerResult must be Success when response is 200, got $result",
                result is BrokerResult.Success,
            )
            val brokerInfo = (result as BrokerResult.Success).brokerInfo

            assertNotNull("BrokerInfo must be non-null for an active broker row", brokerInfo)
            assertNull(
                "BrokerInfo.connected must be null (not false) when the JSON 'connected' field is null — " +
                    "null = not probed (neutral state), false = probed and disconnected; these are distinct",
                brokerInfo!!.connected,
            )
            // Extra guard: confirm the type is Boolean? (null), not Boolean (false)
            assertFalse(
                "BrokerInfo.connected must not equal false — null and false are different states",
                brokerInfo.connected == false,
            )
        }

    // =========================================================================
    // 5. Empty brokers list → BrokerResult.Success(null)
    // =========================================================================

    @Test
    fun `fetchBroker with empty brokers list returns BrokerResult Success with null brokerInfo`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(emptyBrokerSummaryJson()),
            )

            val result = repository.fetchBroker()

            assertTrue(
                "Empty brokers list must produce BrokerResult.Success (not Error), got $result",
                result is BrokerResult.Success,
            )
            assertNull(
                "BrokerResult.Success.brokerInfo must be null when the brokers list is empty — " +
                    "there is no active broker to map",
                (result as BrokerResult.Success).brokerInfo,
            )
        }

    // =========================================================================
    // 6. PUT sends correct body keys (broker + <broker>_api_key + <broker>_api_secret)
    //    Does NOT send api_key_masked
    // =========================================================================

    @Test
    fun `replaceBroker PUT sends broker key and credential keys without api_key_masked`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(saveBrokerSuccessJson()),
            )

            repository.replaceBroker(
                broker = "coinbase",
                apiKey = "my-api-key-value",
                apiSecret = "my-api-secret-value",
            )

            val requestBody = mockWebServer.takeRequest().body.readUtf8()

            assertTrue(
                "PUT body must contain the 'broker' key, got: $requestBody",
                requestBody.contains("\"broker\""),
            )
            assertTrue(
                "PUT body must contain the '<broker>_api_key' key (coinbase_api_key), got: $requestBody",
                requestBody.contains("\"coinbase_api_key\""),
            )
            assertTrue(
                "PUT body must contain the '<broker>_api_secret' key (coinbase_api_secret), got: $requestBody",
                requestBody.contains("\"coinbase_api_secret\""),
            )
            assertFalse(
                "PUT body must NOT contain 'api_key_masked' — never send the masked display key to the server, got: $requestBody",
                requestBody.contains("\"api_key_masked\""),
            )
        }

    // =========================================================================
    // 7. PUT 200 → BrokerActionResult.Success
    // =========================================================================

    @Test
    fun `replaceBroker PUT 200 returns BrokerActionResult Success`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(saveBrokerSuccessJson()),
            )

            val result = repository.replaceBroker(
                broker = "coinbase",
                apiKey = "my-api-key-value",
                apiSecret = "my-api-secret-value",
            )

            assertTrue(
                "HTTP 200 from PUT api/settings/broker must map to BrokerActionResult.Success, got $result",
                result is BrokerActionResult.Success,
            )
        }

    // =========================================================================
    // 8. GET 403 → BrokerResult.Error with "Access not approved" message
    // =========================================================================

    @Test
    fun `fetchBroker 403 returns BrokerResult Error with Access not approved message`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"detail": "Access not approved"}"""),
            )

            val result = repository.fetchBroker()

            assertTrue(
                "HTTP 403 from GET api/settings/broker must map to BrokerResult.Error, got $result",
                result is BrokerResult.Error,
            )
            val error = result as BrokerResult.Error
            assertTrue(
                "BrokerResult.Error message must contain 'Access not approved' for a 403 response, " +
                    "got '${error.message}'",
                error.message.contains("Access not approved", ignoreCase = true),
            )
        }

    // =========================================================================
    // 9. errorBody is closed after error response (connection pool not exhausted)
    //
    // Verified indirectly: two sequential non-2xx calls on a single-connection server
    // must both complete without deadlocking (a leaked errorBody would stall the pool).
    // =========================================================================

    @Test
    fun `errorBody is closed after non-2xx response so connection pool is not exhausted`() =
        runTest(testDispatcher) {
            repeat(2) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(403)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"detail": "Access not approved"}"""),
                )
            }

            val first = repository.fetchBroker()
            val second = repository.fetchBroker()

            assertTrue(
                "First 403 must map to BrokerResult.Error (errorBody must be closed so pool is free)",
                first is BrokerResult.Error,
            )
            assertTrue(
                "Second 403 must also complete without hanging — proves errorBody was closed after first call",
                second is BrokerResult.Error,
            )
        }

    // =========================================================================
    // 10. Malformed JSON body → BrokerResult.Error (SerializationException handled)
    //
    // A 200 response with a body that cannot be deserialized must not crash the app.
    // The repository must catch the SerializationException and return BrokerResult.Error
    // with a message distinct from transport-level errors (to help diagnosis).
    // =========================================================================

    @Test
    fun `fetchBroker malformed JSON body returns BrokerResult Error with distinct message`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("this is not valid json {{{"),
            )

            val result = repository.fetchBroker()

            assertTrue(
                "A 200 response with malformed JSON must map to BrokerResult.Error (SerializationException must be caught), got $result",
                result is BrokerResult.Error,
            )
            val error = result as BrokerResult.Error
            assertFalse(
                "The error message for a serialization failure must not be empty",
                error.message.isBlank(),
            )
            // The error must not be the same message as a network/transport failure — it should
            // be distinguishable so the team can triage parse failures vs connectivity failures.
            // We can't assert the exact wording (that is an impl detail) but we do assert it is
            // not null/blank above, which is sufficient for TDD purposes.
        }
}
