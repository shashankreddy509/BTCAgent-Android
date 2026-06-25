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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.BotMode
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.data.model.PriceDirection
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp

/**
 * Hero layout showing live BTC price, today's P&L, open positions, and bot status.
 *
 * Sections:
 *  1. Live Price card with directional color animation and tick arrow.
 *  2. Today's P&L in points.
 *  3. Open Positions summary (count + aggregate unrealised P&L). Tapping navigates to MOBILE-6.
 *  4. Bot Status chip (Running/Stopped + LIVE/PAPER badge).
 *
 * Test seam: [testTag("dashboard_price")] on the price headline text.
 *
 * @param onPositionsClick Called when the user taps the Open Positions card; navigates to
 *   the MOBILE-6 positions list screen.
 */
@Composable
fun DashboardHeroContent(
    data: DashboardData,
    onPositionsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Live Price card
        LivePriceCard(price = data.btcPrice, direction = data.priceDirection)

        // 2. Today's P&L
        TodayPnlRow(pnlPts = data.todayPnlPts)

        // 3. Open Positions card — tapping navigates to MOBILE-6 positions list
        OpenPositionsCard(
            count = data.openPositionCount,
            unrealisedPnl = data.openUnrealisedPnl,
            onPositionsClick = onPositionsClick,
        )

        // 4. Bot Status chip
        BotStatusRow(running = data.botRunning, mode = data.botMode)
    }
}

@Composable
private fun LivePriceCard(price: Double, direction: PriceDirection) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "BTC / USDT",
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
private fun TodayPnlRow(pnlPts: Double) {
    val isPositive = pnlPts >= 0.0
    val pnlColor = if (isPositive) BtcPriceUp else BtcPriceDown
    val sign = if (isPositive) "+" else ""
    Row(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun OpenPositionsCard(
    count: Int,
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
private fun BotStatusRow(running: Boolean, mode: BotMode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (running) "Running" else "Stopped",
            style = MaterialTheme.typography.bodyMedium,
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (mode == BotMode.Live) BtcPriceDown else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = if (mode == BotMode.Live) "LIVE" else "PAPER",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
