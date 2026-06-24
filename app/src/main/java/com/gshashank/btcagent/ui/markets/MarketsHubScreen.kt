package com.gshashank.btcagent.ui.markets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.ui.navigation.MarketsRoute

/**
 * Markets hub: regime banner + 2-col grid of analytics tiles.
 *
 * Stateless re: navigation — takes an [onTileClick] callback instead of a NavController so the
 * screen stays decoupled from the nav graph and is straightforward to preview/test. A
 * MarketsHubViewModel slot is intentionally omitted until MOBILE-24 wires real regime data; add
 * it back (and observe its uiState) at that point rather than carrying a dead parameter now.
 */
@Composable
fun MarketsHubScreen(
    onTileClick: (MarketsRoute) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_markets"),
    ) {
        RegimeBanner()
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AnalyticsTile(
                    icon = "📈",
                    label = "Open Interest",
                    testTag = "tile_open_interest",
                    onClick = { onTileClick(MarketsRoute.OpenInterest) },
                )
            }
            item {
                AnalyticsTile(
                    icon = "🪙",
                    label = "BTC Regime",
                    testTag = "tile_btc_regime",
                    onClick = { onTileClick(MarketsRoute.BtcRegime) },
                )
            }
            item {
                AnalyticsTile(
                    icon = "🔢",
                    label = "Markov Matrix",
                    testTag = "tile_markov_matrix",
                    onClick = { onTileClick(MarketsRoute.MarkovMatrix) },
                )
            }
            item {
                AnalyticsTile(
                    icon = "📊",
                    label = "Volume Profile",
                    testTag = "tile_volume_profile",
                    onClick = { onTileClick(MarketsRoute.VolumeProfile) },
                )
            }
            item {
                AnalyticsTile(
                    icon = "💧",
                    label = "Liquidity Map",
                    testTag = "tile_liquidity_map",
                    onClick = { onTileClick(MarketsRoute.LiquidityMap) },
                )
            }
            item {
                AnalyticsTile(
                    icon = "🗺️",
                    label = "Zone Strategies",
                    testTag = "tile_zone_strategies",
                    onClick = { onTileClick(MarketsRoute.ZoneStrategies) },
                )
            }
            item {
                AnalyticsTile(
                    icon = "🔬",
                    label = "Analytics",
                    testTag = "tile_analytics",
                    onClick = { onTileClick(MarketsRoute.Analytics) },
                )
            }
        }
    }
}

/**
 * Regime banner at the top of the Markets hub.
 * TODO MOBILE-24: replace static placeholder with live regime data.
 */
@Composable
private fun RegimeBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("regime_banner"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "-- Regime",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "Regime data unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnalyticsTile(
    icon: String,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag(testTag)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = icon, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
