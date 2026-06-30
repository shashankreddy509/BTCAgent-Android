package com.gshashank.btcagent.ui.positions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.theme.BtcAccent
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp

/**
 * Card displaying a single open position's key details — MOBILE-6, MOBILE-43.
 *
 * Rebuilt to match bitcoin-light/btc-ai-agent-07-positions.png:
 * - White card with 1dp hairline border
 * - Left: icon tile + "BTC-PERP" title + side pill + pattern·tf subtitle
 * - Right: P&L "$+X.XX" (green/red) + pnlPct below
 * - Bottom: 3-column ENTRY / CURRENT / SIZE (uppercase labels)
 * - NO leverage chip (dropped — out of scope)
 *
 * Test seam: [testTag("position_card_{signalId}")] on the card root.
 */
@Composable
fun PositionCard(
    position: Position,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pnlColor = if (position.pnl >= 0.0) BtcPriceUp else BtcPriceDown
    val pnlSign = if (position.pnl >= 0.0) "+" else ""
    val sideIsLong = position.side == Side.Long

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("position_card_${position.signalId}")
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Top row: icon + info | P&L ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon tile — "B" on accent background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BtcAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "B",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BtcAccent,
                    )
                }

                // Symbol + side pill + pattern·tf subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "BTC-PERP",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        SidePill(isLong = sideIsLong)
                    }
                    // Pattern · timeframe subtitle — only when pattern is present
                    if (position.pattern != null) {
                        val tfLabel = formatTfMinutes(position.tf)
                        val subtitle = if (tfLabel.isNotEmpty()) "${position.pattern} · $tfLabel"
                                       else position.pattern
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // P&L column — right-aligned
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$pnlSign\$${String.format("%,.2f", position.pnl)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = pnlColor,
                    )
                    Text(
                        text = "$pnlSign${"%.2f".format(position.pnlPct)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = pnlColor,
                    )
                }
            }

            // ── 3-column detail row: ENTRY / CURRENT / SIZE ────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailColumn(label = "ENTRY",   value = "\$${String.format("%,.2f", position.entryPrice)}")
                DetailColumn(label = "CURRENT", value = "\$${String.format("%,.2f", position.currentPrice)}")
                DetailColumn(label = "SIZE",    value = "${"%.3f".format(position.qty)} BTC",
                             alignment = Alignment.End)
            }
        }
    }
}

@Composable
private fun SidePill(isLong: Boolean) {
    val bgColor = if (isLong) BtcPriceUp.copy(alpha = 0.12f) else BtcPriceDown.copy(alpha = 0.12f)
    val textColor = if (isLong) BtcPriceUp else BtcPriceDown
    val label = if (isLong) "Long" else "Short"
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun DetailColumn(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        )
    }
}
