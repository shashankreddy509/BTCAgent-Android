package com.gshashank.btcagent.ui.markets.markov

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
import com.gshashank.btcagent.data.model.MarkovData
import com.gshashank.btcagent.ui.components.state.EmptyStateContent
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Entry-point composable for the Markov Matrix screen — MOBILE-13.
 *
 * Collects [MarkovMatrixViewModel.uiState] and [MarkovMatrixViewModel.selectedTicker]
 * reactively and delegates rendering to [MarkovMatrixScreenContent].
 *
 * Gating is at the tile/nav layer (MarketsHubScreen) — this screen composes unconditionally
 * once navigation reaches it.
 */
@Composable
fun MarkovMatrixScreen(
    viewModel: MarkovMatrixViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTicker by viewModel.selectedTicker.collectAsStateWithLifecycle()
    MarkovMatrixScreenContent(
        uiState = uiState,
        selectedTicker = selectedTicker,
        onSelectTicker = viewModel::onSelectTicker,
        onRetry = viewModel::retry,
    )
}

/**
 * Stateless content composable — test seam for Markov Matrix screen tests.
 *
 * Maps [UiState<MarkovData>] to the appropriate UI exhaustively:
 *  - Loading  → [HeroSkeleton] (testTag "markov_loading")
 *  - Empty    → [EmptyStateContent] (testTag "markov_empty")
 *  - Error    → error message + Retry button (testTag "markov_error")
 *  - Offline  → [OfflineBanner] + Retry button (testTag "markov_offline")
 *  - Ready    → [TickerChipRow] + selected ticker's [RegimeCard] + [StationaryDistRow]
 *               (testTag "markov_ready")
 */
@Composable
fun MarkovMatrixScreenContent(
    uiState: UiState<MarkovData>,
    selectedTicker: String?,
    onSelectTicker: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("markov_loading"),
                ) {
                    HeroSkeleton()
                }
            }

            is UiState.Empty -> {
                EmptyStateContent(
                    onRefresh = onRetry,
                    modifier = Modifier.testTag("markov_empty"),
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("markov_error"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = uiState.message)
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Offline -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("markov_offline"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OfflineBanner(lastUpdatedMs = uiState.lastUpdatedMs)
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Ready -> {
                val data = uiState.data
                val tickers = data.tickers.map { it.ticker }
                val tickerRegime = data.tickers.firstOrNull { it.ticker == selectedTicker }
                    ?: data.tickers.firstOrNull()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("markov_ready"),
                ) {
                    TickerChipRow(
                        tickers = tickers,
                        selectedTicker = selectedTicker,
                        onSelect = onSelectTicker,
                    )
                    if (tickerRegime != null) {
                        RegimeCard(tickerRegime = tickerRegime)
                        val dist = tickerRegime.stationary
                        if (dist != null) {
                            StationaryDistRow(dist = dist)
                            LongRunCaption(dist = dist)
                        }
                    }
                }
            }
        }
    }
}
