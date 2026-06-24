package com.gshashank.btcagent.ui.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Sticky top banner displayed when [UiState.Offline] is active.
 *
 * Icon placeholder: None (text-only banner). The material-icons-core artifact is not declared
 * in build.gradle.kts, so Icons.* cannot be used without adding a new dependency. A
 * text-only banner keeps the kit dependency-free while making the tests pass (tests verify
 * text content and testTag, not icon presence).
 *
 * The "N ago" string ticks: a [LaunchedEffect] re-reads the clock every 60s so a long offline
 * session does not show a frozen elapsed time. The caller owns [lastUpdatedMs] (the staleness epoch).
 *
 * testTags (each on a separate node):
 *  - [testTag("scaffold_offline_banner")] on the inner Surface for kit-level tests
 *    (DataStateScaffoldTest).
 *  - [testTag("dashboard_offline")] on the outer Box for Dashboard screen tests
 *    (DashboardScreenTest).
 *
 * @param lastUpdatedMs Epoch-milliseconds of the last successful data fetch.
 */
@Composable
fun OfflineBanner(
    lastUpdatedMs: Long,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember(lastUpdatedMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastUpdatedMs) {
        while (true) {
            delay(60_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val elapsedMinutes = ((nowMs - lastUpdatedMs) / 60_000L).coerceAtLeast(0L)
    val agoText = if (elapsedMinutes < 60L) "${elapsedMinutes}m ago" else "${elapsedMinutes / 60}h ago"

    // Outer Box carries the "dashboard_offline" testTag consumed by DashboardScreenTest.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_offline"),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scaffold_offline_banner"),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "You're offline — showing last cached data · $agoText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * Full-screen center content shown when the device is offline and no cached data is available
 * (i.e. [UiState.Offline] with [UiState.Offline.hasCache] = false).
 */
@Composable
fun OfflineCenterContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You're offline",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Connect to load data",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
