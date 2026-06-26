package com.gshashank.btcagent.ui.trade.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gshashank.btcagent.core.biometric.BiometricAuthenticator
import com.gshashank.btcagent.core.biometric.BiometricResult
import com.gshashank.btcagent.data.model.ManualOrderDraft
import com.gshashank.btcagent.data.model.OrderType
import com.gshashank.btcagent.data.model.PendingOrder
import com.gshashank.btcagent.data.model.Side
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import kotlinx.coroutines.launch

/**
 * Manual Entry screen — MOBILE-19.
 *
 * Catalog-gated behind CatalogFlags.MANUAL_ENTRY (id=100007). When the flag is OFF the
 * screen renders nothing (empty composable).
 *
 * Biometric flow: the screen observes [ManualEntryViewModel.pendingConfirmState]. When it is
 * non-null it shows a ModalBottomSheet with a "Confirm with Biometric" button that calls the real
 * [BiometricAuthenticator] and feeds the result back to the ViewModel. Only a genuine
 * [BiometricResult.Success] from the system prompt triggers the POST.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    navController: NavController,
    viewModel: ManualEntryViewModel = hiltViewModel(),
) {
    val catalogEnabled by viewModel.catalogEnabled.collectAsStateWithLifecycle()

    if (!catalogEnabled) return

    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val pendingConfirmState by viewModel.pendingConfirmState.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    val adminOnlyMessage by viewModel.adminOnlyMessage.collectAsStateWithLifecycle()
    val pendingOrders by viewModel.pendingOrders.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Local text field states (raw string inputs)
    var qtyText by remember { mutableStateOf("") }
    var entryText by remember { mutableStateOf("") }
    var limitPriceText by remember { mutableStateOf("") }
    var slText by remember { mutableStateOf("") }
    var tpText by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Biometric authenticator — stateless, safe to construct per-composition.
    val authenticator = remember { BiometricAuthenticator() }
    // MainActivity extends AppCompatActivity (a FragmentActivity). Cast defensively so a non-
    // FragmentActivity host (preview/test) can't crash; null host = no biometric (treated below).
    val activity = LocalContext.current as? FragmentActivity

    // Biometric confirm bottom-sheet
    if (pendingConfirmState != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onBiometricResult(BiometricResult.Cancelled) },
            sheetState = sheetState,
        ) {
            // safe: non-null guard above
            ConfirmOrderSheet(
                draft = pendingConfirmState!!,
                onConfirm = {
                    val host = activity
                    if (host == null) {
                        // No FragmentActivity host — cannot authenticate; discard, never POST.
                        viewModel.onBiometricResult(BiometricResult.Unavailable)
                    } else {
                        coroutineScope.launch {
                            val result = authenticator.authenticate(
                                activity = host,
                                title = "Confirm live order",
                                subtitle = "Authenticate to place this LIVE order",
                            )
                            viewModel.onBiometricResult(result)
                        }
                    }
                },
                onCancel = { viewModel.onBiometricResult(BiometricResult.Cancelled) },
            )
        }
    }

    // W8: drive adminOnlyMessage through SnackbarHost so it doesn't stay forever.
    LaunchedEffect(adminOnlyMessage) {
        val msg = adminOnlyMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearAdminOnlyMessage()
    }

    // W8: drive actionResult through SnackbarHost.
    LaunchedEffect(actionResult) {
        val r = actionResult ?: return@LaunchedEffect
        val msg = when (r) {
            is ActionResultUiState.Success -> "Order placed"
            is ActionResultUiState.Error -> r.message
        }
        snackbarHostState.showSnackbar(msg)
        viewModel.clearActionResult()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Manual Entry", style = MaterialTheme.typography.headlineSmall)

            // Symbol label (static)
            Text(text = "BTC/USDT", style = MaterialTheme.typography.titleMedium)

            // Direction toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.updateDirection(Side.Long) },
                    modifier = Modifier.weight(1f),
                ) { Text("LONG") }
                OutlinedButton(
                    onClick = { viewModel.updateDirection(Side.Short) },
                    modifier = Modifier.weight(1f),
                ) { Text("SHORT") }
            }

            // Order type toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.updateOrderType(OrderType.MARKET) },
                    modifier = Modifier.weight(1f),
                ) { Text("MARKET") }
                OutlinedButton(
                    onClick = { viewModel.updateOrderType(OrderType.LIMIT) },
                    modifier = Modifier.weight(1f),
                ) { Text("LIMIT") }
            }

            // Qty
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it; viewModel.updateQty(it) },
                label = { Text("Qty") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // Entry (current price estimate)
            OutlinedTextField(
                value = entryText,
                onValueChange = { entryText = it; viewModel.updateEntry(it) },
                label = { Text("Entry Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // Limit price — only shown for LIMIT orders
            if (formState.orderType == OrderType.LIMIT) {
                OutlinedTextField(
                    value = limitPriceText,
                    onValueChange = { limitPriceText = it },
                    label = { Text("Limit Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // SL (required)
            OutlinedTextField(
                value = slText,
                onValueChange = { slText = it; viewModel.updateSl(it) },
                label = { Text("Stop Loss") },
                isError = formState.slValidationError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                supportingText = formState.slValidationError?.let { { Text(it) } },
            )

            // TP (optional)
            OutlinedTextField(
                value = tpText,
                onValueChange = { tpText = it; viewModel.updateTp(it) },
                label = { Text("Take Profit (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // W10 fix: summary card gated on BOTH non-null AND no SL validation error so an
            // invalid SL never renders a negative max-loss.
            val summary = formState.orderSummary
            if (summary != null && formState.slValidationError == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Notional: ${String.format("%.2f", summary.notional)}")
                        Text("Max Loss: ${String.format("%.2f", summary.maxLoss)}")
                        if (summary.rr != null) {
                            Text("R:R ${String.format("%.2f", summary.rr)}")
                        }
                    }
                }
            }

            // Place button
            val canPlace = formState.slValidationError == null &&
                formState.qty != null &&
                formState.sl != null

            when (formState.orderType) {
                OrderType.MARKET -> {
                    Button(
                        onClick = { viewModel.placeMarket() },
                        enabled = canPlace,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Place Market Order") }
                }
                OrderType.LIMIT -> {
                    Button(
                        onClick = { viewModel.placeLimit(limitPriceText) },
                        enabled = canPlace && limitPriceText.toDoubleOrNull() != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Place Limit Order") }
                }
            }

            // Resting orders section
            if (pendingOrders.isNotEmpty()) {
                Text(text = "Pending Orders", style = MaterialTheme.typography.titleMedium)
                pendingOrders.forEach { order ->
                    PendingOrderRow(
                        order = order,
                        onCancel = { viewModel.cancelPending(order.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PendingOrderRow(order: PendingOrder, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${order.direction.name} Limit @ ${order.limitPrice}")
            Text("SL: ${order.sl}  qty: ${order.qty}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun ConfirmOrderSheet(
    draft: ManualOrderDraft,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Confirm Order", style = MaterialTheme.typography.titleLarge)
        Text("Direction: ${draft.direction.name}")
        Text("Qty: ${draft.qty}")
        Text("SL: ${draft.sl}")
        draft.tp?.let { Text("TP: $it") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text("Confirm with Biometric")
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
