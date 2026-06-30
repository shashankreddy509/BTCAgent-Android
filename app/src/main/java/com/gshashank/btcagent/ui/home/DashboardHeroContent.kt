package com.gshashank.btcagent.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gshashank.btcagent.data.model.BotMode
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.PriceDirection
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.theme.BtcAccent
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp

/**
 * Hero layout — rebuilt to match `bitcoin-light/btc-ai-agent-06-dashboard-hero.png` (MOBILE-39 +
 * design-QA polish). White elevated cards, 2-column stats, status pills, app-bar logo, button icons.
 *
 * Test seam: [testTag("dashboard_price")] on the price headline text.
 */
@Composable
fun DashboardHeroContent(
    data: DashboardData,
    onPositionsClick: () -> Unit = {},
    onScannerClick: () -> Unit = {},
    onNewTradeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppBarRow(mode = data.botMode)
        BotStatusRow(running = data.botRunning, mode = data.botMode, scanIntervalMin = data.scanIntervalMin)
        PriceHeroCard(
            price = data.btcPrice,
            direction = data.priceDirection,
            brokerName = data.brokerName,
            botMode = data.botMode,
        )
        // 2-column stats: Today's P&L | Open Positions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TodayPnlCard(pnlPts = data.todayPnlPts, modifier = Modifier.weight(1f))
            OpenPositionsCard(
                count = data.openPositionCount,
                longCount = data.longCount,
                shortCount = data.shortCount,
                onClick = onPositionsClick,
                modifier = Modifier.weight(1f),
            )
        }
        if (data.positions.isNotEmpty()) {
            PositionsPreviewList(positions = data.positions.take(3), onViewAllClick = onPositionsClick)
        }
        ActionButtonsRow(onScannerClick = onScannerClick, onNewTradeClick = onNewTradeClick)
    }
}

// ---------------------------------------------------------------------------
// Shared card styling — white surface + hairline border, matching the mock.
// ---------------------------------------------------------------------------

@Composable
private fun heroCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
)

@Composable
private fun heroCardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

// ---------------------------------------------------------------------------

@Composable
private fun AppBarRow(mode: BotMode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ฿ logo tile
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BtcAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text("₿", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Everything at a glance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusPill(
            text = if (mode == BotMode.Live) "LIVE" else "PAPER",
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            bg = MaterialTheme.colorScheme.surface,
            border = true,
        )
    }
}

@Composable
private fun BotStatusRow(running: Boolean, mode: BotMode, scanIntervalMin: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ● Bot running / Stopped — green-tinted pill when running.
        val runColor = if (running) BtcPriceUp else MaterialTheme.colorScheme.onSurfaceVariant
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (running) BtcPriceUp.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("●", color = runColor, fontSize = 10.sp)
                Text(
                    text = if (running) "Bot running" else "Stopped",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = runColor,
                )
            }
        }
        StatusPill(
            text = if (mode == BotMode.Live) "LIVE" else "PAPER",
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            bg = MaterialTheme.colorScheme.surface,
            border = true,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (scanIntervalMin > 0) "Scanner ${scanIntervalMin}m" else "Scanner",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusPill(text: String, textColor: Color, bg: Color, border: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) else null,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PriceHeroCard(price: Double, direction: PriceDirection, brokerName: String, botMode: BotMode) {
    val targetColor = when (direction) {
        PriceDirection.Up -> BtcPriceUp
        PriceDirection.Down -> BtcPriceDown
        PriceDirection.Flat -> null
    }
    val animatedColor by animateColorAsState(
        targetValue = targetColor ?: MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "priceColor",
    )
    val arrow = when (direction) {
        PriceDirection.Up -> "▲"
        PriceDirection.Down -> "▼"
        PriceDirection.Flat -> "—"
    }
    val effectiveBroker = brokerName.ifBlank { "Coinbase" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = heroCardColors(),
        border = heroCardBorder(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "BTC-USD · $effectiveBroker",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // ● LIVE / PAPER indicator on the right of the caption row.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("●", color = if (botMode == BotMode.Live) BtcPriceUp else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    Text(
                        text = if (botMode == BotMode.Live) "LIVE" else "PAPER",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (botMode == BotMode.Live) BtcPriceUp else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\$" + "%,.2f".format(price),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor,
                    modifier = Modifier.testTag("dashboard_price"),
                )
                Text(text = arrow, style = MaterialTheme.typography.titleMedium, color = animatedColor)
            }
            // 24h change + sparkline intentionally absent (BTCWEB-52 / dropped).
        }
    }
}

@Composable
private fun TodayPnlCard(pnlPts: Double, modifier: Modifier = Modifier) {
    val isPositive = pnlPts >= 0.0
    val pnlColor = if (isPositive) BtcPriceUp else BtcPriceDown
    val sign = if (isPositive) "+" else ""
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = heroCardColors(),
        border = heroCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TODAY'S P&L",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$sign${"%.2f".format(pnlPts)} pts",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = pnlColor,
            )
        }
    }
}

@Composable
private fun OpenPositionsCard(
    count: Int,
    longCount: Int,
    shortCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = heroCardColors(),
        border = heroCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "OPEN POSITIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "$longCount long · $shortCount short",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PositionsPreviewList(positions: List<Position>, onViewAllClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Open positions",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            TextButton(onClick = onViewAllClick) {
                Text(text = "View all ›", color = BtcAccent)
            }
        }
        positions.forEach { position ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = heroCardColors(),
                border = heroCardBorder(),
            ) {
                PositionPreviewRow(position = position)
            }
        }
    }
}

@Composable
private fun PositionPreviewRow(position: Position) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "BTC-PERP", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                SidePill(side = position.side)
            }
            Text(
                text = "\$" + "%,.0f".format(position.entryPrice) + " → \$" + "%,.0f".format(position.currentPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val pnlColor = if (position.pnl >= 0.0) BtcPriceUp else BtcPriceDown
            val sign = if (position.pnl >= 0.0) "+" else ""
            Text(
                text = "$sign\$" + "%,.2f".format(position.pnl),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = pnlColor,
            )
            Text(
                text = "%+.2f%%".format(position.pnlPct),
                style = MaterialTheme.typography.bodySmall,
                color = pnlColor,
            )
        }
    }
}

@Composable
private fun SidePill(side: Side) {
    val isLong = side == Side.Long
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isLong) BtcPriceUp.copy(alpha = 0.15f) else BtcPriceDown.copy(alpha = 0.15f),
    ) {
        Text(
            text = if (isLong) "Long" else "Short",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLong) BtcPriceUp else BtcPriceDown,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ActionButtonsRow(onScannerClick: () -> Unit, onNewTradeClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onScannerClick,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = "Scanner")
        }
        Button(
            onClick = onNewTradeClick,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtcAccent, contentColor = Color.Black),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = "New trade", fontWeight = FontWeight.SemiBold)
        }
    }
}
