package com.gshashank.btcagent.ui.components.state

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gshashank.btcagent.ui.theme.LocalMonoFontFamily

/**
 * Full-screen error state shown when [UiState.Error] is active.
 *
 * Icon placeholder: A text "!" using [MaterialTheme.colorScheme.error] tint. The
 * material-icons-core artifact is not declared in build.gradle.kts, so Icons.* cannot be
 * used without adding a new dependency. A text placeholder keeps the kit dependency-free
 * while making the tests pass (tests verify text content/error code, not icon presence).
 *
 * The error code is displayed in a chip using [LocalMonoFontFamily] so the active skin's
 * mono font is used (wired via CompositionLocal in BTCAgentTheme — MOBILE-25).
 *
 * @param message   Human-readable headline (e.g. "Couldn't load data" or a server-supplied label).
 * @param errorCode Machine-readable code shown in the mono chip (e.g. "ERR_503").
 * @param onRetry   Called when the user taps "Try again".
 */
@Composable
fun ErrorStateContent(
    message: String,
    errorCode: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Mono error code chip
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp),
            )
        ) {
            Text(
                text = errorCode,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = LocalMonoFontFamily.current,
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("dashboard_retry"),
        ) {
            Text("Try again")
        }
    }
}
