package com.gshashank.btcagent.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.ui.theme.BtcAccent
import com.gshashank.btcagent.ui.theme.BtcAccentDark

/**
 * Stateless content composable. Renders the Login screen UI.
 *
 * Semantic contracts (required by LoginContentTest):
 * - Logo tile has contentDescription = "Bitcoin logo" via semantics modifier
 * - Title text = "BTC AI Agent"
 * - testTag "supporting_text" on the supporting paragraph
 * - Button label "Continue with Google" always present (disabled when Loading)
 * - testTag "sign_in_loading_indicator" on CircularProgressIndicator shown during Loading
 * - testTag "legal_text" on the legal fine-print (no click action)
 *
 * When [isMockLayout] is true, renders the mock-aligned layout (MOBILE-28).
 * When false (default), renders the existing centered layout unchanged.
 */
@Composable
fun LoginContent(
    uiState: LoginUiState,
    isMockLayout: Boolean = false,
    onGoogleSignIn: () -> Unit,
) {
    if (isMockLayout) {
        LoginContentMock(uiState = uiState, onGoogleSignIn = onGoogleSignIn)
    } else {
        val isLoading = uiState is LoginUiState.Loading

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Bitcoin logo tile — 64dp, gradient fill, 18dp radius.
                // semantics block supplies "Bitcoin logo" contentDescription for tests.
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(BtcAccent, BtcAccentDark),
                            )
                        )
                        .semantics { contentDescription = "Bitcoin logo" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "₿",
                        color = Color.Black,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // App title — H1 28sp / SemiBold
                Text(
                    text = "BTC AI Agent",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Supporting paragraph
                Text(
                    text = "Your AI-powered Bitcoin trading assistant. Intelligent insights, real-time data.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .testTag("supporting_text"),
                )

                // Error message shown when state is Error
                if (uiState is LoginUiState.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Loading spinner shown above the button when in Loading state.
                // The button itself stays visible (with text) so tests can find it by text.
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("sign_in_loading_indicator"),
                        color = BtcAccent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Google sign-in button — 52dp height, 14dp radius, BtcBg2 fill, card-border stroke.
                // Button text "Continue with Google" is always present; button is disabled when Loading.
                Button(
                    onClick = onGoogleSignIn,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(14.dp),
                        ),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // "G" chip — only shown when not loading
                        if (!isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "G",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                color = if (isLoading) {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f)
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Legal fine-print — 11sp, text3 colour, no click action
                Text(
                    text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .testTag("legal_text"),
                )
            }
        }
    }
}

/**
 * Mock-aligned login layout (MOBILE-28).
 *
 * Top-anchored header block (logo + title + subtitle, left-aligned), flexible spacer,
 * bottom-pinned CTA button, updated copy, warm gradient background.
 */
@Composable
private fun LoginContentMock(
    uiState: LoginUiState,
    onGoogleSignIn: () -> Unit,
) {
    val isLoading = uiState is LoginUiState.Loading
    val warmGradientBrush = Brush.linearGradient(
        listOf(BtcAccent.copy(alpha = 0.06f), MaterialTheme.colorScheme.background)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = warmGradientBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // Bitcoin logo tile — 64dp, gradient fill, 18dp radius.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BtcAccent, BtcAccentDark),
                        )
                    )
                    .semantics { contentDescription = "Bitcoin logo" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "₿",
                    color = Color.Black,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App title
            Text(
                text = "BTC AI Agent",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                textAlign = TextAlign.Start,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Supporting paragraph — new copy
            Text(
                text = "Monitor and control your server-side trading bot. Live price, positions, analytics — from your pocket.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("supporting_text"),
            )

            // Error message shown when state is Error
            if (uiState is LoginUiState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                )
            }

            // Flexible spacer pushes button + footer to bottom
            Spacer(modifier = Modifier.weight(1f))

            // Loading spinner shown above the button when in Loading state.
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("sign_in_loading_indicator"),
                    color = BtcAccent,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Google sign-in button
            Button(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(14.dp),
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // "G" chip — only shown when not loading
                    if (!isLoading) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "G",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            color = if (isLoading) {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f)
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legal fine-print — new copy
            Text(
                text = "Access is allow-listed. New sign-ins require owner approval. By continuing you agree to the Terms & Risk Disclosure.",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.40f),
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("legal_text"),
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Stateful wrapper. Collects [LoginViewModel.uiState] with lifecycle-awareness and calls
 * [onAuthenticated] exactly once when state transitions to [LoginUiState.Success].
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onAuthenticated: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMockLayout by viewModel.isMockLayout.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onAuthenticated()
        }
    }

    LoginContent(
        uiState = uiState,
        isMockLayout = isMockLayout,
        onGoogleSignIn = {
            // Cast is safe: LoginScreen is always hosted inside an Activity context.
            val activity = context as? Activity
            if (activity != null) {
                viewModel.onGoogleSignIn(activity)
            }
        },
    )
}
