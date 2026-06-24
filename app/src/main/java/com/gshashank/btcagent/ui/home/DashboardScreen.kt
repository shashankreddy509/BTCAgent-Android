package com.gshashank.btcagent.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gshashank.btcagent.data.model.DashboardData
import com.gshashank.btcagent.ui.components.state.DataStateScaffold
import com.gshashank.btcagent.ui.components.state.UiState

/**
 * Entry-point composable for the Dashboard screen — MOBILE-5.
 *
 * Collects [DashboardViewModel.uiState] reactively and delegates rendering to
 * [DashboardScreenContent]. Mounts unconditionally (no catalog flag — Decision 1 in PLAN.md).
 */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreenContent(uiState = uiState, onRetry = viewModel::retry)
}

/**
 * Stateless content composable — test seam for [DashboardScreenTest].
 *
 * Maps [UiState<DashboardData>] to the appropriate UI via [DataStateScaffold]:
 *  - Loading  → [DashboardHeroSkeleton]
 *  - Ready    → [DashboardHeroContent]
 *  - Error    → retry button (via DataStateScaffold's ErrorStateContent)
 *  - Offline  → offline banner (via DataStateScaffold's OfflineBanner)
 */
@Composable
fun DashboardScreenContent(
    uiState: UiState<DashboardData>,
    onRetry: () -> Unit,
) {
    DataStateScaffold(
        uiState = uiState,
        skeleton = { DashboardHeroSkeleton() },
        content = { data -> DashboardHeroContent(data = data) },
        onRetry = onRetry,
    )
}
