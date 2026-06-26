package com.gshashank.btcagent.ui.trade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.data.model.TradingControlData
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Trading Control screen — MOBILE-18.
 *
 * Shows scanner Start/Stop, execution mode (PAPER/LIVE with confirm dialog on LIVE switch),
 * DEPO alerts toggle, and open positions with Close buttons.
 *
 * testTag("screen_trade") is on the root container to support UI automation.
 */
@Composable
fun TradingControlScreen(
    viewModel: TradingControlViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    val pendingLiveMode by viewModel.pendingLiveMode.collectAsStateWithLifecycle()

    // LIVE confirm dialog
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
                    onSetDepoAlerts = { enabled -> viewModel.setDepoAlerts(enabled) },
                    onClose = { signalId -> viewModel.close(signalId) },
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
    onSetDepoAlerts: (Boolean) -> Unit,
    onClose: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Scanner card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (data.running) "Scanner: Running" else "Scanner: Stopped",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (data.running) {
                    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Start")
                    }
                }
            }
        }

        // Mode card
        val isLive = data.mode == ExecutionMode.LIVE
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = if (isLive) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mode: ${data.mode.name}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onSetMode(if (isLive) ExecutionMode.PAPER else ExecutionMode.LIVE)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isLive) "Switch to PAPER" else "Switch to LIVE")
                }
            }
        }

        // DEPO alerts card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DEPO Alerts",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = data.depoAlertsEnabled,
                    onCheckedChange = onSetDepoAlerts,
                )
            }
        }

        // Positions card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Open Positions",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (data.positions.isEmpty()) {
                    Text(
                        text = "No open positions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    data.positions.forEach { position ->
                        PositionRow(
                            position = position,
                            onClose = { onClose(position.signalId) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${position.side.name} @ ${position.entryPrice}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "P&L: ${String.format("%.2f", position.pnl)} (${String.format("%.2f", position.pnlPct)}%)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onClose) {
            Text("Close")
        }
    }
}
