package com.gshashank.btcagent.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the top-level [parseRegime] function defined in RegimeData.kt — MOBILE-12.
 *
 * The function must perform EXACT case-sensitive matching:
 *   "Bull"     → [Regime.BULL]
 *   "Bear"     → [Regime.BEAR]
 *   "Sideways" → [Regime.SIDEWAYS]
 *   anything else (null, blank, unknown, wrong case) → [Regime.UNKNOWN]
 *
 * All tests MUST fail (red) until [parseRegime] and [Regime] are implemented in
 * data/model/RegimeData.kt.
 */
class ParseRegimeTest {

    // =========================================================================
    // 1. "Bull" → Regime.BULL
    // =========================================================================

    @Test
    fun `Bull string maps to Regime BULL`() {
        assertEquals(
            "\"Bull\" must map to Regime.BULL (exact match)",
            Regime.BULL,
            parseRegime("Bull"),
        )
    }

    // =========================================================================
    // 2. "Bear" → Regime.BEAR
    // =========================================================================

    @Test
    fun `Bear string maps to Regime BEAR`() {
        assertEquals(
            "\"Bear\" must map to Regime.BEAR (exact match)",
            Regime.BEAR,
            parseRegime("Bear"),
        )
    }

    // =========================================================================
    // 3. "Sideways" → Regime.SIDEWAYS
    // =========================================================================

    @Test
    fun `Sideways string maps to Regime SIDEWAYS`() {
        assertEquals(
            "\"Sideways\" must map to Regime.SIDEWAYS (exact match)",
            Regime.SIDEWAYS,
            parseRegime("Sideways"),
        )
    }

    // =========================================================================
    // 4. null → Regime.UNKNOWN
    // =========================================================================

    @Test
    fun `null maps to Regime UNKNOWN`() {
        assertEquals(
            "null must map to Regime.UNKNOWN (no crash, graceful fallback)",
            Regime.UNKNOWN,
            parseRegime(null),
        )
    }

    // =========================================================================
    // 5. empty string → Regime.UNKNOWN
    // =========================================================================

    @Test
    fun `empty string maps to Regime UNKNOWN`() {
        assertEquals(
            "An empty string must map to Regime.UNKNOWN",
            Regime.UNKNOWN,
            parseRegime(""),
        )
    }

    // =========================================================================
    // 6. arbitrary unknown string → Regime.UNKNOWN
    // =========================================================================

    @Test
    fun `arbitrary unknown string maps to Regime UNKNOWN`() {
        assertEquals(
            "An unrecognized string like \"xxx\" must map to Regime.UNKNOWN",
            Regime.UNKNOWN,
            parseRegime("xxx"),
        )
    }

    // =========================================================================
    // 7. "bull" (all lowercase) → Regime.UNKNOWN (exact match required, not case-insensitive)
    // =========================================================================

    @Test
    fun `lowercase bull maps to Regime UNKNOWN because matching is case-sensitive`() {
        assertEquals(
            "\"bull\" (lowercase) must map to Regime.UNKNOWN — parsing is case-sensitive",
            Regime.UNKNOWN,
            parseRegime("bull"),
        )
    }

    // =========================================================================
    // 8. "BULL" (all uppercase) → Regime.UNKNOWN (exact match required)
    // =========================================================================

    @Test
    fun `uppercase BULL maps to Regime UNKNOWN because matching is case-sensitive`() {
        assertEquals(
            "\"BULL\" (all-caps) must map to Regime.UNKNOWN — parsing is case-sensitive",
            Regime.UNKNOWN,
            parseRegime("BULL"),
        )
    }
}
