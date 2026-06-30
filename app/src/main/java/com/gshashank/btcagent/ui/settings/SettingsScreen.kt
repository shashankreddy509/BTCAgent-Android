package com.gshashank.btcagent.ui.settings

import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.gshashank.btcagent.data.model.BrokerInfo
import com.gshashank.btcagent.data.repository.BrokerActionResult
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.ColorTheme
import com.gshashank.btcagent.data.model.DashboardLayout
import com.gshashank.btcagent.data.model.UserSettings
import com.gshashank.btcagent.ui.admin.AdminAccessViewModel
import com.gshashank.btcagent.ui.components.state.UiState
import com.gshashank.btcagent.ui.theme.BtcAccent
import com.gshashank.btcagent.ui.theme.CobaltAccent
import com.gshashank.btcagent.ui.theme.VioletAccent

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit = {},
    onNavigateToUsers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    adminAccessViewModel: AdminAccessViewModel = hiltViewModel(),
    brokerViewModel: BrokerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    val dashboardLayout by viewModel.dashboardLayout.collectAsStateWithLifecycle()
    val isAdmin by adminAccessViewModel.isAdmin.collectAsStateWithLifecycle()
    val brokerState by brokerViewModel.brokerState.collectAsStateWithLifecycle()
    val brokerActionResult by brokerViewModel.actionResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.navigateToLogin.collect {
            onSignedOut()
        }
    }

    // Surface save feedback (success / validation / network error) as a snackbar.
    LaunchedEffect(actionResult) {
        when (val result = actionResult) {
            is ActionResultUiState.Success -> {
                snackbarHostState.showSnackbar("Trading parameters saved")
                viewModel.consumeActionResult()
            }
            is ActionResultUiState.Error -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.consumeActionResult()
            }
            null -> Unit
        }
    }

    // Surface broker action feedback as a snackbar.
    LaunchedEffect(brokerActionResult) {
        when (val result = brokerActionResult) {
            is BrokerActionResult.Success -> {
                snackbarHostState.showSnackbar("Broker API credentials saved")
                brokerViewModel.clearActionResult()
            }
            is BrokerActionResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
                brokerViewModel.clearActionResult()
            }
            null -> Unit
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_settings"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Error: ${state.message}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadSettings() }) {
                            Text("Retry")
                        }
                    }
                }
                is UiState.Ready<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val settings = state.data as UserSettings
                    SettingsContent(
                    settings = settings,
                    darkMode = darkMode,
                    colorTheme = colorTheme,
                    dashboardLayout = dashboardLayout,
                    isAdmin = isAdmin,
                    brokerState = brokerState,
                    onSetDarkMode = { viewModel.setDarkMode(it) },
                    onSetColorTheme = { viewModel.setColorTheme(it) },
                    onSetDashboardLayout = { viewModel.setDashboardLayout(it) },
                    onSave = { qty, maxSl, minTp, maxConcurrent ->
                        viewModel.saveTradingParams(
                            qty = qty,
                            maxSl = maxSl,
                            minTp = minTp,
                            maxConcurrent = maxConcurrent,
                            mode = settings.mode,
                        )
                    },
                        onSignOut = { viewModel.signOut() },
                        onNavigateToUsers = onNavigateToUsers,
                        onReplaceBroker = { broker, apiKey, apiSecret ->
                            brokerViewModel.replaceBroker(broker, apiKey, apiSecret)
                        },
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    darkMode: Boolean,
    colorTheme: ColorTheme,
    dashboardLayout: DashboardLayout,
    isAdmin: Boolean,
    brokerState: BrokerState,
    onSetDarkMode: (Boolean) -> Unit,
    onSetColorTheme: (ColorTheme) -> Unit,
    onSetDashboardLayout: (DashboardLayout) -> Unit,
    onSave: (Int?, Double?, Double?, Int?) -> Unit,
    onSignOut: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onReplaceBroker: (broker: String, apiKey: String, apiSecret: String) -> Unit,
) {
    // Editable trading param state — initialized from server values
    var qtyText by rememberSaveable(settings.qty) { mutableStateOf(settings.qty?.toString() ?: "") }
    var maxSlText by rememberSaveable(settings.maxSl) { mutableStateOf(settings.maxSl?.toString() ?: "") }
    var minTpText by rememberSaveable(settings.minTp) { mutableStateOf(settings.minTp?.toString() ?: "") }
    var maxConcurrentText by rememberSaveable(settings.maxConcurrent) {
        mutableStateOf(settings.maxConcurrent?.toString() ?: "")
    }

    // Dialog state for Replace/Add broker credentials
    var showBrokerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Section header: APPEARANCE
        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )

        // Appearance card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Color theme picker
                Text("Color theme", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                ColorThemeSwatch(
                    selectedTheme = colorTheme,
                    onSelectTheme = onSetColorTheme,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Appearance mode: Dark / Light segmented control
                Text("Appearance mode", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                AppearanceModeSegmented(
                    isDark = darkMode,
                    onSelect = onSetDarkMode,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dashboard layout: Hero / Grid / Terminal segmented control
                Text("Dashboard layout", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                DashboardLayoutSegmented(
                    selected = dashboardLayout,
                    onSelect = onSetDashboardLayout,
                )
            }
        }

        // Section header: BROKER API
        Text(
            text = "BROKER API",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )

        // Broker API card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (val state = brokerState) {
                    is BrokerState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    is BrokerState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is BrokerState.Ready -> {
                        val brokerInfo = state.brokerInfo
                        BrokerApiCardContent(
                            brokerInfo = brokerInfo,
                            onClickReplace = { showBrokerDialog = true },
                        )
                    }
                }
            }
        }

        // Section header: SCANNER PARAMETERS
        Text(
            text = "SCANNER PARAMETERS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )

        // Scanner parameters card (display only)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ScannerParamRow(
                    label = "Scan interval",
                    value = settings.scanIntervalMin?.let { "$it min" } ?: "—",
                )
                Spacer(modifier = Modifier.height(8.dp))
                ScannerParamRow(
                    label = "Timeframes",
                    value = buildTimeframeLabel(settings.tfMin, settings.tfMax),
                )
                Spacer(modifier = Modifier.height(8.dp))
                ScannerParamRow(
                    label = "Patterns",
                    value = settings.patterns?.joinToString(", ")?.ifBlank { "—" } ?: "—",
                )
            }
        }

        // Section header: TRADING PARAMETERS
        Text(
            text = "TRADING PARAMETERS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )

        // Trading params card with editable fields
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxSlText,
                    onValueChange = { maxSlText = it },
                    label = { Text("Max Stop Loss") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = minTpText,
                    onValueChange = { minTpText = it },
                    label = { Text("Min Take Profit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxConcurrentText,
                    onValueChange = { maxConcurrentText = it },
                    label = { Text("Max Concurrent") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSave(
                            qtyText.toIntOrNull(),
                            maxSlText.toDoubleOrNull(),
                            minTpText.toDoubleOrNull(),
                            maxConcurrentText.toIntOrNull(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Trading Params")
                }
            }
        }

        // Admin-gated "Manage Users" row — visible only when isAdmin==true
        if (isAdmin) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToUsers() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Manage Users")
                }
            }
        }

        // Sign out
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign Out")
        }
    }

    // Replace/Add broker credentials dialog
    if (showBrokerDialog) {
        val configuredBroker = (brokerState as? BrokerState.Ready)?.brokerInfo?.broker ?: "coinbase"
        BrokerCredentialsDialog(
            defaultBroker = configuredBroker,
            onDismiss = { showBrokerDialog = false },
            onConfirm = { broker, apiKey, apiSecret ->
                onReplaceBroker(broker, apiKey, apiSecret)
                showBrokerDialog = false
            },
        )
    }
}

/** Card content for the Broker API section — shows broker label, masked key, connected status, and Replace/Add button. */
@Composable
private fun BrokerApiCardContent(
    brokerInfo: BrokerInfo?,
    onClickReplace: () -> Unit,
) {
    if (brokerInfo == null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "No broker connected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClickReplace) {
                Text("Add")
            }
        }
    } else {
        Column {
            // Broker name / account name row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = brokerInfo.accountName.ifBlank { brokerInfo.broker },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                // Connected status indicator
                val (indicatorText, indicatorColor) = when (brokerInfo.connected) {
                    true -> "● Connected" to Color(0xFF4CAF50)
                    false -> "● Disconnected" to MaterialTheme.colorScheme.error
                    null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = indicatorText,
                    style = MaterialTheme.typography.labelSmall,
                    color = indicatorColor,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Masked API key row + Replace button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = brokerInfo.apiKeyMasked.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onClickReplace) {
                    Text("Replace")
                }
            }
        }
    }
}

/** Dialog for entering new broker API credentials. */
@Composable
private fun BrokerCredentialsDialog(
    defaultBroker: String,
    onDismiss: () -> Unit,
    onConfirm: (broker: String, apiKey: String, apiSecret: String) -> Unit,
) {
    // Use remember (NOT rememberSaveable) — raw broker credentials must never be persisted to the
    // saved-instance-state Bundle on disk.
    var brokerText by remember { mutableStateOf(defaultBroker.ifBlank { "coinbase" }) }
    var apiKeyText by remember { mutableStateOf("") }
    var apiSecretText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Broker API Credentials") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = brokerText,
                    onValueChange = { brokerText = it },
                    label = { Text("Broker") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiSecretText,
                    onValueChange = { apiSecretText = it },
                    label = { Text("API Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (brokerText.isNotBlank() && apiKeyText.isNotBlank() && apiSecretText.isNotBlank()) {
                        onConfirm(brokerText.trim(), apiKeyText.trim(), apiSecretText.trim())
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** Read-only label+value row used in the Scanner Parameters section. */
@Composable
private fun ScannerParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Builds a "30m – 6H" style label from nullable tf_min / tf_max minute values. */
private fun buildTimeframeLabel(tfMin: Int?, tfMax: Int?): String {
    return when {
        tfMin != null && tfMax != null -> "${formatMinutes(tfMin)} – ${formatMinutes(tfMax)}"
        tfMin != null -> formatMinutes(tfMin)
        tfMax != null -> formatMinutes(tfMax)
        else -> "—"
    }
}

/** "30m", "6H", "1D" from a minute count. */
private fun formatMinutes(min: Int): String = when {
    min % 1440 == 0 -> "${min / 1440}D"
    min % 60 == 0 -> "${min / 60}H"
    else -> "${min}m"
}

/** Dark / Light segmented control. Selected option shows an orange border. */
@Composable
private fun AppearanceModeSegmented(
    isDark: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(true to "Dark", false to "Light").forEach { (value, label) ->
            val selected = isDark == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(value) }
                    .then(
                        if (selected) Modifier.border(2.dp, accent, MaterialTheme.shapes.small)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Hero / Grid / Terminal segmented control. Selected option shows an orange border. */
@Composable
private fun DashboardLayoutSegmented(
    selected: DashboardLayout,
    onSelect: (DashboardLayout) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            DashboardLayout.HERO to "Hero",
            DashboardLayout.GRID to "Grid",
            DashboardLayout.TERMINAL to "Terminal",
        ).forEach { (layout, label) ->
            val isSelected = selected == layout
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(layout) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, accent, MaterialTheme.shapes.small)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 3 labeled swatch cards for picking a color theme skin (Bitcoin / Cobalt / Violet).
 * Each card shows the skin's accent swatch + its name; the selected card has an orange border.
 * Matches the `21-settings` mock's Color theme row.
 */
@Composable
private fun ColorThemeSwatch(
    selectedTheme: ColorTheme,
    onSelectTheme: (ColorTheme) -> Unit,
) {
    val swatches = listOf(
        Triple(ColorTheme.BITCOIN, BtcAccent, "Bitcoin"),
        Triple(ColorTheme.COBALT, CobaltAccent, "Cobalt"),
        Triple(ColorTheme.VIOLET, VioletAccent, "Violet"),
    )
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        swatches.forEach { (theme, accentColor, label) ->
            val isSelected = theme == selectedTheme
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTheme(theme) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, accent)
                } else {
                    CardDefaults.outlinedCardBorder()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .drawBehind { drawCircle(color = accentColor) },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
