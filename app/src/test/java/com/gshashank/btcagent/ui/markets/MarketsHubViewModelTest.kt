package com.gshashank.btcagent.ui.markets

import app.cash.turbine.test
import com.gshashank.btcagent.data.repository.CatalogFlags
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM unit tests for [MarketsHubViewModel] — MOBILE-13 (catalog gating) + MOBILE-10 (hub)
 * + MOBILE-15 (Liquidity Map catalog gating) + MOBILE-17 (Analytics catalog gating).
 *
 * This suite extends the original MOBILE-10 stub tests to add MOBILE-13 catalog gating:
 * the Markov Matrix tile/screen must only be accessible when [CatalogFlags.MARKOV_MATRIX]
 * (id = 100003) is ON.
 *
 * MOBILE-15 adds [isLiquidityMapEnabled] gating via [CatalogFlags.LIQUIDITY_MAP] (id = 100005).
 *
 * MOBILE-17 adds [isAnalyticsEnabled] gating via [CatalogFlags.ANALYTICS] (id = 100006).
 *
 * Uses [FakeCatalogRepository] (hand-written fake) so no DataStore or Firestore is touched.
 * [MainDispatcherRule] installs [kotlinx.coroutines.test.UnconfinedTestDispatcher] as
 * [kotlinx.coroutines.Dispatchers.Main] so [viewModelScope]-backed coroutines are driven
 * synchronously.
 *
 * **Catalog rollback contract (MOBILE-13)**:
 *   - [CatalogFlags.MARKOV_MATRIX] OFF/absent → [MarketsHubViewModel.isMarkovEnabled] emits `false`
 *     (tile hidden — old/absent behavior).
 *   - [CatalogFlags.MARKOV_MATRIX] ON → [MarketsHubViewModel.isMarkovEnabled] emits `true`
 *     (tile shown — new behavior).
 *
 * **Catalog rollback contract (MOBILE-15)**:
 *   - [CatalogFlags.LIQUIDITY_MAP] OFF/absent → [MarketsHubViewModel.isLiquidityMapEnabled] emits `false`
 *     (tile hidden — old/absent behavior).
 *   - [CatalogFlags.LIQUIDITY_MAP] ON → [MarketsHubViewModel.isLiquidityMapEnabled] emits `true`
 *     (tile shown — new behavior).
 *
 * **Catalog rollback contract (MOBILE-17)**:
 *   - [CatalogFlags.ANALYTICS] OFF/absent → [MarketsHubViewModel.isAnalyticsEnabled] emits `false`
 *     (tile hidden — old/absent behavior).
 *   - [CatalogFlags.ANALYTICS] ON → [MarketsHubViewModel.isAnalyticsEnabled] emits `true`
 *     (tile shown — new behavior).
 *
 * All tests that reference [MarketsHubViewModel.isMarkovEnabled], [isLiquidityMapEnabled], or
 * [isAnalyticsEnabled] MUST fail (red) until [MarketsHubViewModel] is updated to accept
 * [CatalogRepository] and expose those flows. The original 4 MOBILE-10 tests must also fail
 * until the ViewModel re-adopts [CatalogRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketsHubViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeCatalog: FakeCatalogRepository

    @Before
    fun setUp() {
        fakeCatalog = FakeCatalogRepository()
    }

    private fun createViewModel(): MarketsHubViewModel =
        MarketsHubViewModel(catalogRepository = fakeCatalog)

    // =========================================================================
    // MOBILE-10 baseline: ViewModel constructs without error
    // =========================================================================

    @Test
    fun `MarketsHubViewModel constructs without error`() {
        val viewModel = createViewModel()

        assertNotNull(
            "MarketsHubViewModel instance must be non-null after construction",
            viewModel,
        )
    }

    // =========================================================================
    // MOBILE-10 baseline: uiState is non-null immediately after construction
    // =========================================================================

    @Test
    fun `uiState is non-null immediately after construction`() {
        val viewModel = createViewModel()

        assertNotNull(
            "uiState StateFlow must be non-null immediately after construction",
            viewModel.uiState,
        )
    }

    // =========================================================================
    // MOBILE-10 baseline: uiState.value equals Stub at construction time
    // =========================================================================

    @Test
    fun `uiState value equals Stub at construction time`() {
        val viewModel = createViewModel()

        val initialState = viewModel.uiState.value

        assertNotNull(
            "uiState.value must not be null immediately after construction",
            initialState,
        )
        assert(initialState is MarketsHubUiState.Stub) {
            "Expected initial uiState to be MarketsHubUiState.Stub but was $initialState"
        }
    }

    // =========================================================================
    // MOBILE-10 baseline: uiState remains Stub after coroutines are drained
    // =========================================================================

    @Test
    fun `uiState remains Stub after coroutines are drained`() = runTest {
        val viewModel = createViewModel()

        advanceUntilIdle()

        val stateAfterDrain = viewModel.uiState.value

        assertNotNull(
            "uiState.value must still be non-null after advanceUntilIdle()",
            stateAfterDrain,
        )
        assert(stateAfterDrain is MarketsHubUiState.Stub) {
            "uiState must remain MarketsHubUiState.Stub after all coroutines finish, got $stateAfterDrain"
        }
    }

    // =========================================================================
    // MOBILE-13 Catalog gating: isMarkovEnabled emits false when flag is OFF/absent
    //
    // Rollback contract: when MARKOV_MATRIX flag is OFF the Markov Matrix tile must be
    // hidden — this is the OLD/absent behavior (feature not shown).
    // =========================================================================

    @Test
    fun `isMarkovEnabled emits false when MARKOV_MATRIX catalog flag is OFF`() = runTest {
        // Flag OFF — isEnabledFlow returns false, which is the default absent behavior.
        fakeCatalog.markovMatrixEnabled = false

        val viewModel = createViewModel()

        viewModel.isMarkovEnabled.test {
            advanceUntilIdle()

            // Drain until we get at least one emission.
            val emission = awaitItem()
            assertFalse(
                "isMarkovEnabled must emit false when CatalogFlags.MARKOV_MATRIX (id=100003) is OFF — " +
                    "Markov Matrix tile must not be accessible in the hub",
                emission,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // MOBILE-13 Catalog gating: isMarkovEnabled emits true when flag is ON
    //
    // Rollback contract: when MARKOV_MATRIX flag is ON the Markov Matrix tile must be
    // visible — this is the NEW behavior (feature shown).
    // =========================================================================

    @Test
    fun `isMarkovEnabled emits true when MARKOV_MATRIX catalog flag is ON`() = runTest {
        // Flag ON — the new Markov Matrix screen is accessible.
        fakeCatalog.markovMatrixEnabled = true

        val viewModel = createViewModel()

        viewModel.isMarkovEnabled.test {
            advanceUntilIdle()

            val emission = awaitItem()
            assertTrue(
                "isMarkovEnabled must emit true when CatalogFlags.MARKOV_MATRIX (id=100003) is ON — " +
                    "Markov Matrix tile must be accessible in the hub",
                emission,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // MOBILE-13 Catalog gating: isMarkovEnabled is false when flag is absent (default)
    //
    // The CatalogFlags.MARKOV_MATRIX flag is NOT security-sensitive (default=false), so
    // a missing/failed fetch must fall back to the OFF path (tile hidden).
    // =========================================================================

    @Test
    fun `isMarkovEnabled emits false by default when MARKOV_MATRIX flag is absent from catalog`() =
        runTest {
            // Do NOT set markovMatrixEnabled — this is the "absent" state with default=false.
            fakeCatalog.markovMatrixEnabled = false // explicit OFF same as absent

            val viewModel = createViewModel()

            viewModel.isMarkovEnabled.test {
                advanceUntilIdle()

                val emission = awaitItem()
                assertFalse(
                    "isMarkovEnabled must default to false when MARKOV_MATRIX flag is absent — " +
                        "the tile must be hidden until the flag is explicitly enabled",
                    emission,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // MOBILE-13 Catalog gating: flag toggling — OFF then ON emits both values in order
    //
    // Tests that the StateFlow reacts to a live catalog update (e.g. startup fetch lands).
    // =========================================================================

    @Test
    fun `isMarkovEnabled reacts to catalog flag toggling from OFF to ON`() = runTest {
        fakeCatalog.markovMatrixEnabled = false

        // Use a flow that emits two values to simulate a late-landing catalog fetch.
        fakeCatalog.markovMatrixFlow = flowOf(false, true)

        val viewModel = createViewModel()

        viewModel.isMarkovEnabled.test {
            advanceUntilIdle()

            val first = awaitItem()
            assertFalse("First emission must be false (flag OFF at startup)", first)

            val second = awaitItem()
            assertTrue(
                "Second emission must be true after catalog fetch lands with flag ON",
                second,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // MOBILE-15 Catalog gating: isLiquidityMapEnabled defaults to false (flag OFF)
    //
    // Rollback contract: when LIQUIDITY_MAP flag is OFF/absent the Liquidity Map tile must
    // be hidden — this is the OLD/absent behavior (feature not shown).
    // =========================================================================

    @Test
    fun `isLiquidityMapEnabled emits false when LIQUIDITY_MAP catalog flag is OFF`() = runTest {
        // Flag OFF — the default absent behavior; tile must be hidden.
        fakeCatalog.liquidityMapEnabled = false

        val viewModel = createViewModel()

        viewModel.isLiquidityMapEnabled.test {
            advanceUntilIdle()

            val emission = awaitItem()
            assertFalse(
                "isLiquidityMapEnabled must emit false when CatalogFlags.LIQUIDITY_MAP (id=100005) is OFF — " +
                    "Liquidity Map tile must not be accessible in the hub",
                emission,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // MOBILE-15 Catalog gating: isLiquidityMapEnabled becomes true when flag is ON
    //
    // Rollback contract: when LIQUIDITY_MAP flag is ON the Liquidity Map tile must be
    // visible — this is the NEW behavior (feature shown).
    // =========================================================================

    @Test
    fun `isLiquidityMapEnabled emits true when LIQUIDITY_MAP catalog flag is ON`() = runTest {
        // Flag ON — the new Liquidity Map screen is accessible.
        fakeCatalog.liquidityMapEnabled = true

        val viewModel = createViewModel()

        viewModel.isLiquidityMapEnabled.test {
            advanceUntilIdle()

            val emission = awaitItem()
            assertTrue(
                "isLiquidityMapEnabled must emit true when CatalogFlags.LIQUIDITY_MAP (id=100005) is ON — " +
                    "Liquidity Map tile must be accessible in the hub",
                emission,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // MOBILE-17 Catalog gating: isAnalyticsEnabled defaults to false (flag OFF)
    //
    // Rollback contract: when ANALYTICS flag is OFF/absent the Analytics tile must be
    // hidden — this is the OLD/absent behavior (feature not shown).
    // =========================================================================

    @Test
    fun `analytics flag off hides tile - isAnalyticsEnabled emits false when ANALYTICS flag is OFF`() =
        runTest {
            // Flag OFF — the default absent behavior; tile must be hidden.
            fakeCatalog.analyticsEnabled = false

            val viewModel = createViewModel()

            viewModel.isAnalyticsEnabled.test {
                advanceUntilIdle()

                val emission = awaitItem()
                assertFalse(
                    "isAnalyticsEnabled must emit false when CatalogFlags.ANALYTICS (id=100006) is OFF — " +
                        "Analytics tile must not be accessible in the hub (old/absent behavior)",
                    emission,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // MOBILE-17 Catalog gating: isAnalyticsEnabled becomes true when flag is ON
    //
    // Rollback contract: when ANALYTICS flag is ON the Analytics tile must be
    // visible — this is the NEW behavior (feature shown).
    // =========================================================================

    @Test
    fun `analytics flag on shows tile - isAnalyticsEnabled emits true when ANALYTICS flag is ON`() =
        runTest {
            // Flag ON — the new Analytics screen is accessible.
            fakeCatalog.analyticsEnabled = true

            val viewModel = createViewModel()

            viewModel.isAnalyticsEnabled.test {
                advanceUntilIdle()

                val emission = awaitItem()
                assertTrue(
                    "isAnalyticsEnabled must emit true when CatalogFlags.ANALYTICS (id=100006) is ON — " +
                        "Analytics tile must be accessible in the hub (new behavior)",
                    emission,
                )

                cancelAndIgnoreRemainingEvents()
            }
        }
}

// =============================================================================
// Fake collaborators
// =============================================================================

/**
 * Hand-written fake [CatalogRepository].
 *
 * [markovMatrixEnabled] controls the sync [isEnabled] response for [CatalogFlags.MARKOV_MATRIX].
 * [markovMatrixFlow] controls the [isEnabledFlow] response; defaults to [flowOf(markovMatrixEnabled)].
 * [liquidityMapEnabled] controls the sync [isEnabled] response for [CatalogFlags.LIQUIDITY_MAP].
 * [liquidityMapFlow] controls the [isEnabledFlow] response for LIQUIDITY_MAP; defaults to
 * [flowOf(liquidityMapEnabled)].
 * [analyticsEnabled] controls the sync [isEnabled] response for [CatalogFlags.ANALYTICS].
 * [analyticsFlow] controls the [isEnabledFlow] response for ANALYTICS; defaults to
 * [flowOf(analyticsEnabled)].
 * All other flags default to `false`.
 */
private class FakeCatalogRepository : CatalogRepository {

    var markovMatrixEnabled: Boolean = false

    /** Override this to supply a multi-emission flow (e.g. for the toggling test). */
    var markovMatrixFlow: Flow<Boolean>? = null

    var liquidityMapEnabled: Boolean = false

    /** Override this to supply a multi-emission flow for LIQUIDITY_MAP. */
    var liquidityMapFlow: Flow<Boolean>? = null

    var analyticsEnabled: Boolean = false

    /** Override this to supply a multi-emission flow for ANALYTICS. */
    var analyticsFlow: Flow<Boolean>? = null

    override suspend fun refresh() {
        // No-op in tests — tests configure the result directly.
    }

    override fun isEnabledFlow(id: Int, default: Boolean): Flow<Boolean> {
        if (id == CatalogFlags.MARKOV_MATRIX) {
            return markovMatrixFlow ?: flowOf(markovMatrixEnabled)
        }
        if (id == CatalogFlags.LIQUIDITY_MAP) {
            return liquidityMapFlow ?: flowOf(liquidityMapEnabled)
        }
        if (id == CatalogFlags.ANALYTICS) {
            return analyticsFlow ?: flowOf(analyticsEnabled)
        }
        return flowOf(default)
    }

    override fun isEnabled(id: Int): Boolean {
        if (id == CatalogFlags.MARKOV_MATRIX) return markovMatrixEnabled
        if (id == CatalogFlags.LIQUIDITY_MAP) return liquidityMapEnabled
        if (id == CatalogFlags.ANALYTICS) return analyticsEnabled
        return false
    }

    override fun isEnabled(id: Int, default: Boolean): Boolean {
        if (id == CatalogFlags.MARKOV_MATRIX) return markovMatrixEnabled
        if (id == CatalogFlags.LIQUIDITY_MAP) return liquidityMapEnabled
        if (id == CatalogFlags.ANALYTICS) return analyticsEnabled
        return default
    }
}
