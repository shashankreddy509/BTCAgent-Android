package com.gshashank.btcagent.ui.markets.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.theme.BTCAgentTheme

/**
 * Entry-point composable for the Analytics screen — MOBILE-17.
 *
 * Collects [AnalyticsViewModel.uiState] reactively and delegates rendering to
 * [AnalyticsScreenContent].
 *
 * Gating is at the tile/nav layer (MarketsHubScreen) — this screen composes unconditionally
 * once navigation reaches it.
 */
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsScreenContent(uiState = uiState, onRetry = viewModel::retry)
}

@Preview
@Composable
private fun AnalyticsScreenPreview() {
    BTCAgentTheme { AnalyticsScreenContent(uiState = UiState.Loading, onRetry = {}) }
}
