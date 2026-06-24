package com.gshashank.btcagent.ui.positions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Entry-point composable for the Positions list screen — MOBILE-6.
 *
 * Delegates to [PositionsListScreenContent] so tests can drive the UI directly
 * without a ViewModel or Hilt injection.
 */
@Composable
fun PositionsListScreen(
    onPositionClick: (signalId: String) -> Unit,
    viewModel: PositionsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PositionsListScreenContent(
        uiState = uiState,
        onRetry = viewModel::retry,
        onPositionClick = onPositionClick,
    )
}

/**
 * Stateless content composable — test seam for [PositionsListScreenTest].
 *
 * Maps [UiState<PositionsScreenData>] to the appropriate UI:
 *  - Loading → shimmer skeleton (testTag "positions_skeleton")
 *  - Ready   → summary cards + lazy list of position cards
 *  - Empty   → "No open positions" message (testTag "positions_empty")
 *  - Error   → retry button
 *  - Offline → offline banner
 *
 * The [when] is exhaustive with no `else` branch so adding a new [UiState] variant forces
 * an update here.
 */
@Composable
fun PositionsListScreenContent(
    uiState: UiState<PositionsScreenData>,
    onRetry: () -> Unit,
    onPositionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("positions_skeleton"),
                )
            }

            is UiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("positions_empty"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No open positions")
                }
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = uiState.message)
                    TextButton(onClick = onRetry) {
                        Text("Try again")
                    }
                }
            }

            is UiState.Offline -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    OfflineBanner(lastUpdatedMs = uiState.lastUpdatedMs)
                }
            }

            is UiState.Ready -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    PositionsSummaryCards(
                        unrealizedTotal = uiState.data.unrealizedTotal,
                        exposureTotal = uiState.data.exposureTotal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.data.positions, key = { it.signalId }) { position ->
                            PositionCard(
                                position = position,
                                onClick = { onPositionClick(position.signalId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
