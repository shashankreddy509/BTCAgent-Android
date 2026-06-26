package com.gshashank.btcagent.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the [VolumeProfileData] domain model and related types — MOBILE-14.
 *
 * Tests are pure model-level: no DTO mapping (those live in [VolumeProfileMapperTest]), no
 * network calls, no Android framework.
 *
 * All tests MUST fail (red) until the production classes are implemented in:
 *   `app/src/main/java/com/gshashank/btcagent/data/model/VolumeProfileData.kt`
 *
 * Covered:
 *  - [Timeframe] enum keys: H4="4h", H12="12h", D1="1d"
 *  - [Session.hasData]: true only when ALL five profile fields (poc, vah, vaLow, lo, hi) are non-null
 *  - [VolumeProfileData.isEmpty]: true when all timeframe lists empty; false when one non-empty
 */
class VolumeProfileDataTest {

    // =========================================================================
    // Timeframe enum keys
    // =========================================================================

    @Test
    fun `Timeframe H4 key is 4h`() {
        assertEquals(
            "Timeframe.H4.key must be \"4h\" to match the backend JSON key",
            "4h",
            Timeframe.H4.key,
        )
    }

    @Test
    fun `Timeframe H12 key is 12h`() {
        assertEquals(
            "Timeframe.H12.key must be \"12h\" to match the backend JSON key",
            "12h",
            Timeframe.H12.key,
        )
    }

    @Test
    fun `Timeframe D1 key is 1d`() {
        assertEquals(
            "Timeframe.D1.key must be \"1d\" to match the backend JSON key",
            "1d",
            Timeframe.D1.key,
        )
    }

    // =========================================================================
    // Session.hasData
    // =========================================================================

    @Test
    fun `hasData is false when poc is null`() {
        val session = Session(
            start = "2026-06-25T00:00:00+00:00",
            poc = null,
            vah = 51000.0,
            vaLow = 49000.0,
            lo = 48000.0,
            hi = 52000.0,
        )

        assertFalse(
            "Session.hasData must be false when poc is null — no meaningful profile data",
            session.hasData,
        )
    }

    @Test
    fun `hasData is true when poc is present`() {
        val session = Session(
            start = "2026-06-25T00:00:00+00:00",
            poc = 50000.0,
            vah = 51000.0,
            vaLow = 49000.0,
            lo = 48000.0,
            hi = 52000.0,
        )

        assertTrue(
            "Session.hasData must be true when poc is non-null",
            session.hasData,
        )
    }

    // =========================================================================
    // VolumeProfileData.isEmpty
    // =========================================================================

    @Test
    fun `isEmpty is true when all timeframe lists are empty`() {
        val data = VolumeProfileData(
            timeframes = mapOf(
                Timeframe.H4 to emptyList(),
                Timeframe.H12 to emptyList(),
                Timeframe.D1 to emptyList(),
            ),
            version = 0,
        )

        assertTrue(
            "VolumeProfileData.isEmpty must be true when all timeframe session lists are empty",
            data.isEmpty,
        )
    }

    @Test
    fun `isEmpty is false when at least one timeframe list is non-empty`() {
        val session = Session(
            start = "2026-06-25T00:00:00+00:00",
            poc = 50000.0,
            vah = 51000.0,
            vaLow = 49000.0,
            lo = 48000.0,
            hi = 52000.0,
        )
        val data = VolumeProfileData(
            timeframes = mapOf(
                Timeframe.H4 to listOf(session),
                Timeframe.H12 to emptyList(),
                Timeframe.D1 to emptyList(),
            ),
            version = 1,
        )

        assertFalse(
            "VolumeProfileData.isEmpty must be false when at least one timeframe list is non-empty",
            data.isEmpty,
        )
    }
}
