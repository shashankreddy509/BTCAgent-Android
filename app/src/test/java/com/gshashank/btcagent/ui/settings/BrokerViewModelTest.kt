package com.gshashank.btcagent.ui.settings

import app.cash.turbine.test
import com.gshashank.btcagent.data.model.BrokerInfo
import com.gshashank.btcagent.data.repository.BrokerActionResult
import com.gshashank.btcagent.data.repository.BrokerRepository
import com.gshashank.btcagent.data.repository.BrokerResult
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [BrokerViewModel] — MOBILE-45.
 *
 * Uses [FakeBrokerRepository] (hand-written fake) so no real network or Android framework
 * calls are made. [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * as [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * **No catalog flag** — the Broker API section is ungated (user decision for MOBILE-45).
 * No catalog-gating test is needed.
 *
 * All tests MUST fail (red) until [BrokerViewModel] is implemented.
 *
 * [BrokerViewModel] exposes:
 *   - [BrokerViewModel.brokerState] : StateFlow<BrokerState>
 *       BrokerState is a sealed class: Loading | Ready(brokerInfo: BrokerInfo?) | Error(message: String)
 *   - [BrokerViewModel.actionResult] : StateFlow<BrokerActionResult?>
 *   - [BrokerViewModel.replaceBroker] : fun (broker, apiKey, apiSecret)
 *   - [BrokerViewModel.clearActionResult] : fun ()
 *
 * Test coverage:
 *   1.  init_loadsAndShowsBrokerInfo        — after init, brokerState is Ready with the BrokerInfo
 *   2.  replaceBroker_success              — PUT success → re-fetch + actionResult is Success
 *   3.  replaceBroker_doubleTap            — two rapid calls → only one PUT sent to repo
 *   4.  connectedNull_statePreservesNull   — BrokerInfo.connected=null → Ready.brokerInfo.connected == null
 *   5.  replaceBroker_failure             — PUT error → actionResult is BrokerActionResult.Error
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrokerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeBrokerRepository

    // -------------------------------------------------------------------------
    // Domain fixtures
    // -------------------------------------------------------------------------

    private val sampleBrokerInfo = BrokerInfo(
        broker = "coinbase",
        accountName = "My Coinbase Account",
        apiKeyMasked = "····3f9a",
        connected = true,
    )

    private val brokerInfoNullConnected = BrokerInfo(
        broker = "pepperstone",
        accountName = "Pepperstone Account",
        apiKeyMasked = "····ab12",
        connected = null, // inactive broker — not probed
    )

    @Before
    fun setUp() {
        fakeRepo = FakeBrokerRepository()
    }

    private fun createViewModel(): BrokerViewModel = BrokerViewModel(repository = fakeRepo)

    // =========================================================================
    // 1. init_loadsAndShowsBrokerInfo
    //    After construction + idle, brokerState must be BrokerState.Ready containing
    //    the BrokerInfo returned by the repository.
    // =========================================================================

    @Test
    fun `init loadsAndShowsBrokerInfo after construction brokerState is Ready with BrokerInfo`() =
        runTest {
            fakeRepo.fetchResult = BrokerResult.Success(sampleBrokerInfo)

            val viewModel = createViewModel()

            viewModel.brokerState.test {
                // Initial emission must be Loading
                val loading = awaitItem()
                assertTrue(
                    "brokerState must initially be BrokerState.Loading before the fetch completes, got $loading",
                    loading is BrokerState.Loading,
                )

                advanceUntilIdle()

                val ready = awaitItem()
                assertTrue(
                    "brokerState must transition to BrokerState.Ready after a successful fetch, got $ready",
                    ready is BrokerState.Ready,
                )
                val brokerInfo = (ready as BrokerState.Ready).brokerInfo
                assertEquals(
                    "BrokerState.Ready.brokerInfo.broker must match repository data",
                    "coinbase",
                    brokerInfo?.broker,
                )
                assertEquals(
                    "BrokerState.Ready.brokerInfo.accountName must match repository data",
                    "My Coinbase Account",
                    brokerInfo?.accountName,
                )
                assertEquals(
                    "BrokerState.Ready.brokerInfo.apiKeyMasked must match repository data",
                    "····3f9a",
                    brokerInfo?.apiKeyMasked,
                )
                assertEquals(
                    "BrokerState.Ready.brokerInfo.connected must be true as returned by the fake",
                    true,
                    brokerInfo?.connected,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 2. replaceBroker_success_refetchesAndSetsSuccessResult
    //    After replaceBroker succeeds:
    //      - actionResult transitions to BrokerActionResult.Success
    //      - fetchBroker is called again (refetch triggers refresh)
    // =========================================================================

    @Test
    fun `replaceBroker success refetchesAndSetsSuccessResult`() = runTest {
        fakeRepo.fetchResult = BrokerResult.Success(sampleBrokerInfo)
        fakeRepo.replaceResult = BrokerActionResult.Success

        val viewModel = createViewModel()
        advanceUntilIdle()

        val fetchCountAfterInit = fakeRepo.fetchCallCount

        viewModel.actionResult.test {
            awaitItem() // consume initial null

            viewModel.replaceBroker(
                broker = "coinbase",
                apiKey = "new-api-key",
                apiSecret = "new-api-secret",
            )
            advanceUntilIdle()

            val result = awaitItem()
            assertTrue(
                "actionResult must be BrokerActionResult.Success after replaceBroker succeeds, got $result",
                result is BrokerActionResult.Success,
            )

            // Re-fetch contract: the VM must call fetchBroker again after a successful PUT so
            // the UI reflects the updated broker state.
            assertTrue(
                "fetchBroker must be called again after replaceBroker succeeds — " +
                    "fetchCallCount must be > $fetchCountAfterInit, got ${fakeRepo.fetchCallCount}",
                fakeRepo.fetchCallCount > fetchCountAfterInit,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. replaceBroker_doubleTap_noDoubleRequest
    //    Two rapid calls to replaceBroker must result in exactly one PUT to the
    //    repository. The second call is ignored while the first is in-flight.
    // =========================================================================

    @Test
    fun `replaceBroker doubleTap noDoubleRequest second call ignored while first is in-flight`() =
        runTest {
            fakeRepo.fetchResult = BrokerResult.Success(sampleBrokerInfo)
            fakeRepo.replaceResult = BrokerActionResult.Success

            val viewModel = createViewModel()
            advanceUntilIdle()

            val replaceCallsBefore = fakeRepo.replaceCallCount

            // Fire two calls back-to-back without yielding between them
            viewModel.replaceBroker(broker = "coinbase", apiKey = "key1", apiSecret = "secret1")
            viewModel.replaceBroker(broker = "coinbase", apiKey = "key2", apiSecret = "secret2")
            advanceUntilIdle()

            assertEquals(
                "Double-tap guard: replaceBroker must only be forwarded to the repository once — " +
                    "second call while first is in-flight must be silently dropped",
                replaceCallsBefore + 1,
                fakeRepo.replaceCallCount,
            )
        }

    // =========================================================================
    // 4. connectedNull_statePreservesNull
    //    When the repository returns BrokerInfo with connected=null, the VM must
    //    propagate null intact into BrokerState.Ready.brokerInfo.connected.
    //    null means "not probed" (neutral "—" UI) and must NOT be coerced to false
    //    ("Disconnected" UI). This is the rollback-safety contract for MOBILE-45.
    // =========================================================================

    @Test
    fun `connectedNull statePreservesNull BrokerInfo with connected null stays null in Ready state`() =
        runTest {
            fakeRepo.fetchResult = BrokerResult.Success(brokerInfoNullConnected)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.brokerState.value
            assertTrue(
                "brokerState must be BrokerState.Ready after a successful fetch, got $state",
                state is BrokerState.Ready,
            )
            val brokerInfo = (state as BrokerState.Ready).brokerInfo
            assertNull(
                "BrokerState.Ready.brokerInfo.connected must be null — null = not probed (neutral '—' UI), " +
                    "NOT false (Disconnected). The ViewModel must not coerce null to false.",
                brokerInfo?.connected,
            )
        }

    // =========================================================================
    // 5. replaceBroker_failure_setsErrorResult
    //    When replaceBroker fails, actionResult must be BrokerActionResult.Error
    //    with the error message from the repository.
    // =========================================================================

    @Test
    fun `replaceBroker failure setsErrorResult actionResult is BrokerActionResult Error`() =
        runTest {
            fakeRepo.fetchResult = BrokerResult.Success(sampleBrokerInfo)
            fakeRepo.replaceResult = BrokerActionResult.Error(message = "Invalid API credentials")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.actionResult.test {
                awaitItem() // consume initial null

                viewModel.replaceBroker(
                    broker = "coinbase",
                    apiKey = "bad-key",
                    apiSecret = "bad-secret",
                )
                advanceUntilIdle()

                val result = awaitItem()
                assertTrue(
                    "actionResult must be BrokerActionResult.Error when replaceBroker fails, got $result",
                    result is BrokerActionResult.Error,
                )
                val error = result as BrokerActionResult.Error
                assertTrue(
                    "BrokerActionResult.Error.message must contain the error reason from the repo, " +
                        "got '${error.message}'",
                    error.message.contains("Invalid API credentials", ignoreCase = true),
                )

                cancelAndIgnoreRemainingEvents()
            }
        }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [BrokerRepository].
 *
 * Configure [fetchResult] and [replaceResult] before each test.
 * [fetchCallCount] and [replaceCallCount] allow tests to assert invocation counts without
 * relying on a mocking framework.
 */
private class FakeBrokerRepository : BrokerRepository {

    var fetchResult: BrokerResult =
        BrokerResult.Error(message = "fetchResult not configured in test")
    var replaceResult: BrokerActionResult =
        BrokerActionResult.Error(message = "replaceResult not configured in test")

    var fetchCallCount: Int = 0
    var replaceCallCount: Int = 0

    override suspend fun fetchBroker(): BrokerResult {
        fetchCallCount++
        return fetchResult
    }

    override suspend fun replaceBroker(
        broker: String,
        apiKey: String,
        apiSecret: String,
    ): BrokerActionResult {
        replaceCallCount++
        return replaceResult
    }
}
