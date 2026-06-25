package com.gshashank.btcagent.ui.markets.markov

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.Regime
import com.gshashank.btcagent.data.model.StationaryDist
import com.gshashank.btcagent.data.model.TickerRegime
import com.gshashank.btcagent.ui.markets.regime.regimeColor

/** Formats a 0.0–1.0 fraction as a whole-number percent, e.g. 0.42 → "42%". */
private fun formatPct(fraction: Double): String = String.format("%.0f%%", fraction * 100)

/**
 * Horizontal row of [FilterChip]s — one per ticker symbol.
 *
 * testTag per chip: "markov_ticker_chip_<ticker>"
 */
@Composable
fun TickerChipRow(
    tickers: List<String>,
    selectedTicker: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tickers.forEach { ticker ->
            FilterChip(
                selected = ticker == selectedTicker,
                onClick = { onSelect(ticker) },
                label = { Text(text = ticker) },
                modifier = Modifier.testTag("markov_ticker_chip_$ticker"),
            )
        }
    }
}

/**
 * Card showing regime classification details for a single [TickerRegime].
 *
 * Shows: ticker, regime displayName, conviction formatted as %, accuracy formatted as %.
 */
@Composable
fun RegimeCard(
    tickerRegime: TickerRegime,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = tickerRegime.ticker,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = tickerRegime.regime.displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = regimeColor(tickerRegime.regime),
        )
        val convictionText = tickerRegime.conviction?.let { formatPct(it) } ?: "—"
        Text(
            text = "Conviction: $convictionText",
            style = MaterialTheme.typography.bodyMedium,
        )
        val accuracyText = tickerRegime.accuracy?.let { formatPct(it) } ?: "—"
        Text(
            text = "Accuracy: $accuracyText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Three colored cells showing Bear / Sideways / Bull long-run probabilities.
 *
 * Uses [regimeColor] from RegimeComponents.kt for consistent coloring.
 * testTag: "markov_stationary"
 */
@Composable
fun StationaryDistRow(
    dist: StationaryDist,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("markov_stationary")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StationaryCell(
            label = "Bear",
            pct = dist.bear,
            regime = Regime.BEAR,
            modifier = Modifier.weight(1f),
        )
        StationaryCell(
            label = "Sideways",
            pct = dist.sideways,
            regime = Regime.SIDEWAYS,
            modifier = Modifier.weight(1f),
        )
        StationaryCell(
            label = "Bull",
            pct = dist.bull,
            regime = Regime.BULL,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StationaryCell(
    label: String,
    pct: Double,
    regime: Regime,
    modifier: Modifier = Modifier,
) {
    val color = regimeColor(regime)
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Text(
                text = formatPct(pct),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
    }
}

/**
 * Caption showing the dominant long-run regime.
 *
 * "Long-run: <dominant_regime> <pct>% dominant"
 * Dominant = whichever of bear/sideways/bull has the highest probability.
 */
@Composable
fun LongRunCaption(
    dist: StationaryDist,
    modifier: Modifier = Modifier,
) {
    val dominantRegime: Regime
    val dominantPct: Double
    if (dist.bull >= dist.bear && dist.bull >= dist.sideways) {
        dominantRegime = Regime.BULL
        dominantPct = dist.bull
    } else if (dist.bear >= dist.sideways) {
        dominantRegime = Regime.BEAR
        dominantPct = dist.bear
    } else {
        dominantRegime = Regime.SIDEWAYS
        dominantPct = dist.sideways
    }

    Text(
        text = "Long-run: ${dominantRegime.displayName} ${formatPct(dominantPct)} dominant",
        style = MaterialTheme.typography.bodySmall,
        color = regimeColor(dominantRegime),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
