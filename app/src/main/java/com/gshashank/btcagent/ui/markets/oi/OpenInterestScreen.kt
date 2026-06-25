package com.gshashank.btcagent.ui.markets.oi

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
import com.gshashank.btcagent.data.model.OpenInterestData
import com.gshashank.btcagent.ui.components.state.EmptyStateContent
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Entry-point composable for the Open Interest screen — MOBILE-11.
 *
 * Collects [OpenInterestViewModel.uiState] reactively and delegates rendering to
 * [OpenInterestScreenContent].
 *
 * No catalog flag — screen mounts unconditionally (PLAN.md: "Catalog flag: NONE").
 */
@Composable
fun OpenInterestScreen(
    vm: OpenInterestViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    OpenInterestScreenContent(
        uiState = uiState,
        onRetry = vm::retry,
    )
}

/**
 * Stateless content composable — test seam for Open Interest screen tests.
 *
 * Maps [UiState<OpenInterestData>] to the appropriate UI:
 *  - Loading  → [HeroSkeleton] (testTag "oi_loading")
 *  - Empty    → [EmptyStateContent] (testTag "oi_empty")
 *  - Error    → error message + retry button (testTag "oi_error")
 *  - Offline  → [OfflineBanner] + retry button (testTag "oi_offline")
 *  - Ready    → [OiValueCard] + [OiSignalReadout] (testTag "oi_ready")
 */
@Composable
fun OpenInterestScreenContent(
    uiState: UiState<OpenInterestData>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("oi_loading"),
                ) {
                    HeroSkeleton()
                }
            }

            is UiState.Empty -> {
                EmptyStateContent(
                    onRefresh = onRetry,
                    modifier = Modifier.testTag("oi_empty"),
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("oi_error"),
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
                        .testTag("oi_offline"),
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
                        .testTag("oi_ready"),
                ) {
                    OiValueCard(
                        oiDelta = uiState.data.oiDelta,
                        sparkline = uiState.data.sparkline,
                    )
                    OiSignalReadout(data = uiState.data)
                }
            }
        }
    }
}
