// androidTest — requires instrumented device/emulator. NOT run in this pipeline.
package com.gshashank.btcagent.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented UI tests for [AppShell] — MOBILE-10.
 *
 * These tests verify the bottom-nav shell and Markets hub navigation at the UI layer:
 *   - Tab switching selects the correct tab graph destination.
 *   - Back-stack preservation keeps the Markets back stack alive across tab switches.
 *   - Tile navigation inside [MarketsHubScreen] routes to the correct [MarketsRoute.*] destination.
 *
 * Semantic conventions assumed by these tests (implementer MUST honour them):
 *   - Each [NavigationBarItem] has a testTag matching its label (e.g. "tab_home", "tab_markets",
 *     "tab_trade", "tab_reports", "tab_settings").
 *   - The active tab's content area is identified by a testTag:
 *       "screen_home", "screen_markets", "screen_trade", "screen_reports", "screen_settings".
 *   - Each [AnalyticsTile] in [MarketsHubScreen] has a testTag matching its label:
 *       "tile_open_interest", "tile_btc_regime", "tile_markov_matrix",
 *       "tile_volume_profile", "tile_liquidity_map", "tile_zone_strategies", "tile_analytics".
 *   - Each stub destination screen in the Markets nested graph shows text
 *     "<Name> — coming soon" (e.g. "Open Interest — coming soon").
 *
 * All tests are expected to FAIL until [AppShell] and [MarketsHubScreen] are implemented.
 */
@RunWith(AndroidJUnit4::class)
class AppShellTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // Tab switching
    // =========================================================================

    // -------------------------------------------------------------------------
    // 1. Clicking the Markets tab shows the Markets hub screen
    // -------------------------------------------------------------------------

    @Test
    fun clicking_markets_tab_shows_markets_hub_screen() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule
            .onNodeWithTag("tab_markets")
            .performClick()

        // The Markets hub screen must be visible after clicking the Markets tab.
        composeTestRule
            .onNodeWithTag("screen_markets")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 2. Clicking the Home tab shows the Home tab screen
    // -------------------------------------------------------------------------

    @Test
    fun clicking_home_tab_shows_home_screen() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        // Navigate away first so we exercise an actual switch.
        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tab_home").performClick()

        composeTestRule
            .onNodeWithTag("screen_home")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 3. Clicking the Trade tab shows the Trade screen
    // -------------------------------------------------------------------------

    @Test
    fun clicking_trade_tab_shows_trade_screen() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule
            .onNodeWithTag("tab_trade")
            .performClick()

        composeTestRule
            .onNodeWithTag("screen_trade")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 4. Clicking the Reports tab shows the Reports screen
    // -------------------------------------------------------------------------

    @Test
    fun clicking_reports_tab_shows_reports_screen() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule
            .onNodeWithTag("tab_reports")
            .performClick()

        composeTestRule
            .onNodeWithTag("screen_reports")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 5. Clicking the Settings tab shows the Settings screen
    // -------------------------------------------------------------------------

    @Test
    fun clicking_settings_tab_shows_settings_screen() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule
            .onNodeWithTag("tab_settings")
            .performClick()

        composeTestRule
            .onNodeWithTag("screen_settings")
            .assertIsDisplayed()
    }

    // =========================================================================
    // Back-stack preservation
    // =========================================================================

    // -------------------------------------------------------------------------
    // 6. Back-stack preserved: navigate into Markets stub, switch tabs, return to Markets
    //    — stub screen (not hub) is still on the back stack
    // -------------------------------------------------------------------------

    @Test
    fun markets_back_stack_preserved_after_tab_switch() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        // Navigate to Markets tab.
        composeTestRule.onNodeWithTag("tab_markets").performClick()

        // Tap a tile to push a stub destination onto the Markets back stack.
        composeTestRule.onNodeWithTag("tile_open_interest").performClick()

        // The "Open Interest — coming soon" stub must be visible.
        composeTestRule
            .onNodeWithText("Open Interest — coming soon")
            .assertIsDisplayed()

        // Switch to the Home tab.
        composeTestRule.onNodeWithTag("tab_home").performClick()

        // The home screen should now be visible.
        composeTestRule
            .onNodeWithTag("screen_home")
            .assertIsDisplayed()

        // Switch back to Markets — the stub screen must still be on the back stack,
        // NOT the hub. Save/restore state must have preserved the Markets destination.
        composeTestRule.onNodeWithTag("tab_markets").performClick()

        composeTestRule
            .onNodeWithText("Open Interest — coming soon")
            .assertIsDisplayed()
    }

    // =========================================================================
    // Tile navigation — all 7 tiles in MarketsHubScreen
    // =========================================================================

    // -------------------------------------------------------------------------
    // 7. Tapping "Open Interest" tile navigates to the Open Interest stub
    // -------------------------------------------------------------------------

    @Test
    fun tile_open_interest_navigates_to_open_interest_stub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_open_interest").performClick()

        composeTestRule
            .onNodeWithText("Open Interest — coming soon")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 8. Tapping "BTC Regime" tile navigates to the BTC Regime stub
    // -------------------------------------------------------------------------

    @Test
    fun tile_btc_regime_navigates_to_btc_regime_stub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_btc_regime").performClick()

        composeTestRule
            .onNodeWithText("BTC Regime — coming soon")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 9. Tapping "Markov Matrix" tile navigates to the Markov Matrix stub
    // -------------------------------------------------------------------------

    @Test
    fun tile_markov_matrix_navigates_to_markov_matrix_stub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_markov_matrix").performClick()

        composeTestRule
            .onNodeWithText("Markov Matrix — coming soon")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 10. Tapping "Volume Profile" tile navigates to the Volume Profile stub
    // -------------------------------------------------------------------------

    @Test
    fun tile_volume_profile_navigates_to_volume_profile_stub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_volume_profile").performClick()

        composeTestRule
            .onNodeWithText("Volume Profile — coming soon")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 11. Tapping "Liquidity Map" tile navigates to the Liquidity Map stub
    // -------------------------------------------------------------------------

    @Test
    fun tile_liquidity_map_navigates_to_liquidity_map_stub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_liquidity_map").performClick()

        composeTestRule
            .onNodeWithText("Liquidity Map — coming soon")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 12. Tapping "Zone Strategies" tile navigates to the Zone Strategies stub
    // -------------------------------------------------------------------------

    @Test
    fun tile_zone_strategies_navigates_to_zone_strategies_stub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_zone_strategies").performClick()

        composeTestRule
            .onNodeWithText("Zone Strategies — coming soon")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 13. Tapping "Analytics" tile navigates to the Analytics stub
    // -------------------------------------------------------------------------

    @Test
    fun tile_analytics_navigates_to_analytics_stub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_analytics").performClick()

        composeTestRule
            .onNodeWithText("Analytics — coming soon")
            .assertIsDisplayed()
    }

    // =========================================================================
    // Markets hub content — RegimeBanner and tile grid are present
    // =========================================================================

    // -------------------------------------------------------------------------
    // 14. Markets hub shows RegimeBanner
    // -------------------------------------------------------------------------

    @Test
    fun markets_hub_shows_regime_banner() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()

        // RegimeBanner must be visible — implementer must set testTag "regime_banner".
        composeTestRule
            .onNodeWithTag("regime_banner")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 15. Markets hub shows all 7 analytics tiles in the grid
    // -------------------------------------------------------------------------

    @Test
    fun markets_hub_shows_all_seven_analytics_tiles() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        composeTestRule.onNodeWithTag("tab_markets").performClick()

        val tileTags = listOf(
            "tile_open_interest",
            "tile_btc_regime",
            "tile_markov_matrix",
            "tile_volume_profile",
            "tile_liquidity_map",
            "tile_zone_strategies",
            "tile_analytics",
        )

        tileTags.forEach { tag ->
            composeTestRule
                .onNodeWithTag(tag)
                .assertIsDisplayed()
        }
    }

    // =========================================================================
    // MOBILE-34 — Start destination must be Home, not Markets
    // =========================================================================

    // -------------------------------------------------------------------------
    // 16. Cold launch: the Home hub (screen_home) is shown without any user interaction.
    //     Fails pre-fix because NavHost startDestination = TabGraph.Markets shows
    //     screen_markets instead.
    // -------------------------------------------------------------------------

    @Test
    fun cold_launch_shows_home_tab_not_markets() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        // No taps — verify the initial destination is the Home hub.
        composeTestRule
            .onNodeWithTag("screen_home")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 17. Cold launch: the Markets screen is NOT shown without any user interaction.
    //     Fails pre-fix because startDestination = TabGraph.Markets shows screen_markets.
    // -------------------------------------------------------------------------

    @Test
    fun cold_launch_does_not_show_markets_screen() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        // No taps — the Markets hub must NOT be the active screen on startup.
        composeTestRule
            .onNodeWithTag("screen_markets")
            .assertIsNotDisplayed()
    }

    // =========================================================================
    // MOBILE-35 — Re-selecting the active tab must pop its nested back stack to the tab root.
    //
    // These tests use the Markets tab (detail screens are Hilt-free stubs) to exercise
    // the same onTabSelected branch that fixes all tabs including Home.
    // =========================================================================

    // -------------------------------------------------------------------------
    // 18. Re-selecting the Markets tab while a detail stub is on the Markets back stack
    //     pops back to the Markets hub.
    //     Fails pre-fix because onTabSelected always uses restoreState=true, so re-selecting
    //     the active tab restores the saved nested stack (detail on top) instead of popping
    //     to the hub.
    // -------------------------------------------------------------------------

    @Test
    fun reselecting_active_markets_tab_pops_detail_to_markets_hub() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        // Navigate to Markets and push a detail stub onto the Markets back stack.
        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_open_interest").performClick()

        // Confirm the detail stub is showing.
        composeTestRule
            .onNodeWithText("Open Interest — coming soon")
            .assertIsDisplayed()

        // Re-select the already-active Markets tab — must pop to the Markets hub.
        composeTestRule.onNodeWithTag("tab_markets").performClick()

        // The Markets hub must now be visible (not the detail stub).
        composeTestRule
            .onNodeWithTag("screen_markets")
            .assertIsDisplayed()

        // The detail stub must no longer be on screen.
        composeTestRule
            .onNodeWithText("Open Interest — coming soon")
            .assertIsNotDisplayed()
    }

    // -------------------------------------------------------------------------
    // 19. After reselecting the Markets tab to pop to hub, switching away and back
    //     still shows the hub (no stale detail is saved in the back stack).
    //     Verifies that the pop-to-root fix does not corrupt the save/restore mechanism
    //     for subsequent cross-tab switches.
    // -------------------------------------------------------------------------

    @Test
    fun after_reselect_pop_switching_tabs_does_not_restore_stale_detail() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        // Navigate to Markets and push a detail stub.
        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_btc_regime").performClick()

        composeTestRule
            .onNodeWithText("BTC Regime — coming soon")
            .assertIsDisplayed()

        // Reselect Markets tab — pops to hub.
        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule
            .onNodeWithTag("screen_markets")
            .assertIsDisplayed()

        // Switch to Trade then back to Markets — hub must still be shown.
        composeTestRule.onNodeWithTag("tab_trade").performClick()
        composeTestRule.onNodeWithTag("tab_markets").performClick()

        composeTestRule
            .onNodeWithTag("screen_markets")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("BTC Regime — coming soon")
            .assertIsNotDisplayed()
    }

    // -------------------------------------------------------------------------
    // 20. Switching between DIFFERENT tabs still preserves each tab's back stack
    //     (regression guard — the MOBILE-35 fix must not break cross-tab save/restore).
    // -------------------------------------------------------------------------

    @Test
    fun different_tab_switch_still_preserves_back_stacks() {
        composeTestRule.setContent {
            BTCAgentTheme {
                AppShell()
            }
        }

        // Push a detail onto the Markets stack.
        composeTestRule.onNodeWithTag("tab_markets").performClick()
        composeTestRule.onNodeWithTag("tile_volume_profile").performClick()

        composeTestRule
            .onNodeWithText("Volume Profile — coming soon")
            .assertIsDisplayed()

        // Switch away to Trade (a different tab) — Markets state should be saved.
        composeTestRule.onNodeWithTag("tab_trade").performClick()
        composeTestRule
            .onNodeWithTag("screen_trade")
            .assertIsDisplayed()

        // Return to Markets via a DIFFERENT-tab switch — detail must still be restored.
        composeTestRule.onNodeWithTag("tab_markets").performClick()

        composeTestRule
            .onNodeWithText("Volume Profile — coming soon")
            .assertIsDisplayed()
    }
}
