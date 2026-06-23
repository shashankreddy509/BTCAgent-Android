package com.gshashank.btcagent.ui.gate

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateless content composable for the Gate screen.
 *
 * Renders per-state UI:
 * - [GateUiState.Pending] — pending-approval screen (primary design spec).
 * - [GateUiState.Loading] — centered spinner.
 * - [GateUiState.Error]   — centered error message + retry button.
 * - [GateUiState.Allowed] / [GateUiState.Unauthorized] — empty box; navigation is driven by
 *   [LaunchedEffect] in [GateScreen].
 *
 * Semantic test-tag contracts (required by GateContentTest):
 *   "gate_spinner"           — CircularProgressIndicator ring in Pending state
 *   "gate_headline"          — headline "Waiting for approval" in Pending state
 *   "gate_email"             — email text in Pending state
 *   "gate_status_chip"       — status chip Surface in Pending state
 *   "gate_sign_out"          — sign-out TextButton in Pending state
 *   "gate_loading_indicator" — CircularProgressIndicator in Loading state
 *   "gate_retry_button"      — retry Button in Error state
 */
@Composable
fun GateContent(
    uiState: GateUiState,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState) {
            is GateUiState.Pending -> PendingContent(uiState = uiState, onSignOut = onSignOut)
            GateUiState.Loading -> LoadingContent()
            GateUiState.Error -> ErrorContent(onRetry = onRetry)
            GateUiState.Allowed, GateUiState.Unauthorized -> {
                // Empty — navigation is handled by LaunchedEffect in GateScreen.
            }
        }
    }
}

@Composable
private fun PendingContent(
    uiState: GateUiState.Pending,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // 84dp accent spinner ring.
        Box(
            modifier = Modifier
                .size(84.dp)
                .testTag("gate_spinner"),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Headline
        Text(
            text = "Waiting for approval",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier.testTag("gate_headline"),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Email
        Text(
            text = uiState.email,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
            ),
            modifier = Modifier.testTag("gate_email"),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status chip with pulsing dot
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = InfiniteRepeatableSpec(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotAlpha",
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.testTag("gate_status_chip"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha),
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pending review",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Sign-out button pinned to the bottom.
        TextButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("gate_sign_out"),
        ) {
            Text(
                text = "Sign out",
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LoadingContent() {
    CircularProgressIndicator(
        modifier = Modifier.testTag("gate_loading_indicator"),
    )
}

@Composable
private fun ErrorContent(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("gate_retry_button"),
        ) {
            Text("Retry")
        }
    }
}

/**
 * Stateful wrapper. Collects [GateViewModel.uiState] with lifecycle-awareness and fires
 * [onAllowed] when state is [GateUiState.Allowed] and [onSignedOut] when state is
 * [GateUiState.Unauthorized].
 */
@Composable
fun GateScreen(
    viewModel: GateViewModel,
    onAllowed: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        when (uiState) {
            GateUiState.Allowed -> onAllowed()
            GateUiState.Unauthorized -> onSignedOut()
            else -> Unit
        }
    }

    GateContent(
        uiState = uiState,
        onSignOut = viewModel::onSignOut,
        onRetry = viewModel::onRetry,
    )
}
