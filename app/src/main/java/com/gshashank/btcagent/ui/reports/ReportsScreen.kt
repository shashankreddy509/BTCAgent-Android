package com.gshashank.btcagent.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.ClosedTrade
import com.gshashank.btcagent.data.model.ReportsData
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp

/**
 * Entry-point composable for the Reports screen — MOBILE-7.
 *
 * Collects [ReportsViewModel.uiState] reactively and delegates rendering to
 * [ReportsScreenContent].
 */
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReportsScreenContent(
        uiState = uiState,
        onRetry = viewModel::retry,
    )
}

/**
 * Stateless content composable — test seam for [ReportsScreenTest].
 *
 * Maps [UiState<ReportsData>] to the appropriate UI:
 *  - Loading → shimmer skeleton (testTag "reports_skeleton")
 *  - Ready   → stats row + lazy list of closed-trade rows
 *  - Empty   → empty state message (testTag "reports_empty")
 *  - Error   → error message + retry button
 *  - Offline → offline banner
 *
 * The [when] is exhaustive with no `else` branch so adding a new [UiState] variant forces
 * an update here.
 */
@Composable
fun ReportsScreenContent(
    uiState: UiState<ReportsData>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reports_skeleton"),
                )
            }

            is UiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reports_empty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No closed trades yet")
                    // Refresh affordance: a transient zero-result should be retryable manually.
                    TextButton(onClick = onRetry) {
                        Text("Refresh")
                    }
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
                ReportsContent(data = uiState.data)
            }
        }
    }
}

/**
 * The ready-state content: stats row + closed-trades table + footer.
 */
@Composable
private fun ReportsContent(data: ReportsData) {
    Column(modifier = Modifier.fillMaxSize()) {
        ReportsStatsRow(
            signalsToday = data.signalsToday,
            winRatePct = data.winRatePct,
            weekPnl = data.weekPnl,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(data.trades) { index, trade ->
                ClosedTradeRow(
                    trade = trade,
                    modifier = Modifier.testTag("reports_trade_row_$index"),
                )
            }
            item {
                Text(
                    // Backend caps history at 25 rows; show the actual count, never a fixed claim.
                    text = "Showing ${data.trades.size} most recent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Three stat tiles: Signals Today, Win Rate %, Week P&L.
 *
 * Test seams:
 *   - [testTag("reports_stat_signals")] on the Signals Today tile.
 *   - [testTag("reports_stat_winrate")] on the Win Rate tile.
 *   - [testTag("reports_stat_weekpnl")] on the Week P&L tile.
 */
@Composable
private fun ReportsStatsRow(
    signalsToday: Int,
    winRatePct: Double,
    weekPnl: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            label = "Signals Today",
            value = signalsToday.toString(),
            modifier = Modifier
                .weight(1f)
                .testTag("reports_stat_signals"),
        )
        StatTile(
            label = "Win Rate",
            value = "${"%.1f".format(winRatePct)}%",
            modifier = Modifier
                .weight(1f)
                .testTag("reports_stat_winrate"),
        )
        val pnlSign = if (weekPnl >= 0.0) "+" else ""
        val pnlColor = if (weekPnl > 0.0) BtcPriceUp else BtcPriceDown
        StatTile(
            label = "Week P&L",
            value = "$pnlSign${"%.2f".format(weekPnl)}",
            valueColor = pnlColor,
            modifier = Modifier
                .weight(1f)
                .testTag("reports_stat_weekpnl"),
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
            )
        }
    }
}

/**
 * A single closed-trade row in the reports table.
 *
 * P&L coloring: green ([BtcPriceUp]) if pnl > 0, red ([BtcPriceDown]) if pnl <= 0.
 */
@Composable
private fun ClosedTradeRow(
    trade: ClosedTrade,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "BTC/USDT",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                val sideText = when (trade.side) {
                    Side.Long -> "Long"
                    Side.Short -> "Short"
                }
                val sideColor = when (trade.side) {
                    Side.Long -> BtcPriceUp
                    Side.Short -> BtcPriceDown
                }
                Text(
                    text = sideText,
                    style = MaterialTheme.typography.labelMedium,
                    color = sideColor,
                )
            }

            val pnlColor = if (trade.pnl > 0.0) BtcPriceUp else BtcPriceDown
            val pnlSign = if (trade.pnl > 0.0) "+" else ""
            Text(
                text = "$pnlSign${"%.2f".format(trade.pnl)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = pnlColor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Entry: \$" + "%.2f".format(trade.entryPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Exit: \$" + "%.2f".format(trade.exitPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = trade.pattern,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
