package com.gshashank.btcagent.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Home tab root — mounts [DashboardScreen] unconditionally.
 *
 * No catalog flag (Decision 1 in PLAN.md — MOBILE-5): the Dashboard is the foundational
 * Screen 04 proving the WS + auth pipe; there is no prior production audience to roll back to.
 *
 * The outer [Box] keeps the [testTag("screen_home")] contract that [AppShellTest] depends on.
 *
 * @param onPositionsClick Called when the user taps the Open Positions card; wired from
 *   [AppShell] to navigate to [HomeTab.Positions] — MOBILE-6.
 */
@Composable
fun HomeTabScreen(
    onPositionsClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_home"),
    ) {
        DashboardScreen(
            onPositionsClick = onPositionsClick,
        )
    }
}
