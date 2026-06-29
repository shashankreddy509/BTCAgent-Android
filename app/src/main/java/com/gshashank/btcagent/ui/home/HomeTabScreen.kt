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
 * @param onScannerClick Called when the user taps the "Scanner" action button; wired from
 *   [AppShell] to navigate to [MarketsRoute.Scanner].
 * @param onNewTradeClick Called when the user taps the "New trade" action button; wired from
 *   [AppShell] to navigate to [TradeTab.ManualEntry].
 */
@Composable
fun HomeTabScreen(
    onPositionsClick: () -> Unit = {},
    onScannerClick: () -> Unit = {},
    onNewTradeClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_home"),
    ) {
        DashboardScreen(
            onPositionsClick = onPositionsClick,
            onScannerClick = onScannerClick,
            onNewTradeClick = onNewTradeClick,
        )
    }
}
