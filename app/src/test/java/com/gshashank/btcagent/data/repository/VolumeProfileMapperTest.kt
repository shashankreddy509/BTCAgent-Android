package com.gshashank.btcagent.data.repository

import com.gshashank.btcagent.data.model.Timeframe
import com.gshashank.btcagent.data.network.ProfilesDto
import com.gshashank.btcagent.data.network.SnapshotDto
import com.gshashank.btcagent.data.network.VolumeProfileDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [VolumeProfileDto.toDomain] mapper — MOBILE-14.
 *
 * The mapper is `internal` and lives in package [com.gshashank.btcagent.data.repository],
 * so these tests are placed in the same package to access it without reflection.
 *
 * All tests MUST fail (red) until the mapper is implemented in:
 *   `app/src/main/java/com/gshashank/btcagent/data/repository/VolumeProfileMapper.kt`
 *
 * Covered:
 *  - All 3 timeframe keys (H4, H12, D1) are present in the domain result map.
 *  - Newest-last in API list becomes newest-first in domain (reversed order).
 *  - @SerialName("val") on SnapshotDto.vaLow parses correctly from JSON key "val".
 *  - Empty lists per timeframe map to empty domain lists.
 *  - version is carried through from the DTO.
 */
class VolumeProfileMapperTest {

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // =========================================================================
    // 1. All 3 timeframes present in domain result map
    // =========================================================================

    @Test
    fun `toDomain maps all three timeframes into the result map`() {
        val snapshot = SnapshotDto(
            poc = 50000.0,
            vah = 51000.0,
            vaLow = 49000.0,
            lo = 48000.0,
            hi = 52000.0,
            start = "2026-06-25T00:00:00+00:00",
            current = false,
        )
        val dto = VolumeProfileDto(
            changed = true,
            version = 1,
            profiles = ProfilesDto(
                h4 = listOf(snapshot),
                h12 = listOf(snapshot),
                d1 = listOf(snapshot),
            ),
        )

        val result = dto.toDomain()

        assertNotNull(
            "toDomain() result must be non-null",
            result,
        )
        assertTrue(
            "Result timeframes map must contain Timeframe.H4",
            result.timeframes.containsKey(Timeframe.H4),
        )
        assertTrue(
            "Result timeframes map must contain Timeframe.H12",
            result.timeframes.containsKey(Timeframe.H12),
        )
        assertTrue(
            "Result timeframes map must contain Timeframe.D1",
            result.timeframes.containsKey(Timeframe.D1),
        )
        assertEquals(
            "Result timeframes map must have exactly 3 keys",
            3,
            result.timeframes.size,
        )
    }

    // =========================================================================
    // 2. Newest-last in API list becomes newest-first after toDomain (reversed)
    // =========================================================================

    @Test
    fun `newestLastInApiBecomesNewestFirst after toDomain reverses the list`() {
        val oldest = SnapshotDto(
            poc = 48000.0, vah = 49000.0, vaLow = 47000.0, lo = 46000.0, hi = 50000.0,
            start = "2026-06-23T00:00:00+00:00",
            current = false,
        )
        val middle = SnapshotDto(
            poc = 49000.0, vah = 50000.0, vaLow = 48000.0, lo = 47000.0, hi = 51000.0,
            start = "2026-06-24T00:00:00+00:00",
            current = false,
        )
        val newest = SnapshotDto(
            poc = 50000.0, vah = 51000.0, vaLow = 49000.0, lo = 48000.0, hi = 52000.0,
            start = "2026-06-25T00:00:00+00:00",
            current = false,
        )
        // API returns oldest first, newest last.
        val dto = VolumeProfileDto(
            changed = true,
            version = 3,
            profiles = ProfilesDto(
                h4 = listOf(oldest, middle, newest),
                h12 = emptyList(),
                d1 = emptyList(),
            ),
        )

        val result = dto.toDomain()

        val h4Sessions = result.timeframes[Timeframe.H4]
        assertNotNull("H4 session list must not be null", h4Sessions)
        assertEquals("H4 must have 3 sessions", 3, h4Sessions!!.size)
        assertEquals(
            "First session after reversal must have start=2026-06-25 (newest)",
            "2026-06-25T00:00:00+00:00",
            h4Sessions[0].start,
        )
        assertEquals(
            "Second session after reversal must have start=2026-06-24",
            "2026-06-24T00:00:00+00:00",
            h4Sessions[1].start,
        )
        assertEquals(
            "Third session after reversal must have start=2026-06-23 (oldest)",
            "2026-06-23T00:00:00+00:00",
            h4Sessions[2].start,
        )
    }

    // =========================================================================
    // 3. @SerialName("val") on SnapshotDto.vaLow parses correctly from JSON "val"
    // =========================================================================

    @Test
    fun `SerialName val deserializes into vaLow field on SnapshotDto`() {
        val json = """
            {
              "changed": true,
              "version": 1,
              "profiles": {
                "4h": [
                  {
                    "poc": 50000.0,
                    "vah": 51000.0,
                    "val": 49000.0,
                    "lo": 48000.0,
                    "hi": 52000.0,
                    "start": "2026-06-25T00:00:00+00:00",
                    "current": false
                  }
                ],
                "12h": [],
                "1d": []
              }
            }
        """.trimIndent()

        val dto = testJson.decodeFromString<VolumeProfileDto>(json)
        val result = dto.toDomain()

        val h4 = result.timeframes[Timeframe.H4]
        assertNotNull("H4 sessions must not be null", h4)
        assertEquals("H4 must have 1 session", 1, h4!!.size)
        assertEquals(
            "vaLow must be 49000.0 — @SerialName(\"val\") must correctly deserialize the \"val\" JSON key",
            49000.0,
            h4[0].vaLow!!,
            0.001,
        )
    }

    // =========================================================================
    // 4. Empty lists map to empty domain session lists
    // =========================================================================

    @Test
    fun `empty snapshot lists map to empty domain session lists for all timeframes`() {
        val dto = VolumeProfileDto(
            changed = true,
            version = 0,
            profiles = ProfilesDto(
                h4 = emptyList(),
                h12 = emptyList(),
                d1 = emptyList(),
            ),
        )

        val result = dto.toDomain()

        assertTrue(
            "H4 session list must be empty when DTO h4 is empty",
            result.timeframes[Timeframe.H4]?.isEmpty() == true,
        )
        assertTrue(
            "H12 session list must be empty when DTO h12 is empty",
            result.timeframes[Timeframe.H12]?.isEmpty() == true,
        )
        assertTrue(
            "D1 session list must be empty when DTO d1 is empty",
            result.timeframes[Timeframe.D1]?.isEmpty() == true,
        )
        assertTrue(
            "VolumeProfileData.isEmpty must be true when all timeframe lists are empty",
            result.isEmpty,
        )
    }

    // =========================================================================
    // 5. version is carried through from the DTO
    // =========================================================================

    @Test
    fun `version is carried through from VolumeProfileDto to VolumeProfileData`() {
        val dto = VolumeProfileDto(
            changed = true,
            version = 42,
            profiles = ProfilesDto(
                h4 = emptyList(),
                h12 = emptyList(),
                d1 = emptyList(),
            ),
        )

        val result = dto.toDomain()

        assertEquals(
            "VolumeProfileData.version must equal the DTO version (42)",
            42,
            result.version,
        )
    }

    // =========================================================================
    // 6. null profiles field → all timeframes empty, no crash
    // =========================================================================

    @Test
    fun `null profiles field in DTO maps to all empty timeframe lists without crashing`() {
        val dto = VolumeProfileDto(
            changed = false,
            version = 0,
            profiles = null,
        )

        // Should not throw; must produce a VolumeProfileData with all empty timeframes.
        val result = dto.toDomain()

        assertTrue(
            "When profiles is null all timeframe lists must be empty and isEmpty must be true",
            result.isEmpty,
        )
    }
}
