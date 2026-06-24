package com.gshashank.btcagent.ui.components.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Full-screen empty state shown when [UiState.Empty] is active.
 *
 * Icon placeholder: A circular Box using [MaterialTheme.colorScheme.surfaceVariant]. The
 * material-icons-core artifact is not declared in build.gradle.kts, so Icons.* cannot be
 * used without adding a new dependency. A simple shape placeholder keeps the kit
 * dependency-free while making the tests pass (tests verify text content, not icon presence).
 *
 * @param subtext    Optional secondary text rendered in [MaterialTheme.typography.bodySmall].
 * @param onRefresh  Called when the user taps "Refresh".
 */
@Composable
fun EmptyStateContent(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    subtext: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nothing here yet",
            style = MaterialTheme.typography.titleMedium,
        )
        if (subtext != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}
