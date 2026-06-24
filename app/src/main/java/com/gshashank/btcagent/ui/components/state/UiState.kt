package com.gshashank.btcagent.ui.components.state

/**
 * Generic cross-cutting UI state type.
 *
 * Variants:
 * - [Loading]  – data is being fetched; render a skeleton.
 * - [Empty]    – fetch succeeded but result set is empty.
 * - [Error]    – fetch failed with a diagnosable code.
 * - [Offline]  – device is offline; [hasCache] indicates whether stale data is available.
 * - [Ready]    – fetch succeeded with live data payload [T].
 *
 * Existing screen-scoped states (LoginUiState, GateUiState) are NOT migrated.
 * Only new data screens (starting with MOBILE-5 Dashboard) should adopt UiState<T>.
 *
 * NOTE: No NetworkMonitor is built here. UiState.Offline is a state the consuming
 * ViewModel sets — the caller decides when to emit it (e.g. by catching a connectivity
 * exception from the repository). Auto-detection is deferred to a future ticket (MOBILE-5).
 */
sealed interface UiState<out T> {
    /** Data is being fetched. Render skeleton UI. */
    data object Loading : UiState<Nothing>

    /** Fetch succeeded but the result set is empty. */
    data object Empty : UiState<Nothing>

    /**
     * Fetch failed.
     * @param code  Machine-readable error code displayed in a mono chip (e.g. "ERR_503").
     * @param message Human-readable label shown as a headline.
     */
    data class Error(val code: String, val message: String) : UiState<Nothing>

    /**
     * Device is offline.
     * @param lastUpdatedMs Epoch-milliseconds of the last successful fetch, used to compute "N ago" text.
     * @param hasCache      True if stale cached data is available for display.
     */
    data class Offline(val lastUpdatedMs: Long, val hasCache: Boolean) : UiState<Nothing>

    /**
     * Fetch succeeded with live data.
     * @param data The payload to render.
     */
    data class Ready<T>(val data: T) : UiState<T>
}

/**
 * Returns `true` for terminal states ([UiState.Error] and [UiState.Offline]).
 * Callers can use this to decide whether to stop polling.
 */
fun <T> UiState<T>.isTerminal(): Boolean = when (this) {
    is UiState.Error, is UiState.Offline -> true
    // Exhaustive (no else) so a future variant forces an explicit terminal/non-terminal choice.
    is UiState.Loading, is UiState.Empty, is UiState.Ready -> false
}

/**
 * Transforms the payload of a [UiState.Ready] state. All other variants pass through unchanged.
 * Useful when a ViewModel wraps a sub-type into a parent type.
 */
fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Ready   -> UiState.Ready(transform(data))
    is UiState.Loading -> UiState.Loading
    is UiState.Empty   -> UiState.Empty
    is UiState.Error   -> UiState.Error(code, message)
    is UiState.Offline -> UiState.Offline(lastUpdatedMs, hasCache)
}

/**
 * Returns [UiState.Ready.data] or `null` for any other state.
 * Eliminates boilerplate `when` at call sites that only need the data.
 */
fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Ready<T>)?.data
