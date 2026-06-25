package com.gshashank.btcagent.ui.markets

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.LiveRegime
import com.gshashank.btcagent.data.model.Regime
import com.gshashank.btcagent.data.model.RegimeData
import com.gshashank.btcagent.data.model.RegimeDay
import com.gshashank.btcagent.data.repository.RegimeRepository
import com.gshashank.btcagent.data.repository.RegimeResult
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.markets.regime.BtcRegimeViewModel
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * JVM unit tests for [BtcRegimeViewModel] — MOBILE-12.
 *
 * Uses [FakeRegimeRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * No catalog flag is required for this feature (PLAN.md: "Catalog flag: NONE").
 *
 * All tests MUST fail (red) until [BtcRegimeViewModel] is implemented.
 *
 * Test coverage (mirrors MorningBriefingViewModelTest):
 *   1. Initial state is [UiState.Loading].
 *   2. Success with non-empty data → [UiState.Ready] with correct [RegimeData].
 *   3. Success with isEmpty data → [UiState.Empty].
 *   4. Error response → [UiState.Error].
 *   5. retry() from Error → Loading → Ready.
 *   6. [NetworkMonitor] offline → [UiState.Offline].
 *   7. Offline → back online → triggers refetch → [UiState.Ready].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BtcRegimeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeRegimeRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    /** A stable live regime instance representing a Bull market in progress. */
    private val sampleLiveRegime = LiveRegime(
        regime = Regime.BULL,
        conviction = 0.72,
        hasError = false,
    )

    /** A minimal list of [RegimeDay] entries for the chart. */
    private val sampleDays = listOf(
        RegimeDay(date = "2026-06-20", regime = Regime.BEAR, correct = false),
        RegimeDay(date = "2026-06-21", regime = Regime.SIDEWAYS, correct = null),
        RegimeDay(date = "2026-06-22", regime = Regime.BULL, correct = true),
        RegimeDay(date = "2026-06-23", regime = Regime.BULL, correct = true),
        RegimeDay(date = "2026-06-24", regime = Regime.BULL, correct = null),
    )

    /**
     * A non-empty [RegimeData] with a live regime and populated days list.
     * [RegimeData.isEmpty] is false because both live and days are populated.
     */
    private val sampleRegimeData = RegimeData(
        live = sampleLiveRegime,
        accuracyPct = 0.785, // fraction 0.0–1.0 (UI multiplies by 100)
        gradedCount = 14,
        days = sampleDays,
    )

    /**
     * An "empty" [RegimeData] — null live and empty days list.
     * [RegimeData.isEmpty] must be true.
     */
    private val emptyRegimeData = RegimeData(
        live = null,
        accuracyPct = null,
        gradedCount = 0,
        days = emptyList(),
    )

    @Before
    fun setUp() {
        fakeRepo = FakeRegimeRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): BtcRegimeViewModel =
        BtcRegimeViewModel(
            repository = fakeRepo,
            networkMonitor = mockNetworkMonitor,
        )

    // =========================================================================
    // 1. Initial state is Loading
    // =========================================================================

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = createViewModel()

        assertEquals(
            "uiState must be Loading immediately after construction before any coroutine runs",
            UiState.Loading,
            viewModel.uiState.value,
        )
    }

    // =========================================================================
    // 2. Success with non-empty data → Ready with correct RegimeData
    // =========================================================================

    @Test
    fun `fetch success with non-empty data emits Ready with correct RegimeData`() = runTest {
        fakeRepo.fetchRegimeResult = RegimeResult.Success(sampleRegimeData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready<RegimeData>).data

            assertEquals(
                "live regime must match the repository value",
                sampleRegimeData.live,
                data.live,
            )
            assertEquals(
                "accuracyPct must match the repository value",
                sampleRegimeData.accuracyPct!!,
                data.accuracyPct!!,
                0.001,
            )
            assertEquals(
                "gradedCount must match the repository value",
                sampleRegimeData.gradedCount,
                data.gradedCount,
            )
            assertEquals(
                "days list size must match the repository value",
                sampleRegimeData.days.size,
                data.days.size,
            )
            assertEquals(
                "first day date must match",
                sampleDays.first().date,
                data.days.first().date,
            )
            assertEquals(
                "first day regime must match",
                sampleDays.first().regime,
                data.days.first().regime,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Success with isEmpty data → UiState.Empty
    // =========================================================================

    @Test
    fun `fetch success with isEmpty data emits UiState Empty`() = runTest {
        fakeRepo.fetchRegimeResult = RegimeResult.Success(emptyRegimeData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "When RegimeData.isEmpty is true the state must be UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. Error response → UiState.Error
    // =========================================================================

    @Test
    fun `fetch failure emits UiState Error`() = runTest {
        fakeRepo.fetchRegimeResult = RegimeResult.Error(message = "network unavailable")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(
                "Fetch failure must produce UiState.Error, got $error",
                error is UiState.Error,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. retry() from Error → Loading → Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.fetchRegimeResult = RegimeResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.fetchRegimeResult = RegimeResult.Success(sampleRegimeData)

            viewModel.retry()

            assertEquals(
                "retry() must re-emit Loading before the new result",
                UiState.Loading,
                awaitItem(),
            )
            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue(
                "retry() must emit Ready after Loading when fetch succeeds, got $ready",
                ready is UiState.Ready,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 6. NetworkMonitor offline → UiState.Offline
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits UiState Offline`() = runTest {
        fakeRepo.fetchRegimeResult = RegimeResult.Success(sampleRegimeData)
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())
            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Must be Ready before going offline, got $ready", ready is UiState.Ready)

            networkOnlineFlow.value = false
            advanceUntilIdle()

            val offline = awaitItem()
            assertTrue(
                "uiState must become Offline when NetworkMonitor emits false, got $offline",
                offline is UiState.Offline,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 7. Offline → back online → triggers refetch → Ready
    // =========================================================================

    @Test
    fun `coming back online from Offline triggers refetch and emits Ready`() = runTest {
        fakeRepo.fetchRegimeResult = RegimeResult.Success(sampleRegimeData)
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue("Must be Ready after first fetch", awaitItem() is UiState.Ready)

            // Go offline.
            networkOnlineFlow.value = false
            advanceUntilIdle()
            assertTrue("Must be Offline when network lost", awaitItem() is UiState.Offline)

            // Come back online.
            networkOnlineFlow.value = true
            advanceUntilIdle()

            // Expect Loading then Ready again.
            val loadingAfterReconnect = awaitItem()
            assertTrue(
                "Must emit Loading when coming back online, got $loadingAfterReconnect",
                loadingAfterReconnect is UiState.Loading,
            )
            advanceUntilIdle()

            val readyAfterReconnect = awaitItem()
            assertTrue(
                "Must emit Ready after refetch on reconnect, got $readyAfterReconnect",
                readyAfterReconnect is UiState.Ready,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // Additional: Empty success stamps lastSuccessMs so offline banner is not epoch-zero
    // =========================================================================

    @Test
    fun `empty success stamps lastSuccessMs so offline banner is not epoch zero`() = runTest {
        fakeRepo.fetchRegimeResult = RegimeResult.Success(emptyRegimeData)
        networkOnlineFlow.value = true

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue("Empty success expected", awaitItem() is UiState.Empty)

            networkOnlineFlow.value = false
            advanceUntilIdle()

            val offline = awaitItem()
            assertTrue("Must transition to Offline, got $offline", offline is UiState.Offline)
            assertTrue(
                "lastUpdatedMs must be > 0 after an empty success (not epoch 0), got " +
                    "${(offline as UiState.Offline).lastUpdatedMs}",
                offline.lastUpdatedMs > 0L,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Configurable fake [RegimeRepository].
 * Set [fetchRegimeResult] before each test to control what [fetchRegime] returns.
 */
private class FakeRegimeRepository : RegimeRepository {

    var fetchRegimeResult: RegimeResult =
        RegimeResult.Error(message = "fetchRegimeResult not configured")

    override suspend fun fetchRegime(): RegimeResult = fetchRegimeResult
}
