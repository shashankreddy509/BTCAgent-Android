package com.gshashank.btcagent.ui.markets.volume_profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Entry-point composable for the Volume Profile screen — MOBILE-14.
 *
 * Collects [VolumeProfileViewModel.uiState] and [VolumeProfileViewModel.selectedTimeframe]
 * reactively and delegates rendering to [VolumeProfileScreenContent].
 *
 * Gating is at the tile/nav layer (MarketsHubScreen) — this screen composes unconditionally
 * once navigation reaches it.
 */
@Composable
fun VolumeProfileScreen(viewModel: VolumeProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    VolumeProfileScreenContent(
        uiState = uiState,
        selectedTimeframe = selectedTimeframe,
        onSelectTimeframe = viewModel::onSelectTimeframe,
        onRetry = viewModel::retry,
    )
}
