package com.gshashank.btcagent.ui.markets.liquidity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Entry-point composable for the Liquidity Map screen — MOBILE-15.
 *
 * Collects [LiquidityMapViewModel.uiState] reactively and delegates rendering to
 * [LiquidityMapScreenContent].
 *
 * Gating is at the tile/nav layer (MarketsHubScreen) — this screen composes unconditionally
 * once navigation reaches it.
 */
@Composable
fun LiquidityMapScreen(
    viewModel: LiquidityMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LiquidityMapScreenContent(uiState = uiState, onRetry = viewModel::retry)
}
