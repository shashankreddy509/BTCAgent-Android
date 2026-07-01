package com.gshashank.btcagent.ui.trade

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.data.model.TradingControlData
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.navigation.TradeTab
import com.gshashank.btcagent.ui.theme.BtcAccent
import com.gshashank.btcagent.ui.theme.BtcPriceDown
import com.gshashank.btcagent.ui.theme.BtcPriceUp
import com.gshashank.btcagent.ui.trade.manual.ManualEntryFlagViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Trading Control screen — MOBILE-18 / MOBILE-41 mock alignment.
 *
 * Redesigned to the `19-trading-control` mock (white-carded, hairline-bordered styling, same
 * approach as the Dashboard hero polish — MOBILE-39): app-bar + PAPER/LIVE pill, a detailed green
 * scanner card, a segmented PAPER/LIVE execution-mode control (LIVE keeps its existing confirm
 * dialog), a 3-row toggle card (Autostart, DEPO alerts, Push), and carded position rows.
 *
 * No leverage control anywhere — dropped permanently per the plan (broker-controlled, not served).
 *
 * MOBILE-19: Catalog-gated "Manual Entry" button (CatalogFlags.MANUAL_ENTRY = 100007) navigates
 * to [TradeTab.ManualEntry]. The flag is read reactively via [ManualEntryFlagViewModel].
 *
 * testTag("screen_trade") is on the root container to support UI automation.
 */
@Composable
fun TradingControlScreen(
    navController: NavController? = null,
    viewModel: TradingControlViewModel = hiltViewModel(),
    flagViewModel: ManualEntryFlagViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    val pendingLiveMode by viewModel.pendingLiveMode.collectAsStateWithLifecycle()
    val manualEntryEnabled by flagViewModel.manualEntryEnabled.collectAsStateWithLifecycle()

    // LIVE confirm dialog — unchanged behavior, only the trigger control is restyled below.
    if (pendingLiveMode) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelLiveMode() },
            title = { Text("Switch to LIVE mode?") },
            text = { Text("LIVE mode uses real funds. Are you sure you want to switch?") },
            confirmButton = {
                Button(onClick = { viewModel.confirmLiveMode() }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLiveMode() }) {
                    Text("Cancel")
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_trade"),
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = state.message)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.fetchState() }) {
                        Text("Retry")
                    }
                }
            }

            is UiState.Ready -> {
                TradingControlContent(
                    data = state.data,
                    onStart = { viewModel.start() },
                    onStop = { viewModel.stop() },
                    onSetMode = { mode -> viewModel.setMode(mode) },
                    onSetAutostart = { enabled -> viewModel.setAutostart(enabled) },
                    onSetDepoAlerts = { enabled -> viewModel.setDepoAlerts(enabled) },
                    onSetPushEnabled = { enabled -> viewModel.setPushEnabled(enabled) },
                    onClose = { signalId -> viewModel.close(signalId) },
                    manualEntryEnabled = manualEntryEnabled,
                    onManualEntry = navController?.let { nav -> { nav.navigate(TradeTab.ManualEntry) } },
                )
            }

            else -> Unit
        }

        // One-shot action result snackbar
        if (actionResult != null) {
            val message = when (val result = actionResult) {
                is ActionResultUiState.Success -> "Action completed successfully"
                is ActionResultUiState.Error -> result.message
                null -> ""
            }
            LaunchedEffect(actionResult) {
                // Auto-clear after display
                viewModel.clearActionResult()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text(message)
            }
        }
    }
}

@Composable
private fun TradingControlContent(
    data: TradingControlData,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSetMode: (ExecutionMode) -> Unit,
    onSetAutostart: (Boolean) -> Unit,
    onSetDepoAlerts: (Boolean) -> Unit,
    onSetPushEnabled: (Boolean) -> Unit,
    onClose: (String) -> Unit,
    manualEntryEnabled: Boolean = false,
    onManualEntry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppBarRow(mode = data.mode)

        ScannerCard(data = data, onStart = onStart, onStop = onStop)

        ExecutionModeSegmented(mode = data.mode, onSetMode = onSetMode)

        TogglesCard(
            data = data,
            onSetAutostart = onSetAutostart,
            onSetDepoAlerts = onSetDepoAlerts,
            onSetPushEnabled = onSetPushEnabled,
        )

        // Manual Entry button — catalog-gated (CatalogFlags.MANUAL_ENTRY = 100007)
        if (manualEntryEnabled && onManualEntry != null) {
            Button(
                onClick = onManualEntry,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Manual Entry")
            }
        }

        PositionsCard(positions = data.positions, onClose = onClose)
    }
}

// ---------------------------------------------------------------------------
// Shared card styling — white surface + hairline border (MOBILE-39 pattern).
// ---------------------------------------------------------------------------

@Composable
private fun tradeCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
)

@Composable
private fun tradeCardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

// ---------------------------------------------------------------------------

@Composable
private fun AppBarRow(mode: ExecutionMode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Trading Control",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        ModePill(mode = mode)
    }
}

@Composable
private fun ModePill(mode: ExecutionMode) {
    val isLive = mode == ExecutionMode.LIVE
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isLive) BtcPriceDown.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Text(
            text = if (isLive) "LIVE" else "PAPER",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isLive) BtcPriceDown else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ScannerCard(
    data: TradingControlData,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BtcPriceUp.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, BtcPriceUp.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (data.running) BtcPriceUp else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = if (data.running) "Scanner running" else "Scanner stopped",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Text(
                    text = "Every ${data.scanInterval}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ScannerStat(label = "Last scan", value = formatLastScanTime(data.lastScanTime))
                ScannerStat(label = "Signals today", value = "${data.signalsToday}")
                ScannerStat(label = "TF count", value = "${data.tfCount}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (data.running) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BtcPriceDown,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("■  Stop scanner")
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BtcPriceUp,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("▶  Start scanner")
                }
            }
        }
    }
}

@Composable
private fun ScannerStat(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * Formats an ISO-8601 [lastScanTime] as a short local time (e.g. "14:32"), falling back to the
 * raw string on any parse error, and "—" when null (never scanned yet). No date-formatting
 * library is added — `java.time.Instant` is already used elsewhere in the codebase for the same
 * ISO-8601 shape (see PositionsRepositoryImpl), so this reuses the same primitive.
 */
private fun formatLastScanTime(lastScanTime: String?): String {
    if (lastScanTime == null) return "—"
    return try {
        Instant.parse(lastScanTime)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        lastScanTime
    }
}

@Composable
private fun ExecutionModeSegmented(
    mode: ExecutionMode,
    onSetMode: (ExecutionMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = tradeCardColors(),
        border = tradeCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Execution mode",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeSegment(
                    selected = mode == ExecutionMode.PAPER,
                    label = "PAPER",
                    caption = "Simulated",
                    accent = BtcAccent,
                    onClick = { onSetMode(ExecutionMode.PAPER) },
                    modifier = Modifier.weight(1f),
                )
                ModeSegment(
                    selected = mode == ExecutionMode.LIVE,
                    label = "LIVE",
                    caption = "Real funds",
                    accent = BtcPriceDown,
                    onClick = { onSetMode(ExecutionMode.LIVE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeSegment(
    selected: Boolean,
    label: String,
    caption: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TogglesCard(
    data: TradingControlData,
    onSetAutostart: (Boolean) -> Unit,
    onSetDepoAlerts: (Boolean) -> Unit,
    onSetPushEnabled: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = tradeCardColors(),
        border = tradeCardBorder(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ToggleRow(
                label = "Autostart",
                caption = "Start scanner automatically",
                checked = data.autostartEnabled,
                onCheckedChange = onSetAutostart,
            )
            ToggleDivider()
            ToggleRow(
                label = "DEPO alerts",
                caption = "Depth-of-market signal alerts",
                checked = data.depoAlertsEnabled,
                onCheckedChange = onSetDepoAlerts,
            )
            ToggleDivider()
            ToggleRow(
                label = "Push",
                caption = "Push notifications for trade events",
                checked = data.pushEnabled,
                onCheckedChange = onSetPushEnabled,
            )
        }
    }
}

@Composable
private fun ToggleDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BtcAccent),
        )
    }
}

@Composable
private fun PositionsCard(
    positions: List<Position>,
    onClose: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = tradeCardColors(),
        border = tradeCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Open positions",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (positions.isEmpty()) {
                Text(
                    text = "No open positions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    positions.forEach { position ->
                        PositionRow(
                            position = position,
                            onClose = { onClose(position.signalId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionRow(
    position: Position,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BTC-PERP",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    SidePill(side = position.side)
                }
                Text(
                    text = "Qty ${position.qty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val pnlColor = if (position.pnl >= 0.0) BtcPriceUp else BtcPriceDown
                val sign = if (position.pnl >= 0.0) "+" else ""
                Text(
                    text = "$sign${"%.2f".format(position.pnl)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = pnlColor,
                )
                Text(
                    text = "%+.2f%%".format(position.pnlPct),
                    style = MaterialTheme.typography.bodySmall,
                    color = pnlColor,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onClose) {
                Text("Close")
            }
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
