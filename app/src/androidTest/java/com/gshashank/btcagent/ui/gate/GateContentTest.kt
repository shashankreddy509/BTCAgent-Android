package com.gshashank.btcagent.ui.gate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gshashank.btcagent.ui.theme.BTCAgentTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented UI tests for the stateless [GateContent] composable.
 *
 * All tests wrap [GateContent] in [BTCAgentTheme] via [createComposeRule].
 * Every test is expected to FAIL until [GateContent] is implemented.
 *
 * Semantic contracts verified by these tests (implementer MUST honour them):
 *   - testTag "gate_spinner"           — CircularProgressIndicator ring in Pending state
 *   - testTag "gate_headline"          — headline text node in Pending state
 *   - testTag "gate_email"             — email text node in Pending state
 *   - testTag "gate_status_chip"       — status chip Surface in Pending state
 *   - testTag "gate_sign_out"          — sign-out TextButton in Pending state
 *   - testTag "gate_loading_indicator" — CircularProgressIndicator in Loading state
 *   - testTag "gate_retry_button"      — retry Button in Error state
 *   - Headline text exactly "Waiting for approval"
 *   - Sign-out button label "Sign out"
 */
@RunWith(AndroidJUnit4::class)
class GateContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // 1. Pending state renders all required elements
    // -------------------------------------------------------------------------

    @Test
    fun pending_state_renders_all_required_elements() {
        composeTestRule.setContent {
            BTCAgentTheme {
                GateContent(
                    uiState = GateUiState.Pending(email = "test@example.com"),
                    onSignOut = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("gate_spinner")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("gate_headline")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("gate_email")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("gate_status_chip")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("gate_sign_out")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 2. Headline text is exactly "Waiting for approval"
    // -------------------------------------------------------------------------

    @Test
    fun pending_state_headline_text_is_waiting_for_approval() {
        composeTestRule.setContent {
            BTCAgentTheme {
                GateContent(
                    uiState = GateUiState.Pending(email = "test@example.com"),
                    onSignOut = {},
                    onRetry = {},
                )
            }
        }

        // onNodeWithTag to locate the headline node, then verify its text.
        composeTestRule
            .onNodeWithTag("gate_headline")
            .assertIsDisplayed()

        // Also verify via exact text match so both tag and text contract are asserted.
        composeTestRule
            .onNodeWithText("Waiting for approval")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 3. Email text matches the Pending state email
    // -------------------------------------------------------------------------

    @Test
    fun pending_state_email_text_matches_state_email() {
        val email = "test@example.com"

        composeTestRule.setContent {
            BTCAgentTheme {
                GateContent(
                    uiState = GateUiState.Pending(email = email),
                    onSignOut = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("gate_email")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(email)
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 4. Sign-out click invokes onSignOut callback
    // -------------------------------------------------------------------------

    @Test
    fun pending_state_sign_out_click_invokes_callback() {
        var onSignOutCalled = false

        composeTestRule.setContent {
            BTCAgentTheme {
                GateContent(
                    uiState = GateUiState.Pending(email = "test@example.com"),
                    onSignOut = { onSignOutCalled = true },
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("gate_sign_out")
            .performClick()

        assertTrue(
            "onSignOut callback must be invoked when the sign-out button is clicked",
            onSignOutCalled,
        )
    }

    // -------------------------------------------------------------------------
    // 5. Loading state shows gate_loading_indicator and NOT gate_headline
    // -------------------------------------------------------------------------

    @Test
    fun loading_state_shows_loading_indicator_and_not_headline() {
        composeTestRule.setContent {
            BTCAgentTheme {
                GateContent(
                    uiState = GateUiState.Loading,
                    onSignOut = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("gate_loading_indicator")
            .assertIsDisplayed()

        // gate_headline must not be present in the Loading state.
        composeTestRule
            .onNodeWithTag("gate_headline")
            .assertDoesNotExist()
    }

    // -------------------------------------------------------------------------
    // 6. Error state shows gate_retry_button; click invokes onRetry
    // -------------------------------------------------------------------------

    @Test
    fun error_state_shows_retry_button_and_click_invokes_onRetry() {
        var onRetryCalled = false

        composeTestRule.setContent {
            BTCAgentTheme {
                GateContent(
                    uiState = GateUiState.Error,
                    onSignOut = {},
                    onRetry = { onRetryCalled = true },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("gate_retry_button")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("gate_retry_button")
            .performClick()

        assertTrue(
            "onRetry callback must be invoked when the retry button is clicked",
            onRetryCalled,
        )
    }
}
