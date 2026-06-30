package com.gshashank.btcagent.ui.positions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Two summary cards shown at the top of the Positions list — MOBILE-6, MOBILE-43.
 *
 * Styled to the Claude Design mock (bitcoin-light/btc-ai-agent-07-positions.png):
 * white cards with 1dp hairline border, uppercase labels.
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
        // UNREALIZED card — white with hairline border, green/red value
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("positions_summary_unrealized"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "UNREALIZED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val sign = if (unrealizedTotal >= 0.0) "+" else ""
                Text(
                    text = "$sign\$${"%.2f".format(unrealizedTotal)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (unrealizedTotal >= 0.0) BtcPriceUp else BtcPriceDown,
                )
            }
        }

        // EXPOSURE card — white with hairline border
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("positions_summary_exposure"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "EXPOSURE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\$${String.format("%,.0f", exposureTotal)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
