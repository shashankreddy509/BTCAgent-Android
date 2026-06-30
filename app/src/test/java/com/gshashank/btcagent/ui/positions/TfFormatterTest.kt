package com.gshashank.btcagent.ui.positions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [formatTfMinutes] pure helper — MOBILE-43.
 *
 * [formatTfMinutes] converts a timeframe in minutes to a human-readable string:
 *   - Whole hours → "XH" (e.g. 240 → "4H", 60 → "1H", 120 → "2H")
 *   - Sub-hour minutes → "Xm" (e.g. 30 → "30m", 15 → "15m")
 *   - null → "" (empty string — do NOT render "null")
 */
class TfFormatterTest {

    @Test
    fun `240 minutes formats to 4H`() {
        assertEquals("4H", formatTfMinutes(240))
    }

    @Test
    fun `60 minutes formats to 1H`() {
        assertEquals("1H", formatTfMinutes(60))
    }

    @Test
    fun `30 minutes formats to 30m`() {
        assertEquals("30m", formatTfMinutes(30))
    }

    @Test
    fun `120 minutes formats to 2H`() {
        assertEquals("2H", formatTfMinutes(120))
    }

    @Test
    fun `15 minutes formats to 15m`() {
        assertEquals("15m", formatTfMinutes(15))
    }

    @Test
    fun `null tf formats to empty string`() {
        assertEquals("", formatTfMinutes(null))
    }
}
