// NOTE: instrumented Compose test — NOT run in the JVM pipeline. Requires device/emulator.
package com.gshashank.btcagent.ui.positions

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented UI tests for [PositionsListScreen] — MOBILE-6.
 *
 * NOTE: instrumented Compose test — NOT run in the JVM pipeline.
 * Requires device/emulator.
 *
 * These tests verify that [PositionsListScreen] renders the correct UI for each variant of
 * [UiState<PositionsScreenData>]. They drive a [MutableStateFlow] whose value is set to the
 * desired variant before rendering, consuming [PositionsListScreenContent] directly so no
 * ViewModel or Hilt injection is needed in tests.
 *
 * All tests MUST fail (red) until [PositionsListScreen] and related composables are implemented.
 *
 * Semantic contracts (testTags) that the implementation MUST honour:
 *   - testTag "positions_skeleton"        — shimmer skeleton shown in Loading state
 *   - testTag "positions_empty"           — empty state message shown when no open positions
 *   - testTag "position_card_{signalId}"  — per-position card, e.g. "position_card_sig-001"
 *   - testTag "positions_summary_unrealized" — unrealized P&L summary card
 *   - testTag "positions_summary_exposure"   — exposure summary card
 *
 * Test coverage:
 *   1. UiState.Loading → skeleton / progress visible.
 *   2. UiState.Ready with positions → position cards rendered.
 *   3. UiState.Empty → empty state message visible.
 *   4. Tapping a position card fires the navigation callback (onPositionClick).
 */
@RunWith(AndroidJUnit4::class)
class PositionsListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleLongPosition = Position(
        signalId = "sig-001",
        side = Side.Long,
        entryPrice = 50_000.0,
        currentPrice = 51_000.0,
        qty = 2.0,
        sl = 49_000.0,
        tp = 53_000.0,
        status = "open",
        openedAt = "2026-06-24T10:00:00Z",
        pnl = 2.0,
        pnlPct = 2.0,
        contractSize = 0.001,
    )

    private val sampleShortPosition = Position(
        signalId = "sig-002",
        side = Side.Short,
        entryPrice = 60_000.0,
        currentPrice = 59_000.0,
        qty = 1.0,
        sl = 61_000.0,
        tp = 57_000.0,
        status = "open",
        openedAt = "2026-06-24T09:00:00Z",
        pnl = 1.0,
        pnlPct = 1.0,
        contractSize = 0.001,
    )

    private val sampleScreenData = PositionsScreenData(
        unrealizedTotal = 3.0,
        exposureTotal = 160.0,
        positions = listOf(sampleLongPosition, sampleShortPosition),
    )

    // =========================================================================
    // 1. UiState.Loading → skeleton visible
    // =========================================================================

    @Test
    fun loading_state_shows_skeleton() {
        val uiStateFlow = MutableStateFlow<UiState<PositionsScreenData>>(UiState.Loading)

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                PositionsListScreenContent(
                    uiState = uiState,
                    onRetry = {},
                    onPositionClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("positions_skeleton")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 2. UiState.Ready with positions → position cards rendered
    // =========================================================================

    @Test
    fun ready_state_displays_position_cards() {
        val uiStateFlow =
            MutableStateFlow<UiState<PositionsScreenData>>(UiState.Ready(sampleScreenData))

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                PositionsListScreenContent(
                    uiState = uiState,
                    onRetry = {},
                    onPositionClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("position_card_sig-001")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("position_card_sig-002")
            .assertIsDisplayed()
    }

    @Test
    fun ready_state_displays_summary_cards() {
        val uiStateFlow =
            MutableStateFlow<UiState<PositionsScreenData>>(UiState.Ready(sampleScreenData))

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                PositionsListScreenContent(
                    uiState = uiState,
                    onRetry = {},
                    onPositionClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("positions_summary_unrealized")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("positions_summary_exposure")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 3. UiState.Empty → empty state message visible
    // =========================================================================

    @Test
    fun empty_state_shows_empty_message() {
        val uiStateFlow = MutableStateFlow<UiState<PositionsScreenData>>(UiState.Empty)

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                PositionsListScreenContent(
                    uiState = uiState,
                    onRetry = {},
                    onPositionClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("positions_empty")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 4. Tapping a position card fires the onPositionClick callback
    // =========================================================================

    @Test
    fun tapping_position_card_fires_navigation_callback() {
        val uiStateFlow =
            MutableStateFlow<UiState<PositionsScreenData>>(UiState.Ready(sampleScreenData))

        var clickedSignalId: String? = null

        composeTestRule.setContent {
            BTCAgentTheme {
                val uiState by uiStateFlow.collectAsState()
                PositionsListScreenContent(
                    uiState = uiState,
                    onRetry = {},
                    onPositionClick = { signalId -> clickedSignalId = signalId },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("position_card_sig-001")
            .performClick()

        assertEquals(
            "onPositionClick callback must be fired with the tapped position's signalId",
            "sig-001",
            clickedSignalId,
        )
    }
}
