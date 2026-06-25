package com.gshashank.btcagent.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.ScanDirection
import com.gshashank.btcagent.data.model.ScanSignal
import com.gshashank.btcagent.ui.theme.BtcDown
import com.gshashank.btcagent.ui.theme.BtcUp

/**
 * A single row in the Scanner signals list — MOBILE-8.
 *
 * Displays: pattern name, timeframe, direction pill (Bullish=green/[BtcUp],
 * Bearish=red/[BtcDown], Neutral=surfaceVariant), open price, DEPO tag when
 * [ScanSignal.depoLine] is non-null, and bars-ago count.
 */
@Composable
fun ScannerSignalRow(
    signal: ScanSignal,
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
                    text = signal.pattern,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                DirectionPill(direction = signal.direction)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = signal.timeframe,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${signal.barsAgo} bar${if (signal.barsAgo != 1) "s" else ""} ago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Open: \$" + "%.2f".format(signal.openPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (signal.depoLine != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "DEPO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionPill(direction: ScanDirection) {
    val (label, color) = when (direction) {
        ScanDirection.Bullish -> "Bullish" to BtcUp
        ScanDirection.Bearish -> "Bearish" to BtcDown
        ScanDirection.Neutral -> "Neutral" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
