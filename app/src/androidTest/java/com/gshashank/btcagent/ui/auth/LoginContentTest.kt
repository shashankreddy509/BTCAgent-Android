package com.gshashank.btcagent.ui.auth

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Compose UI tests for the stateless [LoginContent] composable.
 *
 * All tests wrap [LoginContent] in [BTCAgentTheme] via [createComposeRule].
 * Every test is expected to FAIL until [LoginContent] is implemented.
 *
 * Semantic conventions assumed by these tests (implementer must honour them):
 *   - Bitcoin logo image has contentDescription = "Bitcoin logo"
 *   - Title text = "BTC AI Agent"
 *   - Google sign-in button has text "Continue with Google"
 *   - CircularProgressIndicator inside the button has testTag = "sign_in_loading_indicator"
 *   - Legal fine-print text contains the word "Terms" (or is otherwise findable by partial text)
 *   - Supporting paragraph is present and visible
 */
@RunWith(AndroidJUnit4::class)
class LoginContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // 1. Renders all static elements in Idle state
    // -------------------------------------------------------------------------

    @Test
    fun renders_all_static_elements_in_idle_state() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    onGoogleSignIn = {},
                )
            }
        }

        // Bitcoin logo tile
        composeTestRule
            .onNodeWithContentDescription("Bitcoin logo")
            .assertIsDisplayed()

        // App title
        composeTestRule
            .onNodeWithText("BTC AI Agent")
            .assertIsDisplayed()

        // Supporting paragraph (partial text match on a distinctive word)
        // The implementer must include a supporting paragraph; matching any node
        // whose text contains "AI" or "Bitcoin" as a sanity check.
        // Using a tag to be precise — implementer must set testTag "supporting_text".
        composeTestRule
            .onNodeWithTag("supporting_text")
            .assertIsDisplayed()

        // Sign-in button
        composeTestRule
            .onNodeWithText("Continue with Google")
            .assertIsDisplayed()

        // Legal fine-print — implementer must set testTag "legal_text".
        composeTestRule
            .onNodeWithTag("legal_text")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 2. Clicking the button invokes onGoogleSignIn exactly once
    // -------------------------------------------------------------------------

    @Test
    fun clicking_sign_in_button_invokes_callback_once() {
        var callCount = 0

        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    onGoogleSignIn = { callCount++ },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Continue with Google")
            .performClick()

        assertEquals("onGoogleSignIn must be called exactly once", 1, callCount)
    }

    // -------------------------------------------------------------------------
    // 3. Loading state disables button and shows spinner
    // -------------------------------------------------------------------------

    @Test
    fun loading_state_disables_button_and_shows_spinner() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Loading,
                    onGoogleSignIn = {},
                )
            }
        }

        // Button must be disabled (not clickable / not enabled).
        composeTestRule
            .onNodeWithText("Continue with Google")
            .assertIsNotEnabled()

        // CircularProgressIndicator must be present with the agreed testTag.
        composeTestRule
            .onNodeWithTag("sign_in_loading_indicator")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 4. Error state shows the error message on screen
    // -------------------------------------------------------------------------

    @Test
    fun error_state_shows_error_message() {
        val errorMessage = "Sign-in failed"

        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Error(errorMessage),
                    onGoogleSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(errorMessage)
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 5. Error state keeps the button enabled for retry
    // -------------------------------------------------------------------------

    @Test
    fun error_state_button_remains_enabled() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Error("Something went wrong"),
                    onGoogleSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Continue with Google")
            .assertIsEnabled()
    }

    // -------------------------------------------------------------------------
    // 6. Legal text is decorative / not clickable
    // -------------------------------------------------------------------------

    @Test
    fun legal_text_is_not_clickable() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    onGoogleSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("legal_text")
            .assertHasNoClickAction()
    }

    // =========================================================================
    // Catalog flag-gating contract (MOBILE-28)
    //
    // LoginContent takes `isMockLayout: Boolean` as a plain param. Both branches
    // are driven directly — no CatalogRepository, no Hilt, no fake needed here.
    // =========================================================================

    // -------------------------------------------------------------------------
    // 7. Flag OFF: old subtitle copy shown; new subtitle copy absent
    // -------------------------------------------------------------------------

    @Test
    fun flag_off_idle_shows_old_subtitle_copy() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    isMockLayout = false,
                    onGoogleSignIn = {},
                )
            }
        }

        // Old subtitle must be present when flag is OFF.
        composeTestRule
            .onNodeWithText("Your AI-powered Bitcoin", substring = true)
            .assertIsDisplayed()

        // New subtitle must NOT be present when flag is OFF.
        composeTestRule
            .onNodeWithText("Monitor and control", substring = true)
            .assertDoesNotExist()
    }

    // -------------------------------------------------------------------------
    // 8. Flag ON: new subtitle copy shown; old subtitle copy absent
    // -------------------------------------------------------------------------

    @Test
    fun flag_on_idle_shows_new_subtitle_copy() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    isMockLayout = true,
                    onGoogleSignIn = {},
                )
            }
        }

        // New subtitle must be present when flag is ON.
        composeTestRule
            .onNodeWithText("Monitor and control your server-side trading bot", substring = true)
            .assertIsDisplayed()

        // Old subtitle must NOT be present when flag is ON.
        composeTestRule
            .onNodeWithText("Your AI-powered Bitcoin", substring = true)
            .assertDoesNotExist()
    }

    // -------------------------------------------------------------------------
    // 9. Flag OFF: old footer copy shown
    // -------------------------------------------------------------------------

    @Test
    fun flag_off_idle_shows_old_footer_copy() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    isMockLayout = false,
                    onGoogleSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Terms of Service and Privacy Policy", substring = true)
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 10. Flag ON: new footer copy shown
    // -------------------------------------------------------------------------

    @Test
    fun flag_on_idle_shows_new_footer_copy() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    isMockLayout = true,
                    onGoogleSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Terms & Risk Disclosure", substring = true)
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 11. Flag ON + Idle: button present and enabled
    // -------------------------------------------------------------------------

    @Test
    fun flag_on_idle_button_present_and_enabled() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Idle,
                    isMockLayout = true,
                    onGoogleSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Continue with Google")
            .assertIsEnabled()
    }

    // -------------------------------------------------------------------------
    // 12. Flag ON + Loading: button disabled and spinner visible
    // -------------------------------------------------------------------------

    @Test
    fun flag_on_loading_disables_button_and_shows_spinner() {
        composeTestRule.setContent {
            BTCAgentTheme {
                LoginContent(
                    uiState = LoginUiState.Loading,
                    isMockLayout = true,
                    onGoogleSignIn = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Continue with Google")
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag("sign_in_loading_indicator")
            .assertIsDisplayed()
    }
}
