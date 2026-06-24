// NOTE: instrumented Compose test — NOT run in the JVM pipeline. Requires device/emulator via /run-app.
package com.gshashank.btcagent.ui.components.state

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented UI tests for [DataStateScaffold] — MOBILE-24.
 *
 * NOTE: instrumented Compose test — NOT run in the JVM pipeline.
 * Requires device/emulator via /run-app.
 *
 * These tests verify that [DataStateScaffold] renders the correct slot composable for
 * each variant of [UiState]. All tests are expected to FAIL until the implementation
 * in DataStateScaffold.kt is written.
 *
 * Semantic contracts verified by these tests (implementer MUST honour them):
 *   - testTag "scaffold_skeleton"   — the default skeleton placeholder in Loading state
 *   - testTag "scaffold_offline_banner" — the offline banner shown in Offline state
 *   - Text "Nothing here yet"       — shown in Empty state
 *   - Text "Refresh"                — button shown in Empty state
 *   - Text "Try again"              — button shown in Error state
 *   - Text "Connect to load data"   — shown in Offline state when hasCache = false
 *   - Error code text in a mono chip in Error state
 *   - Content slot receives the Ready/cached data as a parameter
 *
 * No catalog flag is involved for this story (MOBILE-24 explicitly opted out).
 */
@RunWith(AndroidJUnit4::class)
class DataStateScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // 1. Loading state renders skeleton and does NOT invoke content slot
    // =========================================================================

    @Test
    fun loading_state_renders_skeleton_and_not_content_slot() {
        var contentSlotInvoked = false

        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Loading,
                    onRefresh = {},
                    onRetry = {},
                ) {
                    contentSlotInvoked = true
                    Text("content", Modifier.testTag("scaffold_content"))
                }
            }
        }

        // Default skeleton placeholder must be visible.
        composeTestRule
            .onNodeWithTag("scaffold_skeleton")
            .assertIsDisplayed()

        // The content slot must NOT have been invoked while loading.
        assertEquals(
            "Content slot must NOT be invoked when state is Loading",
            false,
            contentSlotInvoked,
        )
    }

    // =========================================================================
    // 2. Empty state renders "Nothing here yet" text and "Refresh" button
    // =========================================================================

    @Test
    fun empty_state_renders_empty_content_with_refresh_button() {
        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Empty,
                    onRefresh = {},
                    onRetry = {},
                ) {
                    Text("content")
                }
            }
        }

        composeTestRule
            .onNodeWithText("Nothing here yet")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Refresh")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 3. Error state renders error message, code chip, and "Try again" button
    // =========================================================================

    @Test
    fun error_state_renders_message_code_and_retry_button() {
        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Error(code = "ERR_503", message = "Couldn't load data"),
                    onRefresh = {},
                    onRetry = {},
                ) {
                    Text("content")
                }
            }
        }

        // Human-readable headline must be displayed.
        composeTestRule
            .onNodeWithText("Couldn't load data")
            .assertIsDisplayed()

        // Machine error code must be present in the mono chip.
        composeTestRule
            .onNodeWithText("ERR_503")
            .assertIsDisplayed()

        // Retry button must be present and clickable.
        composeTestRule
            .onNodeWithText("Try again")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 4. Offline state with no cache renders banner + "Connect to load data"
    // =========================================================================

    @Test
    fun offline_no_cache_state_renders_banner_and_connect_prompt() {
        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Offline(lastUpdatedMs = 0L, hasCache = false),
                    cachedData = null,
                    onRefresh = {},
                    onRetry = {},
                ) { data: String ->
                    Text("cached: $data")
                }
            }
        }

        // Offline banner must contain the word "offline" (case-insensitive substring match).
        composeTestRule
            .onNodeWithTag("scaffold_offline_banner")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("offline", substring = true, ignoreCase = true)
            .assertIsDisplayed()

        // No-cache prompt must be present.
        composeTestRule
            .onNodeWithText("Connect to load data")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 5. Offline state WITH cache renders banner + content slot called with stale data
    // =========================================================================

    @Test
    fun offline_with_cache_state_renders_banner_and_content_with_stale_data() {
        val someMs = 1_700_000_000_000L
        var slotData: String? = null

        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Offline(lastUpdatedMs = someMs, hasCache = true),
                    cachedData = "stale",
                    onRefresh = {},
                    onRetry = {},
                ) { data: String ->
                    slotData = data
                    Text("cached: $data", Modifier.testTag("scaffold_cached_content"))
                }
            }
        }

        // Offline banner must be present.
        composeTestRule
            .onNodeWithTag("scaffold_offline_banner")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("offline", substring = true, ignoreCase = true)
            .assertIsDisplayed()

        // Content slot must have been invoked with the stale cached data.
        assertEquals(
            "Content slot must be invoked with the stale cached data when Offline + hasCache = true",
            "stale",
            slotData,
        )

        composeTestRule
            .onNodeWithTag("scaffold_cached_content")
            .assertIsDisplayed()
    }

    // =========================================================================
    // 6. Ready state renders content slot; no banner, no skeleton
    // =========================================================================

    @Test
    fun ready_state_renders_content_slot_without_banner_or_skeleton() {
        var slotData: String? = null

        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Ready("live"),
                    onRefresh = {},
                    onRetry = {},
                ) { data: String ->
                    slotData = data
                    Text("live data: $data", Modifier.testTag("scaffold_ready_content"))
                }
            }
        }

        // Content slot must be invoked with the live data.
        assertEquals(
            "Content slot must be invoked with Ready.data when state is Ready",
            "live",
            slotData,
        )

        composeTestRule
            .onNodeWithTag("scaffold_ready_content")
            .assertIsDisplayed()

        // No offline banner must be present.
        composeTestRule
            .onNodeWithTag("scaffold_offline_banner")
            .assertDoesNotExist()

        // No skeleton must be present.
        composeTestRule
            .onNodeWithTag("scaffold_skeleton")
            .assertDoesNotExist()
    }

    // =========================================================================
    // 7. Custom skeleton slot replaces the default skeleton in Loading state
    // =========================================================================

    @Test
    fun loading_state_custom_skeleton_slot_is_rendered() {
        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Loading,
                    skeleton = {
                        Text("custom_skeleton", Modifier.testTag("custom_skeleton_tag"))
                    },
                    onRefresh = {},
                    onRetry = {},
                ) {
                    Text("content")
                }
            }
        }

        // Custom skeleton text must be visible.
        composeTestRule
            .onNodeWithTag("custom_skeleton_tag")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("custom_skeleton")
            .assertIsDisplayed()

        // Default scaffold_skeleton must NOT be present when a custom skeleton is provided.
        composeTestRule
            .onNodeWithTag("scaffold_skeleton")
            .assertDoesNotExist()
    }

    // =========================================================================
    // 8. Refresh callback fires exactly once when "Refresh" button clicked in Empty state
    // =========================================================================

    @Test
    fun empty_state_refresh_button_click_invokes_onRefresh_exactly_once() {
        var onRefreshCallCount = 0

        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Empty,
                    onRefresh = { onRefreshCallCount++ },
                    onRetry = {},
                ) {
                    Text("content")
                }
            }
        }

        composeTestRule
            .onNodeWithText("Refresh")
            .performClick()

        assertEquals(
            "onRefresh must be invoked exactly once when the Refresh button is clicked in Empty state",
            1,
            onRefreshCallCount,
        )
    }

    // =========================================================================
    // 9. Retry callback fires exactly once when "Try again" button clicked in Error state
    // =========================================================================

    @Test
    fun error_state_retry_button_click_invokes_onRetry_exactly_once() {
        var onRetryCallCount = 0

        composeTestRule.setContent {
            BTCAgentTheme {
                DataStateScaffold(
                    uiState = UiState.Error(code = "ERR_503", message = "Couldn't load data"),
                    onRefresh = {},
                    onRetry = { onRetryCallCount++ },
                ) {
                    Text("content")
                }
            }
        }

        composeTestRule
            .onNodeWithText("Try again")
            .performClick()

        assertEquals(
            "onRetry must be invoked exactly once when the Try again button is clicked in Error state",
            1,
            onRetryCallCount,
        )
    }
}
