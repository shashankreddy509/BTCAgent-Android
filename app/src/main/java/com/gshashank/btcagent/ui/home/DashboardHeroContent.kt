package com.gshashank.btcagent.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.BotMode
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.PriceDirection
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp

/**
 * Hero layout showing live BTC price, today's P&L, open positions, positions preview, and
 * action buttons. Rebuilt to match `bitcoin-light/btc-ai-agent-06-dashboard-hero.png` — MOBILE-39.
 *
 * Sections:
 *  A. Top app-bar row: "Dashboard" title + subtitle + PAPER/LIVE pill.
 *  B. Bot-status row: ● running/stopped + mode + Scanner interval.
 *  C. Price hero Card: BTC-USD · broker · mode caption + big live price (directional color + tick).
 *  D. Today's P&L Card: label + value with sign and directional color.
 *  E. Open Positions Card: count + long/short breakdown + unrealised P&L. Tapping → positions list.
 *  F. Positions preview list: up to 3 rows + "View all ›" link.
 *  G. Action buttons: "Scanner" (outlined) + "New trade" (orange filled).
 *
 * Test seam: [testTag("dashboard_price")] on the price headline text.
 *
 * @param onPositionsClick Called when the user taps the Open Positions card or "View all ›".
 * @param onScannerClick Called when the user taps the "Scanner" action button.
 * @param onNewTradeClick Called when the user taps the "New trade" action button.
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
        // A. Top app-bar row
        AppBarRow(mode = data.botMode)

        // B. Bot-status row
        BotStatusRow(
            running = data.botRunning,
            mode = data.botMode,
            scanIntervalMin = data.scanIntervalMin,
        )

        // C. Price hero card
        PriceHeroCard(
            price = data.btcPrice,
            direction = data.priceDirection,
            brokerName = data.brokerName,
            botMode = data.botMode,
        )

        // D. Today's P&L card
        TodayPnlCard(pnlPts = data.todayPnlPts)

        // E. Open Positions card
        OpenPositionsCard(
            count = data.openPositionCount,
            longCount = data.longCount,
            shortCount = data.shortCount,
            unrealisedPnl = data.openUnrealisedPnl,
            onPositionsClick = onPositionsClick,
        )

        // F. Positions preview list (up to 3 rows)
        if (data.positions.isNotEmpty()) {
            PositionsPreviewList(
                positions = data.positions.take(3),
                onViewAllClick = onPositionsClick,
            )
        }

        // G. Action buttons row
        ActionButtonsRow(
            onScannerClick = onScannerClick,
            onNewTradeClick = onNewTradeClick,
        )
    }
}

@Composable
private fun AppBarRow(mode: BotMode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Everything at a glance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ModePill(mode = mode)
    }
}

@Composable
private fun ModePill(mode: BotMode) {
    val isLive = mode == BotMode.Live
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isLive) Color(0xFFFF6D00) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = if (isLive) "LIVE" else "PAPER",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
        // Colored dot indicator
        Text(
            text = "●",
            style = MaterialTheme.typography.bodyMedium,
            color = if (running) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (running) "Running" else "Stopped",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (mode == BotMode.Live) "live mode" else "paper mode",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (scanIntervalMin > 0) "Scanner ${scanIntervalMin}m" else "Scanner",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PriceHeroCard(
    price: Double,
    direction: PriceDirection,
    brokerName: String,
    botMode: BotMode,
) {
    val targetColor = when (direction) {
        PriceDirection.Up -> BtcPriceUp
        PriceDirection.Down -> BtcPriceDown
        PriceDirection.Flat -> null // fallback to onSurface
    }
    val surfaceColor = MaterialTheme.colorScheme.onSurface
    val animatedColor by animateColorAsState(
        targetValue = targetColor ?: surfaceColor,
        animationSpec = tween(durationMillis = 300),
        label = "priceColor",
    )
    val arrow = when (direction) {
        PriceDirection.Up -> "▲"
        PriceDirection.Down -> "▼"
        PriceDirection.Flat -> "—"
    }

    val effectiveBroker = brokerName.ifBlank { "Coinbase" }
    val modeLabel = if (botMode == BotMode.Live) "LIVE" else "PAPER"
    val caption = "BTC-USD · $effectiveBroker · $modeLabel"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Use string concatenation to avoid Kotlin string template issues with '$'.
                Text(
                    text = "\$" + "%.2f".format(price),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = animatedColor,
                    modifier = Modifier.testTag("dashboard_price"),
                )
                Text(
                    text = arrow,
                    style = MaterialTheme.typography.titleLarge,
                    color = animatedColor,
                )
            }
        }
    }
}

@Composable
private fun TodayPnlCard(pnlPts: Double) {
    val isPositive = pnlPts >= 0.0
    val pnlColor = if (isPositive) BtcPriceUp else BtcPriceDown
    val sign = if (isPositive) "+" else ""
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today's P&L",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$sign${"%.2f".format(pnlPts)} pts",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
    unrealisedPnl: Double,
    onPositionsClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPositionsClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Open Positions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$count position${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$longCount long · $shortCount short",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val sign = if (unrealisedPnl >= 0.0) "+" else ""
            Text(
                text = "$sign${"%.2f".format(unrealisedPnl)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (unrealisedPnl >= 0.0) BtcPriceUp else BtcPriceDown,
            )
        }
    }
}

@Composable
private fun PositionsPreviewList(
    positions: List<Position>,
    onViewAllClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        positions.forEach { position ->
            PositionPreviewRow(position = position)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onViewAllClick) {
                Text(text = "View all ›")
            }
        }
    }
}

@Composable
private fun PositionPreviewRow(position: Position) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BTC-PERP",
                style = MaterialTheme.typography.bodyMedium,
            )
            SidePill(side = position.side)
        }
        Column(horizontalAlignment = Alignment.End) {
            // Use string concatenation to avoid Kotlin string template issues with '$'.
            Text(
                text = "\$" + "%.2f".format(position.pnl),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (position.pnl >= 0.0) BtcPriceUp else BtcPriceDown,
            )
            Text(
                text = "%.2f%%".format(position.pnlPct),
                style = MaterialTheme.typography.bodySmall,
                color = if (position.pnlPct >= 0.0) BtcPriceUp else BtcPriceDown,
            )
        }
    }
}

@Composable
private fun SidePill(side: Side) {
    val isLong = side == Side.Long
    Surface(
        shape = MaterialTheme.shapes.small,
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
private fun ActionButtonsRow(
    onScannerClick: () -> Unit,
    onNewTradeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onScannerClick) {
            Text(text = "Scanner")
        }
        Button(
            onClick = onNewTradeClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
        ) {
            Text(text = "New trade")
        }
    }
}
