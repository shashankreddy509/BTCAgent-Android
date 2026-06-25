package com.gshashank.btcagent.ui.markets.regime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.LiveRegime
import com.gshashank.btcagent.data.model.Regime
import com.gshashank.btcagent.data.model.RegimeDay
import com.gshashank.btcagent.ui.components.state.HeroSkeleton
import com.gshashank.btcagent.ui.components.state.StatTileSkeleton
import com.gshashank.btcagent.ui.theme.BtcDown
import com.gshashank.btcagent.ui.theme.BtcUp

/**
 * Maps a [Regime] to the appropriate theme color token.
 *
 * Bull  → [BtcUp]   (green)
 * Bear  → [BtcDown] (red)
 * Sideways → muted surfaceVariant color (neutral)
 * Unknown  → surfaceVariant (fallback)
 */
@Composable
fun regimeColor(regime: Regime): Color = when (regime) {
    Regime.BULL -> BtcUp
    Regime.BEAR -> BtcDown
    Regime.SIDEWAYS -> MaterialTheme.colorScheme.onSurfaceVariant
    Regime.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * Hero card showing the live regime label and conviction score.
 *
 * Displays: regime label (BULL/BEAR/SIDEWAYS) in [regimeColor], conviction formatted as
 * "+0.70" / "-0.30", and an explanatory label.
 * When live is null, regime is UNKNOWN, or hasError is true → shows "Regime unavailable".
 *
 * testTag: "regime_hero"
 */
@Composable
fun RegimeHero(
    live: LiveRegime?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("regime_hero")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val safeLive = live?.takeUnless { it.regime == Regime.UNKNOWN || it.hasError }
        if (safeLive == null) {
            Text(
                text = "Regime unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = safeLive.regime.displayName,
                style = MaterialTheme.typography.displaySmall,
                color = regimeColor(safeLive.regime),
            )
            if (safeLive.conviction != null) {
                val convictionText = if (safeLive.conviction >= 0.0) {
                    "+${String.format("%.2f", safeLive.conviction)}"
                } else {
                    String.format("%.2f", safeLive.conviction)
                }
                Text(
                    text = convictionText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = regimeColor(safeLive.regime),
                )
            }
            Text(
                text = "Conviction (Bull−Bear)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Stat row showing model accuracy percentage and number of graded days.
 *
 * testTag: "regime_stat_row"
 */
@Composable
fun RegimeStatRow(
    accuracyPct: Double?,
    gradedCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("regime_stat_row")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val accuracyText = if (accuracyPct != null) {
            "Model Accuracy: ${String.format("%.0f", accuracyPct * 100)}%"
        } else {
            "Model Accuracy: N/A"
        }
        Text(
            text = accuracyText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Graded: $gradedCount days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Horizontal strip of colored boxes, one per [RegimeDay].
 *
 * Each box uses [regimeColor] based on the day's regime. Intended to show the last 14 days
 * left-to-right in chronological (oldest→newest) order.
 *
 * testTag: "regime_strip"
 */
@Composable
fun RegimeStrip(
    days: List<RegimeDay>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("regime_strip")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        days.forEach { day ->
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 32.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(regimeColor(day.regime)),
            )
        }
    }
}

/**
 * Legend showing the three regime colors with labels.
 *
 * testTag: "regime_legend"
 */
@Composable
fun RegimeLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("regime_legend")
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LegendItem(color = BtcUp, label = "Bull")
        LegendItem(color = BtcDown, label = "Bear")
        LegendItem(color = MaterialTheme.colorScheme.onSurfaceVariant, label = "Sideways")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
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

/**
 * Skeleton placeholder matching the Regime screen layout shape.
 *
 * testTag: "regime_loading"
 */
@Composable
fun RegimeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("regime_loading")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroSkeleton()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTileSkeleton(modifier = Modifier.weight(1f))
            StatTileSkeleton(modifier = Modifier.weight(1f))
        }
        // Strip skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
