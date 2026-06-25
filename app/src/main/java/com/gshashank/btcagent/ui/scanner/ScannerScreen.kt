package com.gshashank.btcagent.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.ScannerData
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.OfflineBanner
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Entry-point composable for the Scanner screen — MOBILE-8.
 *
 * Collects [ScannerViewModel.uiState], [ScannerViewModel.canTrigger],
 * [ScannerViewModel.triggerState], and [ScannerViewModel.activeFilter] reactively and
 * delegates rendering to [ScannerScreenContent].
 *
 * No catalog flag — ships unconditionally (per MOBILE-8 plan).
 */
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canTrigger by viewModel.canTrigger.collectAsStateWithLifecycle()
    val triggerState by viewModel.triggerState.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()

    ScannerScreenContent(
        uiState = uiState,
        canTrigger = canTrigger,
        triggerState = triggerState,
        activeFilter = activeFilter,
        onBack = onBack,
        onRetry = viewModel::retry,
        onTriggerScan = viewModel::triggerScan,
        onSetFilter = viewModel::setFilter,
    )
}

/**
 * Stateless content composable — test seam for ScannerScreenTest.
 *
 * Maps [UiState<ScannerData>] to the appropriate UI exhaustively.
 * Note: material-icons-core is not in the dependency graph, so the back button uses
 * a text label "←" instead of an icon vector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreenContent(
    uiState: UiState<ScannerData>,
    canTrigger: Boolean,
    triggerState: ActionResultUiState?,
    activeFilter: ScanFilter,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTriggerScan: () -> Unit,
    onSetFilter: (ScanFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Show trigger result as a snackbar.
    LaunchedEffect(triggerState) {
        when (triggerState) {
            is ActionResultUiState.Success -> snackbarHostState.showSnackbar("Scan triggered")
            is ActionResultUiState.Error -> snackbarHostState.showSnackbar(triggerState.message)
            null -> Unit
        }
    }

    Scaffold(
        modifier = modifier.testTag("scanner_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Scanner") },
                navigationIcon = {
                    // Use text arrow — material-icons-core not in the dependency graph.
                    TextButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    if (canTrigger) {
                        TextButton(
                            onClick = onTriggerScan,
                            modifier = Modifier.testTag("scanner_trigger_btn"),
                        ) {
                            Text("Scan now")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("scanner_skeleton"),
                    )
                }

                is UiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("scanner_empty"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FilterChipRow(
                            activeFilter = activeFilter,
                            onSetFilter = onSetFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                        Text("No signals match the current filter")
                    }
                }

                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("scanner_error"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = uiState.message)
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag("scanner_retry_btn"),
                        ) {
                            Text("Try again")
                        }
                    }
                }

                is UiState.Offline -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("scanner_offline"),
                    ) {
                        OfflineBanner(lastUpdatedMs = uiState.lastUpdatedMs)
                    }
                }

                is UiState.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Timestamp row
                        val timestamp = uiState.data.timestamp
                        if (timestamp != null) {
                            Text(
                                text = "Updated: $timestamp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .testTag("scanner_timestamp"),
                            )
                        }

                        FilterChipRow(
                            activeFilter = activeFilter,
                            onSetFilter = onSetFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(uiState.data.signals) { index, signal ->
                                ScannerSignalRow(
                                    signal = signal,
                                    modifier = Modifier.testTag("scanner_signal_row_$index"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    activeFilter: ScanFilter,
    onSetFilter: (ScanFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScanFilter.entries.forEach { filter ->
            FilterChip(
                selected = activeFilter == filter,
                onClick = { onSetFilter(filter) },
                label = {
                    Text(
                        text = when (filter) {
                            ScanFilter.All -> "All"
                            ScanFilter.Bullish -> "Bullish"
                            ScanFilter.Bearish -> "Bearish"
                            ScanFilter.Depo -> "DEPO"
                        }
                    )
                },
                modifier = Modifier.testTag("scanner_filter_${filter.name.lowercase()}"),
            )
        }
    }
}
