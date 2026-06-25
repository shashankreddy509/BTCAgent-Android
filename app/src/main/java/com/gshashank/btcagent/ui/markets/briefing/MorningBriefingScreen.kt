package com.gshashank.btcagent.ui.markets.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.BriefingData
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography

/**
 * Entry-point composable for the Morning Briefing screen — MOBILE-9.
 *
 * Collects [MorningBriefingViewModel.uiState] reactively and delegates rendering to
 * [MorningBriefingScreenContent].
 */
@Composable
fun MorningBriefingScreen(
    vm: MorningBriefingViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    MorningBriefingScreenContent(
        uiState = uiState,
        onRetry = vm::retry,
    )
}

/**
 * Stateless content composable — test seam for Morning Briefing screen tests.
 *
 * Maps [UiState<BriefingData>] to the appropriate UI:
 *  - Loading  → loading box (testTag "briefing_loading")
 *  - Empty    → empty state message (testTag "briefing_empty")
 *  - Error    → error message + retry button (testTag "briefing_error")
 *  - Offline  → offline banner (testTag "briefing_offline")
 *  - Ready    → timestamp hero + markdown body (testTag "briefing_ready")
 */
@Composable
fun MorningBriefingScreenContent(
    uiState: UiState<BriefingData>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("briefing_loading"),
                )
            }

            is UiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("briefing_empty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "No briefing generated yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = onRetry) {
                        Text("Refresh")
                    }
                }
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("briefing_error"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = uiState.message)
                    TextButton(onClick = onRetry) {
                        Text("Try again")
                    }
                }
            }

            is UiState.Offline -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("briefing_offline"),
                ) {
                    OfflineBanner(lastUpdatedMs = uiState.lastUpdatedMs)
                }
            }

            is UiState.Ready -> {
                BriefingReadyContent(
                    data = uiState.data,
                    modifier = Modifier.testTag("briefing_ready"),
                )
            }
        }
    }
}

/**
 * The ready-state content: timestamp hero + markdown body.
 */
@Composable
private fun BriefingReadyContent(
    data: BriefingData,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    // Build markdown colors from MaterialTheme — avoids the required-param trap in 0.32.0.
    val mdColors = remember(colorScheme) {
        DefaultMarkdownColors(
            text = colorScheme.onSurface,
            codeText = colorScheme.onSurfaceVariant,
            inlineCodeText = colorScheme.onSurfaceVariant,
            linkText = colorScheme.primary,
            codeBackground = colorScheme.surfaceVariant,
            inlineCodeBackground = colorScheme.surfaceVariant,
            dividerColor = colorScheme.outlineVariant,
            tableText = colorScheme.onSurface,
            tableBackground = colorScheme.surfaceVariant,
        )
    }

    // Build markdown typography from MaterialTheme.
    val mdTypography = remember(typography) {
        DefaultMarkdownTypography(
            h1 = typography.headlineLarge,
            h2 = typography.headlineMedium,
            h3 = typography.headlineSmall,
            h4 = typography.titleLarge,
            h5 = typography.titleMedium,
            h6 = typography.titleSmall,
            text = typography.bodyMedium,
            code = typography.bodySmall,
            inlineCode = typography.bodySmall,
            quote = typography.bodyMedium,
            paragraph = typography.bodyMedium,
            ordered = typography.bodyMedium,
            bullet = typography.bodyMedium,
            list = typography.bodyMedium,
            link = typography.bodyMedium,
            textLink = TextLinkStyles(SpanStyle(color = colorScheme.primary)),
            table = typography.bodySmall,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Timestamp hero — show relative time if timestampMs is available.
        if (data.timestampMs != null) {
            Text(
                text = formatRelativeTime(
                    epochMs = data.timestampMs,
                    nowMs = System.currentTimeMillis(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Render the freeform Markdown text from the API.
        Markdown(
            content = data.markdown,
            colors = mdColors,
            typography = mdTypography,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
