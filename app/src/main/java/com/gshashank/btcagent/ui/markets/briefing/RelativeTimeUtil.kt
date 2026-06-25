package com.gshashank.btcagent.ui.markets.briefing

/**
 * Formats an epoch-millisecond timestamp as a human-readable relative-time string — MOBILE-9.
 *
 * Contract:
 *  - diff < 60 000 ms (or negative)   → "Updated just now"
 *  - diff in [60 000, 3 600 000)       → "Updated Nm ago"  (N = whole minutes, floor)
 *  - diff >= 3 600 000                 → "Updated Nh ago"  (N = whole hours, floor)
 *
 * @param epochMs  The timestamp of the event (epoch milliseconds).
 * @param nowMs    The current instant (epoch milliseconds). Injected so callers control the clock.
 */
fun formatRelativeTime(epochMs: Long, nowMs: Long): String {
    val diffMs = (nowMs - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> "Updated just now"
        diffMs < 3_600_000L -> "Updated ${diffMs / 60_000L}m ago"
        else -> "Updated ${diffMs / 3_600_000L}h ago"
    }
}
