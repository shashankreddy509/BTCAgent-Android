package com.gshashank.btcagent.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the top-level [parseOiSignal] function defined in OpenInterestData.kt — MOBILE-11.
 *
 * The function must perform EXACT case-sensitive matching:
 *   "LONG"  → [OiSignal.LONG]
 *   "SHORT" → [OiSignal.SHORT]
 *   "NONE"  → [OiSignal.NONE]
 *   anything else (null, blank, unknown, wrong case) → [OiSignal.NONE]
 *
 * All tests MUST fail (red) until [parseOiSignal] and [OiSignal] are implemented in
 * data/model/OpenInterestData.kt.
 */
class ParseOiSignalTest {

    // =========================================================================
    // 1. "LONG" → OiSignal.LONG
    // =========================================================================

    @Test
    fun `LONG string maps to OiSignal LONG`() {
        assertEquals(
            "\"LONG\" must map to OiSignal.LONG (exact match)",
            OiSignal.LONG,
            parseOiSignal("LONG"),
        )
    }

    // =========================================================================
    // 2. "SHORT" → OiSignal.SHORT
    // =========================================================================

    @Test
    fun `SHORT string maps to OiSignal SHORT`() {
        assertEquals(
            "\"SHORT\" must map to OiSignal.SHORT (exact match)",
            OiSignal.SHORT,
            parseOiSignal("SHORT"),
        )
    }

    // =========================================================================
    // 3. "NONE" → OiSignal.NONE
    // =========================================================================

    @Test
    fun `NONE string maps to OiSignal NONE`() {
        assertEquals(
            "\"NONE\" must map to OiSignal.NONE (exact match)",
            OiSignal.NONE,
            parseOiSignal("NONE"),
        )
    }

    // =========================================================================
    // 4. null → OiSignal.NONE
    // =========================================================================

    @Test
    fun `null maps to OiSignal NONE`() {
        assertEquals(
            "null must map to OiSignal.NONE (no crash, graceful fallback)",
            OiSignal.NONE,
            parseOiSignal(null),
        )
    }

    // =========================================================================
    // 5. empty string → OiSignal.NONE
    // =========================================================================

    @Test
    fun `empty string maps to OiSignal NONE`() {
        assertEquals(
            "An empty string must map to OiSignal.NONE",
            OiSignal.NONE,
            parseOiSignal(""),
        )
    }

    // =========================================================================
    // 6. arbitrary unknown string → OiSignal.NONE
    // =========================================================================

    @Test
    fun `arbitrary unknown string maps to OiSignal NONE`() {
        assertEquals(
            "An unrecognized string like \"xxx\" must map to OiSignal.NONE",
            OiSignal.NONE,
            parseOiSignal("xxx"),
        )
    }

    // =========================================================================
    // 7. "long" (lowercase) → OiSignal.NONE (exact match only, not case-insensitive)
    // =========================================================================

    @Test
    fun `lowercase long maps to OiSignal NONE because matching is case-sensitive`() {
        assertEquals(
            "\"long\" (lowercase) must map to OiSignal.NONE — parsing is case-sensitive",
            OiSignal.NONE,
            parseOiSignal("long"),
        )
    }

    // =========================================================================
    // Additional: "short" (lowercase) → OiSignal.NONE
    // =========================================================================

    @Test
    fun `lowercase short maps to OiSignal NONE because matching is case-sensitive`() {
        assertEquals(
            "\"short\" (lowercase) must map to OiSignal.NONE — parsing is case-sensitive",
            OiSignal.NONE,
            parseOiSignal("short"),
        )
    }

    // =========================================================================
    // Additional: "none" (lowercase) → OiSignal.NONE
    // =========================================================================

    @Test
    fun `lowercase none maps to OiSignal NONE because matching is case-sensitive`() {
        assertEquals(
            "\"none\" (lowercase) must map to OiSignal.NONE — parsing is case-sensitive",
            OiSignal.NONE,
            parseOiSignal("none"),
        )
    }

    // =========================================================================
    // Additional: "BULLISH" → OiSignal.NONE (an adjacent domain term that must not map)
    // =========================================================================

    @Test
    fun `BULLISH string maps to OiSignal NONE`() {
        assertEquals(
            "\"BULLISH\" is not a valid OI signal and must map to OiSignal.NONE",
            OiSignal.NONE,
            parseOiSignal("BULLISH"),
        )
    }
}
