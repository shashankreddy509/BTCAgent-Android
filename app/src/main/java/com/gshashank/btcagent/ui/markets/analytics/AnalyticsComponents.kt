package com.gshashank.btcagent.ui.markets.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.AnalyticsData
import com.gshashank.btcagent.data.model.PatternMetrics
import com.gshashank.btcagent.data.model.TradeMetrics
import com.gshashank.btcagent.ui.components.state.EmptyStateContent
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.markets.oi.Sparkline
import com.gshashank.btcagent.ui.theme.BtcUp

/**
 * Stateless content composable — test seam for Analytics screen.
 *
 * Maps [UiState<AnalyticsData>] to the appropriate UI:
 *  - Loading  → [HeroSkeleton] (testTag "analytics_loading")
 *  - Empty    → [EmptyStateContent] (testTag "analytics_empty")
 *  - Error    → error message + retry button (testTag "analytics_error")
 *  - Offline  → [OfflineBanner] + retry button (testTag "analytics_offline")
 *  - Ready    → analytics content (testTag "analytics_ready")
 */
@Composable
fun AnalyticsScreenContent(
    uiState: UiState<AnalyticsData>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("analytics_loading"),
                ) {
                    HeroSkeleton()
                }
            }

            is UiState.Empty -> {
                EmptyStateContent(
                    onRefresh = onRetry,
                    modifier = Modifier.testTag("analytics_empty"),
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("analytics_error"),
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
                        .testTag("analytics_offline"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OfflineBanner(lastUpdatedMs = uiState.lastUpdatedMs)
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Ready -> {
                AnalyticsReadyContent(
                    data = uiState.data,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("analytics_ready"),
                )
            }
        }
    }
}

@Composable
private fun AnalyticsReadyContent(
    data: AnalyticsData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Equity curve card
        EquityCurveCard(equityCurve = data.equityCurve)

        // 2. 3×2 metric grid
        MetricGrid(metrics = data.metrics)

        // 3. Win-rate by pattern
        if (data.byPattern.isNotEmpty()) {
            PatternBreakdown(patterns = data.byPattern)
        }
    }
}

@Composable
private fun EquityCurveCard(
    equityCurve: List<Double>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "30-day equity (points/PnL)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Sparkline(
                points = equityCurve,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            )
        }
    }
}

@Composable
private fun MetricGrid(
    metrics: TradeMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                label = "Win rate",
                value = "%.1f%%".format(metrics.winRatePct),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Expectancy",
                value = "%.1f pts".format(metrics.expectancy),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Max drawdown",
                value = "%.1f pts".format(metrics.maxDrawdown),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                label = "Profit factor",
                value = metrics.profitFactor?.let { "%.2f".format(it) } ?: "∞",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Avg win",
                value = "%.1f pts".format(metrics.avgWin),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Avg loss",
                value = "%.1f pts".format(metrics.avgLoss),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PatternBreakdown(
    patterns: List<PatternMetrics>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Win rate by pattern",
            style = MaterialTheme.typography.titleSmall,
        )
        patterns.forEach { pm ->
            PatternBar(pm = pm)
        }
    }
}

@Composable
private fun PatternBar(
    pm: PatternMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = pm.pattern,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "%.1f%%".format(pm.metrics.winRatePct),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Horizontal bar: width fraction = winRatePct / 100
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ),
        ) {
            val fraction = (pm.metrics.winRatePct / 100.0).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(
                        color = BtcUp,
                        shape = MaterialTheme.shapes.small,
                    ),
            )
        }
    }
}
