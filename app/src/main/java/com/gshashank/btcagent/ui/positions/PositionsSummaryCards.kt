package com.gshashank.btcagent.ui.positions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp

/**
 * Two summary cards shown at the top of the Positions list — MOBILE-6.
 *
 * Test seams:
 *   - [testTag("positions_summary_unrealized")] on the Unrealized P&L card.
 *   - [testTag("positions_summary_exposure")]   on the Total Exposure card.
 */
@Composable
fun PositionsSummaryCards(
    unrealizedTotal: Double,
    exposureTotal: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Unrealized P&L card
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("positions_summary_unrealized"),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Unrealized P&L",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val sign = if (unrealizedTotal >= 0.0) "+" else ""
                Text(
                    text = "$sign${"%.2f".format(unrealizedTotal)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (unrealizedTotal >= 0.0) BtcPriceUp else BtcPriceDown,
                )
            }
        }

        // Total Exposure card
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("positions_summary_exposure"),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Total Exposure",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\$" + "%.2f".format(exposureTotal),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
