package com.gshashank.btcagent.ui.markets.liquidity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.HeatTier
import com.gshashank.btcagent.data.model.LiquidityLevel
import com.gshashank.btcagent.data.model.LiquidityMapData
import com.gshashank.btcagent.ui.components.state.EmptyStateContent
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.theme.BtcAccent
import com.gshashank.btcagent.ui.theme.BtcDown

/**
 * Stateless content composable — test seam for Liquidity Map screen.
 *
 * Maps [UiState<LiquidityMapData>] to the appropriate UI exhaustively:
 *  - Loading  → [HeroSkeleton] (testTag "lm_loading")
 *  - Empty    → [EmptyStateContent] (testTag "lm_empty")
 *  - Error    → error message + Retry button (testTag "lm_error")
 *  - Offline  → offline message (testTag "lm_offline")
 *  - Ready    → [LiquidityHeatmap] (testTag "lm_ready")
 */
@Composable
fun LiquidityMapScreenContent(
    uiState: UiState<LiquidityMapData>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("lm_loading"),
                ) {
                    HeroSkeleton()
                }
            }

            is UiState.Empty -> {
                EmptyStateContent(
                    onRefresh = onRetry,
                    modifier = Modifier.testTag("lm_empty"),
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("lm_error"),
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
                        .testTag("lm_offline"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "You are offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("lm_ready"),
                ) {
                    LiquidityLegend()
                    LiquidityHeatmap(data = uiState.data)
                    uiState.data.lastUpdated?.let { ts ->
                        Text(
                            text = "last updated $ts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * LazyColumn of colored cell-rows, one per [LiquidityLevel].
 *
 * Levels are assumed to be price-desc sorted (sorted by the domain mapper).
 * Cell background alpha = intensity = (notional / maxNotional).coerceIn(0.15f, 1f).
 * Guard: if maxNotional == 0.0 use 0.15f flat.
 */
@Composable
fun LiquidityHeatmap(
    data: LiquidityMapData,
    modifier: Modifier = Modifier,
) {
    val maxNotional = data.maxNotional
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(data.levels) { level ->
            LiquidityCell(level = level, maxNotional = maxNotional)
        }
    }
}

@Composable
private fun LiquidityCell(
    level: LiquidityLevel,
    maxNotional: Double,
) {
    val intensity = if (maxNotional == 0.0) {
        0.15f
    } else {
        (level.notional / maxNotional).toFloat().coerceIn(0.15f, 1f)
    }
    val color = tierColor(level.tier)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = intensity)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "%.1f".format(level.price),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatNotional(level.notional),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Maps a [HeatTier] to its liquidation-intensity color (CoinGlass heat scale).
 *
 * HOT  → [BtcDown]  (red — dense liquidation zone)
 * WARM → [BtcAccent] (orange — moderate)
 * COOL → onSurfaceVariant (muted — sparse)
 */
@Composable
internal fun tierColor(tier: HeatTier): Color = when (tier) {
    HeatTier.HOT -> BtcDown
    HeatTier.WARM -> BtcAccent
    HeatTier.COOL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Legend showing the liquidation-intensity heat tiers (hot = dense zone). */
@Composable
private fun LiquidityLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LiquidityLegendItem(color = tierColor(HeatTier.HOT), label = "Hot")
        LiquidityLegendItem(color = tierColor(HeatTier.WARM), label = "Warm")
        LiquidityLegendItem(color = tierColor(HeatTier.COOL), label = "Cool")
    }
}

@Composable
private fun LiquidityLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Formats a notional value back to a human-readable K/M/B string. */
private fun formatNotional(value: Double): String = when {
    value >= 1_000_000_000.0 -> "${"%.1f".format(value / 1_000_000_000.0)}B"
    value >= 1_000_000.0 -> "${"%.1f".format(value / 1_000_000.0)}M"
    value >= 1_000.0 -> "${"%.1f".format(value / 1_000.0)}K"
    else -> "%.1f".format(value)
}
