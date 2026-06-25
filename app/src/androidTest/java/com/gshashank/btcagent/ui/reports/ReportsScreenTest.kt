// NOTE: instrumented Compose test — NOT run in the JVM pipeline. Requires device/emulator.
package com.gshashank.btcagent.ui.reports

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gshashank.btcagent.data.model.ClosedTrade
import com.gshashank.btcagent.data.model.ReportsData
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented UI tests for [ReportsScreen] — MOBILE-7.
 *
 * NOTE: instrumented Compose test — NOT run in the JVM pipeline.
 * Requires device/emulator.
 *
 * These tests verify that [ReportsScreen] renders the correct UI for each variant of
 * [UiState<ReportsData>]. They drive a [MutableStateFlow] whose value is set to the desired
 * variant before rendering, consuming [ReportsScreenContent] directly so no ViewModel or
 * Hilt injection is needed in tests.
 *
 * All tests MUST fail (red) until [ReportsScreen] and related composables are implemented.
 *
 * Semantic contracts (testTags) that the implementation MUST honour:
 *   - testTag "reports_skeleton"         — shimmer skeleton shown in Loading state.
 *   - testTag "reports_stat_signals"     — signals-today stat tile in Ready state.
 *   - testTag "reports_stat_winrate"     — win-rate stat tile in Ready state.
 *   - testTag "reports_stat_weekpnl"     — week P&L stat tile in Ready state.
 *   - testTag "reports_trade_row_{idx}"  — closed-trade row by index, e.g. "reports_trade_row_0".
 *   - testTag "reports_empty"            — empty state message when no trades.
 *
 * Test coverage:
 *   1. UiState.Loading → skeleton visible.
 *   2. UiState.Ready → all 3 stat tiles visible.
 *   3. UiState.Ready → trade rows visible with colored P&L.
 *   4. UiState.Empty → empty state shown when no trades.
 */
@RunWith(AndroidJUnit4::class)
class ReportsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val winningTrade = ClosedTrade(
        closedAt = "2026-06-24T10:00:00Z",
        side = Side.Long,
        entryPrice = 50_000.0,
        exitPrice = 51_000.0,
        pnl = 100.0,
        pattern = "Bull Flag",
    )

    private val losingTrade = ClosedTrade(
        closedAt = "2026-06-24T09:00:00Z",
        side = Side.Short,
        entryPrice = 60_000.0,
        exitPrice = 61_000.0,
        pnl = -50.0,
        pattern = "Bear Flag",
    )

    private val sampleReportsData = ReportsData(
        signalsToday = 3,
        winRatePct = 66.7,
        weekPnl = 250.0,
        trades = listOf(winningTrade, losingTrade),
    )

    // =========================================================================
    // 1. UiState.Loading → skeleton visible
    // =========================================================================

    @Test
    fun loading_state_shows_skeleton() {
        val uiStateFlow = MutableStateFlow<UiState<ReportsData>>(UiState.Loading)

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                ReportsScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("reports_skeleton")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 2. UiState.Ready → all 3 stat tiles visible
    // =========================================================================

    @Test
    fun ready_state_displays_all_three_stat_tiles() {
        val uiStateFlow =
            MutableStateFlow<UiState<ReportsData>>(UiState.Ready(sampleReportsData))

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                ReportsScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("reports_stat_signals")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("reports_stat_winrate")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("reports_stat_weekpnl")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 3. UiState.Ready → trade rows visible with colored P&L
    // =========================================================================

    @Test
    fun ready_state_displays_trade_rows() {
        val uiStateFlow =
            MutableStateFlow<UiState<ReportsData>>(UiState.Ready(sampleReportsData))

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                ReportsScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        // First trade row (index 0)
        composeTestRule
            .onNodeWithTag("reports_trade_row_0")
            .assertIsDisplayed()

        // Second trade row (index 1)
        composeTestRule
            .onNodeWithTag("reports_trade_row_1")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 4. UiState.Empty → empty state shown when no trades
    // =========================================================================

    @Test
    fun empty_state_shows_empty_message() {
        val uiStateFlow = MutableStateFlow<UiState<ReportsData>>(UiState.Empty)

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                ReportsScreenContent(
                    uiState = uiState,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("reports_empty")
            .assertIsDisplayed()
    }
}
