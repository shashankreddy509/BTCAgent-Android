package com.gshashank.btcagent.ui.markets.regime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.RegimeData
import com.gshashank.btcagent.ui.components.state.EmptyStateContent
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Entry-point composable for the BTC Regime screen — MOBILE-12.
 *
 * Collects [BtcRegimeViewModel.uiState] reactively and delegates rendering to
 * [BtcRegimeScreenContent].
 */
@Composable
fun BtcRegimeScreen(
    vm: BtcRegimeViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    BtcRegimeScreenContent(
        uiState = uiState,
        onRetry = vm::retry,
    )
}

/**
 * Stateless content composable — test seam for BTC Regime screen tests.
 *
 * Maps [UiState<RegimeData>] to the appropriate UI:
 *  - Loading  → [RegimeSkeleton] (testTag "regime_loading")
 *  - Empty    → [EmptyStateContent] (testTag "regime_empty")
 *  - Error    → error message + retry button (testTag "regime_error")
 *  - Offline  → [OfflineBanner] + error content (testTag "regime_offline")
 *  - Ready    → [RegimeHero] + [RegimeStatRow] + [RegimeStrip] + [RegimeLegend] (testTag "regime_ready")
 */
@Composable
fun BtcRegimeScreenContent(
    uiState: UiState<RegimeData>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                // RegimeSkeleton owns the "regime_loading" testTag internally.
                RegimeSkeleton()
            }

            is UiState.Empty -> {
                EmptyStateContent(
                    onRefresh = onRetry,
                    modifier = Modifier.testTag("regime_empty"),
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("regime_error"),
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
                        .testTag("regime_offline"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OfflineBanner(lastUpdatedMs = uiState.lastUpdatedMs)
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("regime_ready"),
                ) {
                    RegimeHero(live = uiState.data.live)
                    RegimeStatRow(
                        accuracyPct = uiState.data.accuracyPct,
                        gradedCount = uiState.data.gradedCount,
                    )
                    RegimeStrip(days = uiState.data.days)
                    RegimeLegend()
                }
            }
        }
    }
}
