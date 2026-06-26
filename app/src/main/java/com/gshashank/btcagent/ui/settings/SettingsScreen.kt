package com.gshashank.btcagent.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.UserSettings
import com.gshashank.btcagent.ui.components.state.UiState

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateToLogin.collect {
            onSignedOut()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_settings"),
    ) {
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
                    onSetDarkMode = { viewModel.setDarkMode(it) },
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
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    darkMode: Boolean,
    onSetDarkMode: (Boolean) -> Unit,
    onSave: (Int?, Double?, Double?, Int?) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Appearance card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Appearance")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Dark Mode")
                    Switch(checked = darkMode, onCheckedChange = { onSetDarkMode(it) })
                }
            }
        }

        // Trading params card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Trading Parameters")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.qty?.toString() ?: "",
                    onValueChange = {},
                    label = { Text("Quantity") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.maxSl?.toString() ?: "",
                    onValueChange = {},
                    label = { Text("Max Stop Loss") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.minTp?.toString() ?: "",
                    onValueChange = {},
                    label = { Text("Min Take Profit") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.maxConcurrent?.toString() ?: "",
                    onValueChange = {},
                    label = { Text("Max Concurrent") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSave(settings.qty, settings.maxSl, settings.minTp, settings.maxConcurrent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Trading Params")
                }
            }
        }

        // Broker keys card (read-only)
        if (settings.brokerKeys.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Broker Keys")
                    Spacer(modifier = Modifier.height(8.dp))
                    settings.brokerKeys.forEach { key ->
                        Text(key)
                    }
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
}
