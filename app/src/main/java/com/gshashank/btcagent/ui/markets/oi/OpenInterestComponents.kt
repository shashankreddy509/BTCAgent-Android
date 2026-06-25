package com.gshashank.btcagent.ui.markets.oi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.data.model.OiSignal
import com.gshashank.btcagent.data.model.OpenInterestData
import com.gshashank.btcagent.ui.markets.briefing.formatRelativeTime
import com.gshashank.btcagent.ui.theme.BtcDown
import com.gshashank.btcagent.ui.theme.BtcUp

/**
 * Maps an [OiSignal] to the appropriate theme color.
 *
 * LONG  → [BtcUp]   (green)
 * SHORT → [BtcDown] (red)
 * NONE  → onSurfaceVariant (neutral)
 */
fun oiSignalColor(signal: OiSignal, colors: ColorScheme): Color = when (signal) {
    OiSignal.LONG -> BtcUp
    OiSignal.SHORT -> BtcDown
    OiSignal.NONE -> colors.onSurfaceVariant
}

/**
 * Card showing the OI delta value and the 5-point sparkline.
 *
 * testTag: "oi_value_card"
 */
@Composable
fun OiValueCard(
    oiDelta: Double?,
    sparkline: List<Double>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("oi_value_card")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "OI Δ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val valueText = if (oiDelta != null) {
            String.format("%.2f", oiDelta)
        } else {
            "—"
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (sparkline.isNotEmpty()) {
            Sparkline(
                points = sparkline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            )
        }
    }
}

/**
 * Compose Canvas polyline over a list of OI delta history points.
 *
 * Guard: if points.size < 2 → renders nothing (flat line guard).
 * Auto-scales Y to min/max of the series.
 *
 * testTag: "oi_sparkline"
 */
@Composable
fun Sparkline(
    points: List<Double>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier.testTag("oi_sparkline"),
    ) {
        if (points.size < 2) return@Canvas

        val minVal = points.min()
        val maxVal = points.max()
        val range = (maxVal - minVal).let { if (it == 0.0) 1.0 else it }

        val stepX = size.width / (points.size - 1).toFloat()

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = index * stepX
            // Y axis: top = max, bottom = min
            val normalised = ((value - minVal) / range).toFloat()
            val y = size.height * (1f - normalised)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/**
 * KV readout of OI signal metadata.
 *
 * Rows: Signal (colored), Large OI ↑, Large OI ↓, Upper Threshold, Lower Threshold, Signal Age.
 *
 * testTag: "oi_signal_readout"
 */
@Composable
fun OiSignalReadout(
    data: OpenInterestData,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("oi_signal_readout")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OiStatRow(
            label = "Signal",
            value = data.signal.name,
            valueColor = oiSignalColor(data.signal, colors),
        )
        OiStatRow(
            label = "Large OI ↑",
            value = if (data.largeUp) "Yes" else "No",
        )
        OiStatRow(
            label = "Large OI ↓",
            value = if (data.largeDown) "Yes" else "No",
        )
        OiStatRow(
            label = "Upper Threshold",
            value = data.upperThresh?.let { String.format("%.2f", it) } ?: "—",
        )
        OiStatRow(
            label = "Lower Threshold",
            value = data.lowerThresh?.let { String.format("%.2f", it) } ?: "—",
        )
        val ageText = data.signalAgeMs?.let { ageMs ->
            // signalAgeMs = now - receivedAt, so receivedAt = now - ageMs.
            // formatRelativeTime(epochMs, nowMs) → we pass epoch=(now-ageMs), now=now.
            val nowMs = System.currentTimeMillis()
            formatRelativeTime(epochMs = nowMs - ageMs, nowMs = nowMs)
        } ?: "—"
        OiStatRow(
            label = "Signal Age",
            value = ageText,
        )
    }
}

/**
 * Single key-value row matching the RegimeStatRow idiom from RegimeComponents.
 */
@Composable
private fun OiStatRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}
