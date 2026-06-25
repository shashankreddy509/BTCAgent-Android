package com.gshashank.btcagent.ui.positions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp

/**
 * Card displaying a single open position's key details — MOBILE-6.
 *
 * Test seam: [testTag("position_card_{signalId}")] on the card root.
 */
@Composable
fun PositionCard(
    position: Position,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("position_card_${position.signalId}")
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "BTC/USDT",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                val sideText = when (position.side) {
                    Side.Long -> "Long"
                    Side.Short -> "Short"
                }
                val sideColor = when (position.side) {
                    Side.Long -> BtcPriceUp
                    Side.Short -> BtcPriceDown
                }
                Text(
                    text = sideText,
                    style = MaterialTheme.typography.labelMedium,
                    color = sideColor,
                )
            }

            val pnlColor = if (position.pnl >= 0.0) BtcPriceUp else BtcPriceDown
            val pnlSign = if (position.pnl >= 0.0) "+" else ""
            Text(
                text = "$pnlSign${"%.2f".format(position.pnl)} ($pnlSign${"%.2f".format(position.pnlPct)}%)",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = pnlColor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Entry: \$" + "%.2f".format(position.entryPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Current: \$" + "%.2f".format(position.currentPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Size: ${"%.4f".format(position.qty)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
