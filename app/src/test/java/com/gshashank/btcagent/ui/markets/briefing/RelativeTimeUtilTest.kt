package com.gshashank.btcagent.ui.markets.briefing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [formatRelativeTime] — MOBILE-9.
 *
 * [formatRelativeTime] converts an epoch-millisecond timestamp to a human-readable relative-time
 * string such as "Updated 2h ago", "Updated 45m ago", or "Updated just now". It takes two
 * parameters — [epochMs] and [nowMs] — so tests can use fixed instants without touching the real
 * system clock.
 *
 * Contract under test:
 *   - Difference < 60 seconds (60_000 ms)  → "Updated just now"
 *   - Difference in [1m, 60m)              → "Updated Nm ago"  (N = whole minutes)
 *   - Difference >= 60 minutes             → "Updated Nh ago"  (N = whole hours)
 *   - Negative difference (epochMs > nowMs) → "Updated just now" (treated as 0 diff)
 *
 * All tests MUST fail (red) until [formatRelativeTime] is implemented.
 */
class RelativeTimeUtilTest {

    // =========================================================================
    // "just now" — difference is zero or sub-minute
    // =========================================================================

    @Test
    fun `same instant returns Updated just now`() {
        val nowMs = 1_750_841_400_000L
        assertEquals(
            "Zero difference must return 'Updated just now'",
            "Updated just now",
            formatRelativeTime(epochMs = nowMs, nowMs = nowMs),
        )
    }

    @Test
    fun `30 seconds ago returns Updated just now`() {
        val nowMs = 1_750_841_400_000L
        val thirtySecondsAgoMs = nowMs - 30_000L
        assertEquals(
            "30 second difference must return 'Updated just now'",
            "Updated just now",
            formatRelativeTime(epochMs = thirtySecondsAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `59 seconds ago returns Updated just now`() {
        val nowMs = 1_750_841_400_000L
        val fiftyNineSecondsAgoMs = nowMs - 59_000L
        assertEquals(
            "59 second difference must return 'Updated just now' (sub-minute boundary)",
            "Updated just now",
            formatRelativeTime(epochMs = fiftyNineSecondsAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `future timestamp returns Updated just now`() {
        val nowMs = 1_750_841_400_000L
        val futureMs = nowMs + 60_000L // 1 minute in the future
        assertEquals(
            "A future epochMs (negative diff) must degrade gracefully to 'Updated just now'",
            "Updated just now",
            formatRelativeTime(epochMs = futureMs, nowMs = nowMs),
        )
    }

    // =========================================================================
    // "Nm ago" — minutes range [1m, 60m)
    // =========================================================================

    @Test
    fun `exactly 1 minute ago returns Updated 1m ago`() {
        val nowMs = 1_750_841_400_000L
        val oneMinuteAgoMs = nowMs - 60_000L
        assertEquals(
            "Exactly 60 000 ms difference must return 'Updated 1m ago'",
            "Updated 1m ago",
            formatRelativeTime(epochMs = oneMinuteAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `45 minutes ago returns Updated 45m ago`() {
        val nowMs = 1_750_841_400_000L
        val fortyFiveMinutesAgoMs = nowMs - 45 * 60_000L
        assertEquals(
            "45-minute difference must return 'Updated 45m ago'",
            "Updated 45m ago",
            formatRelativeTime(epochMs = fortyFiveMinutesAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `59 minutes ago returns Updated 59m ago`() {
        val nowMs = 1_750_841_400_000L
        val fiftyNineMinutesAgoMs = nowMs - 59 * 60_000L
        assertEquals(
            "59-minute difference must return 'Updated 59m ago' (boundary before hour threshold)",
            "Updated 59m ago",
            formatRelativeTime(epochMs = fiftyNineMinutesAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `90 seconds ago returns Updated 1m ago`() {
        val nowMs = 1_750_841_400_000L
        val ninetySecondsAgoMs = nowMs - 90_000L
        assertEquals(
            "90 seconds = 1 whole minute → 'Updated 1m ago'",
            "Updated 1m ago",
            formatRelativeTime(epochMs = ninetySecondsAgoMs, nowMs = nowMs),
        )
    }

    // =========================================================================
    // "Nh ago" — hours range >= 60 minutes
    // =========================================================================

    @Test
    fun `exactly 1 hour ago returns Updated 1h ago`() {
        val nowMs = 1_750_841_400_000L
        val oneHourAgoMs = nowMs - 60 * 60_000L
        assertEquals(
            "Exactly 3 600 000 ms (60 minutes) must return 'Updated 1h ago'",
            "Updated 1h ago",
            formatRelativeTime(epochMs = oneHourAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `2 hours ago returns Updated 2h ago`() {
        val nowMs = 1_750_841_400_000L
        val twoHoursAgoMs = nowMs - 2 * 60 * 60_000L
        assertEquals(
            "2-hour difference must return 'Updated 2h ago'",
            "Updated 2h ago",
            formatRelativeTime(epochMs = twoHoursAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `90 minutes ago returns Updated 1h ago`() {
        val nowMs = 1_750_841_400_000L
        val ninetyMinutesAgoMs = nowMs - 90 * 60_000L
        assertEquals(
            "90 minutes = 1 whole hour → 'Updated 1h ago'",
            "Updated 1h ago",
            formatRelativeTime(epochMs = ninetyMinutesAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `24 hours ago returns Updated 24h ago`() {
        val nowMs = 1_750_841_400_000L
        val twentyFourHoursAgoMs = nowMs - 24 * 60 * 60_000L
        assertEquals(
            "24-hour difference must return 'Updated 24h ago'",
            "Updated 24h ago",
            formatRelativeTime(epochMs = twentyFourHoursAgoMs, nowMs = nowMs),
        )
    }

    @Test
    fun `119 minutes ago returns Updated 1h ago not 2h ago`() {
        val nowMs = 1_750_841_400_000L
        val oneHundredNineteenMinutesAgoMs = nowMs - 119 * 60_000L
        assertEquals(
            "119 minutes = 1 whole hour (floor) → 'Updated 1h ago', not '2h ago'",
            "Updated 1h ago",
            formatRelativeTime(epochMs = oneHundredNineteenMinutesAgoMs, nowMs = nowMs),
        )
    }
}
