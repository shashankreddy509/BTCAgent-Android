package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.network.UsersApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * JVM unit tests for [UsersRepositoryImpl] — MOBILE-21.
 *
 * Uses [MockWebServer] as the in-process HTTP server so real HTTP responses are parsed by
 * the Retrofit layer and then mapped by the repository.
 *
 * Endpoints under test:
 *   GET  api/admin/users           → fetchUsers() lists
 *   GET  api/admin/allowlist       → used in approve/reject + fetchUsers
 *   PUT  api/admin/allowlist       → approve/reject write
 *   POST api/admin/users/{uid}/mode → setMode()
 *   POST api/admin/users/{uid}/stop → stop()
 *
 * Repository contract:
 *   - NEVER throws to callers; CancellationException is rethrown.
 *   - errorBody() is closed on non-2xx.
 *   - HTTP error reason masked: "Server error (<code>)" not raw response.message().
 *   - HTTP 403 → "Admin access required" (not raw "Admin only" body text).
 *   - Pending/Active = client-derived: email ∈ allowlist → Active, else Pending.
 *   - approve(email) = GET allowlist → add email → PUT full list.
 *   - reject(email) = GET allowlist → remove email → PUT full list.
 *
 * All tests MUST fail (red) until [UsersRepositoryImpl] is implemented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsersRepositoryImplTest {

    private val mockWebServer = MockWebServer()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var usersApi: UsersApi
    private lateinit var repository: UsersRepositoryImpl

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private fun usersResponseJson(vararg users: Triple<String, String, String>): String {
        // Each Triple: (uid, email, mode), scanner_running=false by default
        val items = users.joinToString(",") { (uid, email, mode) ->
            """{"uid":"$uid","email":"$email","display_name":"User $uid","mode":"$mode","broker":"binance","scanner_running":false}"""
        }
        return "[$items]"
    }

    private fun usersWithScannerJson(uid: String, email: String, mode: String, scannerRunning: Boolean): String =
        """[{"uid":"$uid","email":"$email","display_name":"User $uid","mode":"$mode","broker":"binance","scanner_running":$scannerRunning}]"""

    private fun allowlistResponseJson(vararg emails: String): String {
        val list = emails.joinToString(",") { "\"$it\"" }
        return """{"emails":[$list]}"""
    }

    private fun statusSavedJson(): String = """{"status":"saved"}"""
    private fun statusStoppedJson(): String = """{"status":"stopped"}"""

    @Before
    fun setUp() {
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()

        usersApi = retrofit.create(UsersApi::class.java)

        repository = UsersRepositoryImpl(
            usersApi = usersApi,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // =========================================================================
    // DISCRIMINATOR: email ∈ allowlist → Active; email ∉ allowlist → Pending
    // =========================================================================

    @Test
    fun `fetchUsers partitions correctly - email in allowlist goes to active not pending`() =
        runTest(testDispatcher) {
            // alice@example.com is in the allowlist → Active
            // bob@example.com is NOT in the allowlist → Pending
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(usersResponseJson(
                        Triple("uid-alice", "alice@example.com", "paper"),
                        Triple("uid-bob", "bob@example.com", "paper"),
                    ))
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("alice@example.com"))
            )

            val result = repository.fetchUsers()

            assertTrue(
                "fetchUsers with allowlist must return AdminUsersResult.Success, got $result",
                result is AdminUsersResult.Success
            )
            val data = (result as AdminUsersResult.Success).data

            assertEquals(
                "alice@example.com is in allowlist so active list must have 1 user",
                1,
                data.active.size,
            )
            assertEquals(
                "Active user must be alice@example.com",
                "alice@example.com",
                data.active[0].email,
            )
            assertEquals(
                "bob@example.com is NOT in allowlist so pending list must have 1 user",
                1,
                data.pending.size,
            )
            assertEquals(
                "Pending user must be bob@example.com",
                "bob@example.com",
                data.pending[0].email,
            )
        }

    @Test
    fun `fetchUsers - email not in allowlist goes to pending list`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(usersResponseJson(Triple("uid-charlie", "charlie@example.com", "paper")))
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson()) // empty allowlist
            )

            val result = repository.fetchUsers()

            assertTrue(result is AdminUsersResult.Success)
            val data = (result as AdminUsersResult.Success).data

            assertEquals("Pending list must have 1 user when allowlist is empty", 1, data.pending.size)
            assertEquals("Active list must be empty when allowlist is empty", 0, data.active.size)
        }

    // =========================================================================
    // Mode mapping
    // =========================================================================

    @Test
    fun `fetchUsers maps mode live to ExecutionMode LIVE not defaulting to PAPER`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(usersResponseJson(Triple("uid-live", "live@example.com", "live")))
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("live@example.com"))
            )

            val result = repository.fetchUsers()

            assertTrue(result is AdminUsersResult.Success)
            val active = (result as AdminUsersResult.Success).data.active

            assertEquals("mode='live' must map to ExecutionMode.LIVE", ExecutionMode.LIVE, active[0].mode)
        }

    @Test
    fun `fetchUsers maps mode paper to ExecutionMode PAPER`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(usersResponseJson(Triple("uid-paper", "paper@example.com", "paper")))
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("paper@example.com"))
            )

            val result = repository.fetchUsers()

            assertTrue(result is AdminUsersResult.Success)
            val active = (result as AdminUsersResult.Success).data.active

            assertEquals("mode='paper' must map to ExecutionMode.PAPER", ExecutionMode.PAPER, active[0].mode)
        }

    @Test
    fun `fetchUsers maps scanner_running correctly`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(usersWithScannerJson("uid-1", "scan@example.com", "paper", true))
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("scan@example.com"))
            )

            val result = repository.fetchUsers()

            assertTrue(result is AdminUsersResult.Success)
            val user = (result as AdminUsersResult.Success).data.active[0]
            assertTrue("scanner_running=true must be mapped to AdminUser.scannerRunning=true", user.scannerRunning)
        }

    // =========================================================================
    // approve = GET allowlist → add email → PUT full list
    // =========================================================================

    @Test
    fun `approve gets allowlist adds email and puts full list`() =
        runTest(testDispatcher) {
            // approve(email) does: GET allowlist, then PUT with email added
            // Then re-fetches (GET users + GET allowlist for state refresh)
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("existing@example.com")) // GET allowlist for read
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("existing@example.com", "new@example.com")) // PUT response
            )
            // Re-fetch responses after approve
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(usersResponseJson(
                        Triple("uid-e", "existing@example.com", "paper"),
                        Triple("uid-n", "new@example.com", "paper"),
                    ))
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("existing@example.com", "new@example.com"))
            )

            val result = repository.approve("new@example.com")

            assertTrue(
                "approve() with 200 responses must return ActionResult.Success, got $result",
                result is ActionResult.Success
            )

            // Verify PUT body contains both existing and new email
            val requests = (0 until mockWebServer.requestCount).map { mockWebServer.takeRequest() }
            val putRequest = requests.firstOrNull { it.method == "PUT" }
            val putBody = putRequest?.body?.readUtf8() ?: ""

            assertTrue(
                "PUT body must contain new email 'new@example.com', got: $putBody",
                putBody.contains("new@example.com")
            )
            assertTrue(
                "PUT body must contain existing email 'existing@example.com' (full list replace), got: $putBody",
                putBody.contains("existing@example.com")
            )
        }

    // =========================================================================
    // reject = GET allowlist → remove email → PUT full list
    // =========================================================================

    @Test
    fun `reject gets allowlist removes email and puts reduced list`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("keep@example.com", "remove@example.com")) // GET allowlist
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("keep@example.com")) // PUT response
            )
            // Re-fetch
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(usersResponseJson(Triple("uid-k", "keep@example.com", "paper")))
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(allowlistResponseJson("keep@example.com"))
            )

            val result = repository.reject("remove@example.com")

            assertTrue(
                "reject() with 200 responses must return ActionResult.Success, got $result",
                result is ActionResult.Success
            )

            val requests = (0 until mockWebServer.requestCount).map { mockWebServer.takeRequest() }
            val putRequest = requests.firstOrNull { it.method == "PUT" }
            val putBody = putRequest?.body?.readUtf8() ?: ""

            assertFalse(
                "PUT body must NOT contain rejected email 'remove@example.com', got: $putBody",
                putBody.contains("remove@example.com")
            )
            assertTrue(
                "PUT body must still contain non-rejected email 'keep@example.com', got: $putBody",
                putBody.contains("keep@example.com")
            )
        }

    // =========================================================================
    // setMode — POST api/admin/users/{uid}/mode
    // =========================================================================

    @Test
    fun `setMode posts correct mode to correct path`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(statusSavedJson())
            )

            val result = repository.setMode(uid = "uid-abc", mode = ExecutionMode.LIVE)

            assertTrue(
                "setMode with 200 response must return ActionResult.Success, got $result",
                result is ActionResult.Success
            )

            val request = mockWebServer.takeRequest()
            assertTrue(
                "POST path must contain 'uid-abc' and 'mode', got: ${request.path}",
                request.path?.contains("uid-abc") == true && request.path?.contains("mode") == true
            )
            val body = request.body.readUtf8()
            assertTrue(
                "Request body must contain mode value 'live', got: $body",
                body.contains("live")
            )
        }

    // =========================================================================
    // stop — POST api/admin/users/{uid}/stop
    // =========================================================================

    @Test
    fun `stop posts to correct path`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(statusStoppedJson())
            )

            val result = repository.stop(uid = "uid-xyz")

            assertTrue(
                "stop() with 200 response must return ActionResult.Success, got $result",
                result is ActionResult.Success
            )

            val request = mockWebServer.takeRequest()
            assertTrue(
                "POST path must contain 'uid-xyz' and 'stop', got: ${request.path}",
                request.path?.contains("uid-xyz") == true && request.path?.contains("stop") == true
            )
        }

    // =========================================================================
    // 403 → "Admin access required"
    // =========================================================================

    @Test
    fun `fetchUsers 403 returns error with admin access required message`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(403)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"detail":"Admin only"}""")
            )

            val result = repository.fetchUsers()

            assertTrue(
                "HTTP 403 from fetchUsers must return AdminUsersResult.Error, got $result",
                result is AdminUsersResult.Error
            )
            val error = result as AdminUsersResult.Error
            assertTrue(
                "403 error message must contain 'Admin access required' (NOT raw 'Admin only'), got '${error.message}'",
                error.message.contains("Admin access required", ignoreCase = true)
            )
            assertFalse(
                "403 error message must NOT expose raw HTTP body text 'Admin only', got '${error.message}'",
                error.message == "Admin only"
            )
        }

    // =========================================================================
    // 422 — bad mode
    // =========================================================================

    @Test
    fun `setMode 422 returns ActionResult Error`() =
        runTest(testDispatcher) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(422)
                    .setBody("""{"detail":"invalid mode"}""")
            )

            val result = repository.setMode(uid = "uid-abc", mode = ExecutionMode.LIVE)

            assertTrue(
                "HTTP 422 from setMode must return ActionResult.Error, got $result",
                result is ActionResult.Error
            )
        }

    // =========================================================================
    // errorBody closed on non-2xx (verified indirectly via sequential calls)
    // =========================================================================

    @Test
    fun `errorBody is closed after non-2xx so connection pool is not exhausted`() =
        runTest(testDispatcher) {
            repeat(2) {
                mockWebServer.enqueue(
                    MockResponse().setResponseCode(403)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"detail":"Admin only"}""")
                )
            }

            val first = repository.fetchUsers()
            val second = repository.fetchUsers()

            assertTrue(
                "First 403 must map to AdminUsersResult.Error (errorBody must be closed so pool is free)",
                first is AdminUsersResult.Error
            )
            assertTrue(
                "Second 403 must also complete without hanging — proves errorBody was closed after first",
                second is AdminUsersResult.Error
            )
        }

    // =========================================================================
    // Network exception → never throws
    // =========================================================================

    @Test
    fun `fetchUsers network exception returns error and never throws`() =
        runTest(testDispatcher) {
            mockWebServer.shutdown()

            val result = repository.fetchUsers()

            assertTrue(
                "Network IOException must map to AdminUsersResult.Error — repository must never throw to callers",
                result is AdminUsersResult.Error
            )
        }
}
