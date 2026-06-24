package com.gshashank.btcagent.ui.positions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.Position
import com.gshashank.btcagent.ui.components.state.UiState

private val PriceUp = Color(0xFF00C853)
private val PriceDown = Color(0xFFD50000)

/**
 * Position Detail screen — MOBILE-6.
 *
 * Shows a P&L hero, key-value table, Close button (with confirm dialog), and an admin-only
 * Edit TP/SL sheet button.
 *
 * Uses [hiltViewModel] with [PositionDetailViewModel.Factory] to pass [signalId] at VM
 * construction time ([@AssistedInject] pattern).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionDetailScreen(
    signalId: String,
    onBack: () -> Unit,
    viewModel: PositionDetailViewModel = hiltViewModel<PositionDetailViewModel, PositionDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(signalId) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()

    var showCloseDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface close/edit action results — a swallowed 403/404/network error would be invisible.
    LaunchedEffect(actionState) {
        when (val a = actionState) {
            is ActionResultUiState.Success -> snackbarHostState.showSnackbar("Done")
            is ActionResultUiState.Error -> snackbarHostState.showSnackbar("${a.message} (${a.code})")
            null -> Unit
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { inner ->
        when (val s = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(s.message)
                    TextButton(onClick = onBack) { Text("Back") }
                }
            }
            is UiState.Ready -> {
                PositionDetailContent(
                    position = s.data,
                    canEdit = canEdit,
                    onCloseClick = { showCloseDialog = true },
                    onEditClick = { showEditSheet = true },
                    modifier = Modifier.padding(inner),
                )
            }
            else -> {
                // Empty / Offline — centered, padded for system bars (matches the Error branch).
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            }
        }
    }

    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Close position?") },
            text = { Text("This will cancel the position. This action cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    showCloseDialog = false
                    viewModel.close()
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = sheetState,
        ) {
            EditTpSlSheet(
                onSubmit = { sl, tp ->
                    showEditSheet = false
                    viewModel.editTpSl(sl, tp)
                },
                onDismiss = { showEditSheet = false },
            )
        }
    }
}

@Composable
private fun PositionDetailContent(
    position: Position,
    canEdit: Boolean,
    onCloseClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // P&L hero
        val pnlColor = if (position.pnl >= 0.0) PriceUp else PriceDown
        val pnlSign = if (position.pnl >= 0.0) "+" else ""
        Text(
            text = "$pnlSign${"%.2f".format(position.pnl)} ($pnlSign${"%.2f".format(position.pnlPct)}%)",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = pnlColor,
        )

        // KV table
        KvRow("Symbol", "BTC/USDT")
        KvRow("Side", position.side.name)
        KvRow("Entry Price", "\$" + "%.2f".format(position.entryPrice))
        KvRow("Current Price", "\$" + "%.2f".format(position.currentPrice))
        KvRow("Stop Loss", position.sl?.let { "\$%.2f".format(it) } ?: "—")
        KvRow("Take Profit", position.tp?.let { "\$%.2f".format(it) } ?: "—")
        KvRow("Qty", "%.4f".format(position.qty))
        KvRow("Status", position.status)
        KvRow("Opened", position.openedAt)

        Spacer(modifier = Modifier.height(8.dp))

        // Close button
        Button(
            onClick = onCloseClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close Position")
        }

        // Admin-only Edit TP/SL
        if (canEdit) {
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit TP / SL")
            }
        }
    }
}

@Composable
private fun KvRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
