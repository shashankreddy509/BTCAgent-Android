package com.gshashank.btcagent.ui.markets.volume_profile

import app.cash.turbine.test
import com.gshashank.btcagent.core.NetworkMonitor
import com.gshashank.btcagent.data.model.Session
import com.gshashank.btcagent.data.model.Timeframe
import com.gshashank.btcagent.data.model.VolumeProfileData
import com.gshashank.btcagent.data.repository.VolumeProfileRepository
import com.gshashank.btcagent.data.repository.VolumeProfileResult
import com.gshashank.btcagent.ui.components.state.UiState
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
 * JVM unit tests for [VolumeProfileViewModel] — MOBILE-14.
 *
 * Uses [FakeVolumeProfileRepository] (hand-written fake) and a mockito-kotlin mock for
 * [NetworkMonitor] so no real network or Android framework calls are made.
 *
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * All tests MUST fail (red) until [VolumeProfileViewModel] is implemented in:
 *   `app/src/main/java/com/gshashank/btcagent/ui/markets/volume_profile/VolumeProfileViewModel.kt`
 *
 * Test coverage:
 *  1. Initial [uiState] is [UiState.Loading].
 *  2. Successful fetch with non-empty data → [UiState.Ready].
 *  3. Successful fetch with all-empty data → [UiState.Empty].
 *  4. Fetch error → [UiState.Error] with code "ERR_FETCH".
 *  5. Default [selectedTimeframe] is [Timeframe.H4].
 *  6. [onSelectTimeframe] updates [selectedTimeframe].
 *  7. [NetworkMonitor] offline → [UiState.Offline].
 *  8. Back online from Offline → triggers refetch → [UiState.Ready].
 *  9. retry() from Error → Loading then Ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeVolumeProfileRepository

    private val networkOnlineFlow = MutableStateFlow(true)
    private val mockNetworkMonitor: NetworkMonitor = mock<NetworkMonitor>().also { nm ->
        whenever(nm.isOnlineFlow).thenReturn(networkOnlineFlow as StateFlow<Boolean>)
    }

    // -------------------------------------------------------------------------
    // Sample data
    // -------------------------------------------------------------------------

    private val sampleSession = Session(
        start = "2026-06-25T00:00:00+00:00",
        poc = 50000.0,
        vah = 51000.0,
        vaLow = 49000.0,
        lo = 48000.0,
        hi = 52000.0,
    )

    private val nonEmptyData = VolumeProfileData(
        timeframes = mapOf(
            Timeframe.H4 to listOf(sampleSession),
            Timeframe.H12 to emptyList(),
            Timeframe.D1 to emptyList(),
        ),
        version = 1,
    )

    private val emptyData = VolumeProfileData(
        timeframes = mapOf(
            Timeframe.H4 to emptyList(),
            Timeframe.H12 to emptyList(),
            Timeframe.D1 to emptyList(),
        ),
        version = 0,
    )

    @Before
    fun setUp() {
        fakeRepo = FakeVolumeProfileRepository()
        networkOnlineFlow.value = true
    }

    private fun createViewModel(): VolumeProfileViewModel =
        VolumeProfileViewModel(
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
    // 2. Successful fetch with non-empty data → UiState.Ready
    // =========================================================================

    @Test
    fun `fetch success with non-empty data emits Ready with correct VolumeProfileData`() = runTest {
        fakeRepo.result = VolumeProfileResult.Success(nonEmptyData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val ready = awaitItem()
            assertTrue("Second emission must be Ready, got $ready", ready is UiState.Ready)
            val data = (ready as UiState.Ready<VolumeProfileData>).data

            assertTrue(
                "H4 sessions must not be empty in the Ready state",
                data.timeframes[Timeframe.H4]?.isNotEmpty() == true,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 3. Successful fetch with all-empty data → UiState.Empty
    // =========================================================================

    @Test
    fun `fetch success with all empty data emits UiState Empty`() = runTest {
        fakeRepo.result = VolumeProfileResult.Success(emptyData)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(
                "When VolumeProfileData.isEmpty is true the state must be UiState.Empty, got $state",
                state is UiState.Empty,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 4. Fetch error → UiState.Error with code "ERR_FETCH"
    // =========================================================================

    @Test
    fun `fetch error emits UiState Error with code ERR_FETCH`() = runTest {
        fakeRepo.result = VolumeProfileResult.Error(message = "network unavailable")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals("First emission must be Loading", UiState.Loading, awaitItem())

            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(
                "Repo Error must produce UiState.Error, got $error",
                error is UiState.Error,
            )
            val uiError = error as UiState.Error
            assertEquals(
                "Error code must be ERR_FETCH for repo Error result",
                "ERR_FETCH",
                uiError.code,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // 5. Default selectedTimeframe is H4
    // =========================================================================

    @Test
    fun `default selectedTimeframe is H4`() = runTest {
        val viewModel = createViewModel()

        assertEquals(
            "selectedTimeframe must default to Timeframe.H4",
            Timeframe.H4,
            viewModel.selectedTimeframe.value,
        )
    }

    // =========================================================================
    // 6. onSelectTimeframe updates selectedTimeframe
    // =========================================================================

    @Test
    fun `onSelectTimeframe updates selectedTimeframe to the new value`() = runTest {
        fakeRepo.result = VolumeProfileResult.Success(nonEmptyData)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSelectTimeframe(Timeframe.H12)

        assertEquals(
            "selectedTimeframe must be H12 after calling onSelectTimeframe(Timeframe.H12)",
            Timeframe.H12,
            viewModel.selectedTimeframe.value,
        )
    }

    // =========================================================================
    // 7. NetworkMonitor offline → UiState.Offline
    // =========================================================================

    @Test
    fun `NetworkMonitor offline emits UiState Offline`() = runTest {
        fakeRepo.result = VolumeProfileResult.Success(nonEmptyData)
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
    // 8. Coming back online from Offline → triggers refetch → Ready
    // =========================================================================

    @Test
    fun `coming back online from Offline triggers refetch and emits Ready`() = runTest {
        fakeRepo.result = VolumeProfileResult.Success(nonEmptyData)
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
    // 9. retry() from Error → Loading then Ready
    // =========================================================================

    @Test
    fun `retry from Error resets to Loading then emits Ready`() = runTest {
        fakeRepo.result = VolumeProfileResult.Error(message = "transient error")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue("Must be Error before retry, got $error", error is UiState.Error)

            fakeRepo.result = VolumeProfileResult.Success(nonEmptyData)

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
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [VolumeProfileRepository].
 * Set [result] before each test to control what [fetch] returns.
 */
private class FakeVolumeProfileRepository : VolumeProfileRepository {

    var result: VolumeProfileResult =
        VolumeProfileResult.Error(message = "result not configured")

    override suspend fun fetch(): VolumeProfileResult = result
}
