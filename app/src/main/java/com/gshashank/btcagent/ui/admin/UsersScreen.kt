package com.gshashank.btcagent.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.AdminUser
import com.gshashank.btcagent.data.model.AdminUsersData
import com.gshashank.btcagent.data.model.ExecutionMode
import com.gshashank.btcagent.ui.components.state.ActionResultUiState
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Admin Users screen — MOBILE-21.
 *
 * Shows a Pending section (email + Approve/Reject buttons) and an Active section
 * (email + PAPER/LIVE badge + Stop button when scanner is running).
 * Guards against non-admin access: shows "Access denied" when [isAdmin] is false.
 */
@Composable
fun UsersScreen(
    isAdmin: Boolean,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // C1: only fetch the admin endpoints once we've confirmed the user IS an admin. The VM no
    // longer fetches from init{}, so a non-admin landing on this route never calls them.
    LaunchedEffect(isAdmin) {
        if (isAdmin) viewModel.refresh(isAdmin = true)
    }

    LaunchedEffect(actionResult) {
        when (val result = actionResult) {
            is ActionResultUiState.Success -> {
                snackbarHostState.showSnackbar("Done")
                viewModel.clearActionResult()
            }
            is ActionResultUiState.Error -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearActionResult()
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!isAdmin) {
                Text(
                    text = "Access denied",
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Scaffold
            }

            when (val state = uiState) {
                is UiState.Loading -> {
                    Text(
                        text = "Loading...",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                is UiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                is UiState.Ready<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val data = state.data as AdminUsersData
                    UsersContent(
                        data = data,
                        onApprove = { email -> viewModel.approve(email) },
                        onReject = { email -> viewModel.reject(email) },
                        onSetMode = { uid, mode -> viewModel.setMode(uid, mode) },
                        onStop = { uid -> viewModel.stop(uid) },
                    )
                }
                is UiState.Empty -> {
                    Text("No users", modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Offline -> {
                    Text(
                        text = "Offline — pull to refresh when reconnected.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun UsersContent(
    data: AdminUsersData,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onSetMode: (String, ExecutionMode) -> Unit,
    onStop: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Pending", style = MaterialTheme.typography.titleMedium) }

        if (data.pending.isEmpty()) {
            item { Text("No pending users", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(data.pending, key = { it.uid }) { user ->
                PendingUserRow(
                    user = user,
                    onApprove = { onApprove(user.email) },
                    onReject = { onReject(user.email) },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { Text("Active", style = MaterialTheme.typography.titleMedium) }

        if (data.active.isEmpty()) {
            item { Text("No active users", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(data.active, key = { it.uid }) { user ->
                ActiveUserRow(
                    user = user,
                    onSetMode = { mode -> onSetMode(user.uid, mode) },
                    onStop = { onStop(user.uid) },
                )
            }
        }
    }
}

@Composable
private fun PendingUserRow(
    user: AdminUser,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = user.email,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onApprove) { Text("Approve") }
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Reject") }
    }
}

@Composable
private fun ActiveUserRow(
    user: AdminUser,
    onSetMode: (ExecutionMode) -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = user.email,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        // LIVE badge uses errorContainer tint; PAPER uses secondaryContainer
        Text(
            text = if (user.mode == ExecutionMode.LIVE) "LIVE" else "PAPER",
            color = if (user.mode == ExecutionMode.LIVE) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (user.scannerRunning) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Stop") }
        }
    }
}
