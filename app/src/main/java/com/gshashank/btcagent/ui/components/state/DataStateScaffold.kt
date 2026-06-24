package com.gshashank.btcagent.ui.components.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Top-level composable that maps a [UiState] to the correct UI component.
 *
 * Usage contract:
 * - [UiState.Loading]              → [skeleton] slot (defaults to [DataLoadingSkeleton]).
 * - [UiState.Empty]                → [EmptyStateContent] with [onRefresh].
 * - [UiState.Error]                → [ErrorStateContent] with [onRetry].
 * - [UiState.Offline] (hasCache=false, or cachedData=null)
 *                                  → [OfflineBanner] + [OfflineCenterContent].
 * - [UiState.Offline] (hasCache=true AND cachedData≠null)
 *                                  → [OfflineBanner] + [content](cachedData).
 * - [UiState.Ready]                → [content](uiState.data) directly.
 *
 * The `when` is exhaustive with no `else` branch, which guarantees at compile time that
 * adding a new [UiState] variant forces every [DataStateScaffold] call site to handle it.
 *
 * Offline + cache contract:
 * When emitting [UiState.Offline] with [UiState.Offline.hasCache] = true, the caller's
 * ViewModel is responsible for providing [cachedData] (the last known [UiState.Ready] payload).
 * If [cachedData] is null when [hasCache] is true, [OfflineCenterContent] is shown as a safe
 * fallback. The scaffold does NOT hold state itself.
 *
 * NOTE: Existing screen-scoped states (LoginUiState, GateUiState) are NOT migrated to
 * [UiState]. Only new data screens (starting with MOBILE-5 Dashboard) should adopt this type.
 *
 * @param uiState    The current data state.
 * @param onRefresh  Called by [EmptyStateContent]'s "Refresh" button.
 * @param onRetry    Called by [ErrorStateContent]'s "Try again" button.
 * @param modifier   Layout modifier applied to the root container.
 * @param cachedData Last known payload, passed to [content] when offline with cache available.
 * @param skeleton   Slot rendered for [UiState.Loading]; defaults to [DataLoadingSkeleton].
 * @param content    Slot rendered for [UiState.Ready] (and offline+cache). Receives the payload [T].
 */
@Composable
fun <T> DataStateScaffold(
    uiState: UiState<T>,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    cachedData: T? = null,
    skeleton: @Composable () -> Unit = { DataLoadingSkeleton() },
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier) {
        // Exhaustive when — no else branch. Adding a new UiState variant will cause a
        // compile error here until this when expression is updated.
        when (uiState) {
            is UiState.Loading -> skeleton()

            is UiState.Empty -> EmptyStateContent(onRefresh = onRefresh)

            is UiState.Error -> ErrorStateContent(
                message = uiState.message,
                errorCode = uiState.code,
                onRetry = onRetry,
            )

            is UiState.Offline -> {
                Column {
                    OfflineBanner(lastUpdatedMs = uiState.lastUpdatedMs)
                    if (uiState.hasCache && cachedData != null) {
                        // cachedData is smart-cast to T (non-null) after the null check above.
                        // No testTag here — the caller's content slot owns its own tags.
                        content(cachedData)
                    } else {
                        OfflineCenterContent()
                    }
                }
            }

            is UiState.Ready -> {
                Box(modifier = Modifier.testTag("scaffold_ready_content")) {
                    content(uiState.data)
                }
            }
        }
    }
}
