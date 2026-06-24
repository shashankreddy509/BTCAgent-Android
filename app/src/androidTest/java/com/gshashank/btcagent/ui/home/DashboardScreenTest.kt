// NOTE: instrumented Compose test — NOT run in the JVM pipeline. Requires device/emulator.
package com.gshashank.btcagent.ui.home

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gshashank.btcagent.data.model.BotMode
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.data.model.PriceDirection
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented UI tests for [DashboardScreen] — MOBILE-5.
 *
 * NOTE: instrumented Compose test — NOT run in the JVM pipeline.
 * Requires device/emulator.
 *
 * These tests verify that [DashboardScreen] renders the correct UI for each variant of
 * [UiState<DashboardData>]. They drive a [FakeDashboardViewModelForUi] whose [uiState]
 * StateFlow is set to the desired variant before rendering.
 *
 * All tests MUST fail (red) until [DashboardScreen], [DashboardHeroContent], and
 * [DashboardHeroSkeleton] are implemented.
 *
 * Semantic contracts (testTags) that the implementation MUST honour:
 *   - testTag "dashboard_skeleton"   — the shimmer skeleton shown in Loading state
 *   - testTag "dashboard_price"      — the price headline text node shown in Ready state
 *   - testTag "dashboard_retry"      — the retry button shown in Error state
 *   - testTag "dashboard_offline"    — the offline banner shown in Offline state
 *
 * Test coverage:
 *   1. UiState.Loading → shimmer skeleton visible (testTag "dashboard_skeleton").
 *   2. UiState.Ready   → price text is displayed (testTag "dashboard_price").
 *   3. UiState.Error   → retry button visible (testTag "dashboard_retry").
 *   4. UiState.Offline → offline banner visible (testTag "dashboard_offline").
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** A stable [DashboardData] used in the Ready-state test. */
    private val sampleData = DashboardData(
        btcPrice = 67_432.5,
        priceDirection = PriceDirection.Up,
        todayPnlPts = 15.75,
        openPositionCount = 2,
        openUnrealisedPnl = 200.0,
        botRunning = true,
        botMode = BotMode.Live,
    )

    // =========================================================================
    // 1. UiState.Loading → shimmer skeleton visible
    // =========================================================================

    @Test
    fun loading_state_shows_shimmer_skeleton() {
        val uiStateFlow = MutableStateFlow<UiState<DashboardData>>(UiState.Loading)

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                DashboardScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("dashboard_skeleton")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 2. UiState.Ready → price text is displayed
    // =========================================================================

    @Test
    fun ready_state_displays_price_text() {
        val uiStateFlow = MutableStateFlow<UiState<DashboardData>>(UiState.Ready(sampleData))

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                DashboardScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        // The price node tagged "dashboard_price" must be present and displayed.
        composeTestRule
            .onNodeWithTag("dashboard_price")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 3. UiState.Error → retry button visible
    // =========================================================================

    @Test
    fun error_state_shows_retry_button() {
        val uiStateFlow = MutableStateFlow<UiState<DashboardData>>(
            UiState.Error(code = "ERR_FETCH", message = "Could not load dashboard"),
        )

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                DashboardScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("dashboard_retry")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 4. UiState.Offline → offline banner visible
    // =========================================================================

    @Test
    fun offline_state_shows_offline_banner() {
        val uiStateFlow = MutableStateFlow<UiState<DashboardData>>(
            UiState.Offline(lastUpdatedMs = System.currentTimeMillis() - 60_000L, hasCache = false),
        )

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                DashboardScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("dashboard_offline")
            .assertIsDisplayed()
    }
}
