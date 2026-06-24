// androidTest — requires instrumented device/emulator. NOT run in this pipeline.
package com.gshashank.btcagent.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
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
}
