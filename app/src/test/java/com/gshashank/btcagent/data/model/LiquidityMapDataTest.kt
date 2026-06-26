package com.gshashank.btcagent.data.model

import com.gshashank.btcagent.data.network.LiquidityDto
import com.gshashank.btcagent.data.network.LiquidityRowDto
import com.gshashank.btcagent.data.repository.colorToHeat
import com.gshashank.btcagent.data.repository.parseLeverage
import com.gshashank.btcagent.data.repository.parsePrice
import com.gshashank.btcagent.data.repository.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [LiquidityMapData] domain model, parse helpers, and DTO mappings.
 * MOBILE-15 — Liquidity Map.
 *
 * Tests must FAIL (red) until the production classes are implemented.
 *
 * Covers:
 *  - parseLeverage: suffix K/M/B (case-insensitive), plain numeric, comma-stripping, null/garbage → null
 *  - parsePrice: valid, unparseable, null
 *  - colorToHeat: RED/ORANGE→HOT, YELLOW→WARM, BLUE/GREEN/others/null→COOL, case-insensitive
 *  - LiquidityDto.toDomain(): drops bad rows, sorts by price DESC, lastUpdated from last row
 *  - LiquidityMapData.isEmpty, maxNotional
 *  - @SerialName fields: "y_pixel"→yPixel, "y_range"→yRange
 *  - status "no_data" with empty rows → isEmpty == true
 *  - lastUpdated from last row's timestamp
 */
class LiquidityMapDataTest {

    private val testJson = Json { ignoreUnknownKeys = true }

    // =========================================================================
    // parseLeverage
    // =========================================================================

    @Test
    fun `parseLeverage parses M suffix correctly`() {
        val result = parseLeverage("18.74M")
        assertNotNull("parseLeverage(\"18.74M\") must not be null", result)
        assertEquals("parseLeverage(\"18.74M\") must return 18_740_000.0", 18_740_000.0, result!!, 0.001)
    }

    @Test
    fun `parseLeverage parses K suffix correctly`() {
        val result = parseLeverage("500K")
        assertNotNull("parseLeverage(\"500K\") must not be null", result)
        assertEquals("parseLeverage(\"500K\") must return 500_000.0", 500_000.0, result!!, 0.001)
    }

    @Test
    fun `parseLeverage parses B suffix correctly`() {
        val result = parseLeverage("1.2B")
        assertNotNull("parseLeverage(\"1.2B\") must not be null", result)
        assertEquals("parseLeverage(\"1.2B\") must return 1_200_000_000.0", 1_200_000_000.0, result!!, 1.0)
    }

    @Test
    fun `parseLeverage parses plain numeric string correctly`() {
        val result = parseLeverage("1234")
        assertNotNull("parseLeverage(\"1234\") must not be null", result)
        assertEquals("parseLeverage(\"1234\") must return 1234.0", 1234.0, result!!, 0.001)
    }

    @Test
    fun `parseLeverage returns null for garbage input`() {
        assertNull("parseLeverage(\"abc\") must return null", parseLeverage("abc"))
    }

    @Test
    fun `parseLeverage returns null for null input`() {
        assertNull("parseLeverage(null) must return null", parseLeverage(null))
    }

    @Test
    fun `parseLeverage strips commas before parsing`() {
        val result = parseLeverage("1,234.5K")
        assertNotNull("parseLeverage(\"1,234.5K\") must not be null", result)
        assertEquals("parseLeverage(\"1,234.5K\") must return 1_234_500.0", 1_234_500.0, result!!, 0.001)
    }

    // =========================================================================
    // parsePrice
    // =========================================================================

    @Test
    fun `parsePrice parses valid decimal string`() {
        val result = parsePrice("43200.5")
        assertNotNull("parsePrice(\"43200.5\") must not be null", result)
        assertEquals("parsePrice(\"43200.5\") must return 43200.5", 43200.5, result!!, 0.001)
    }

    @Test
    fun `parsePrice returns null for non-numeric string`() {
        assertNull("parsePrice(\"x\") must return null", parsePrice("x"))
    }

    @Test
    fun `parsePrice returns null for null input`() {
        assertNull("parsePrice(null) must return null", parsePrice(null))
    }

    // =========================================================================
    // colorToHeat — CoinGlass color label → liquidation-intensity tier
    // =========================================================================

    @Test
    fun `colorToHeat RED maps to HOT`() {
        assertEquals("\"RED\" must map to HeatTier.HOT", HeatTier.HOT, colorToHeat("RED"))
    }

    @Test
    fun `colorToHeat ORANGE maps to HOT`() {
        assertEquals("\"ORANGE\" must map to HeatTier.HOT", HeatTier.HOT, colorToHeat("ORANGE"))
    }

    @Test
    fun `colorToHeat PINK maps to HOT`() {
        assertEquals("\"PINK\" must map to HeatTier.HOT", HeatTier.HOT, colorToHeat("PINK"))
    }

    @Test
    fun `colorToHeat YELLOW maps to WARM`() {
        assertEquals("\"YELLOW\" must map to HeatTier.WARM", HeatTier.WARM, colorToHeat("YELLOW"))
    }

    @Test
    fun `colorToHeat LIME maps to WARM`() {
        assertEquals("\"LIME\" must map to HeatTier.WARM", HeatTier.WARM, colorToHeat("LIME"))
    }

    @Test
    fun `colorToHeat BLUE maps to COOL`() {
        assertEquals("\"BLUE\" must map to HeatTier.COOL", HeatTier.COOL, colorToHeat("BLUE"))
    }

    @Test
    fun `colorToHeat GREEN maps to COOL`() {
        assertEquals("\"GREEN\" must map to HeatTier.COOL", HeatTier.COOL, colorToHeat("GREEN"))
    }

    @Test
    fun `colorToHeat null maps to COOL`() {
        assertEquals("null color must map to HeatTier.COOL", HeatTier.COOL, colorToHeat(null))
    }

    @Test
    fun `colorToHeat lowercase red maps to HOT (case-insensitive)`() {
        assertEquals("\"red\" must map to HeatTier.HOT (case-insensitive)", HeatTier.HOT, colorToHeat("red"))
    }

    // =========================================================================
    // LiquidityDto.toDomain() — row filtering and sorting
    // =========================================================================

    @Test
    fun `toDomain drops row with unparseable price keeps only parseable rows`() {
        val dto = LiquidityDto(
            rows = listOf(
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:30:00 UTC",
                    color = "RED",
                    leverage = "10M",
                    price = "43200.5",
                ),
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:31:00 UTC",
                    color = "GREEN",
                    leverage = "5M",
                    price = "not_a_number", // bad price — must be dropped
                ),
            ),
            status = "ok",
        )

        val result = dto.toDomain()
        assertEquals("toDomain must keep only the 1 row with a parseable price", 1, result.levels.size)
        assertEquals(
            "The kept level must have the correct price",
            43200.5,
            result.levels[0].price,
            0.001,
        )
    }

    @Test
    fun `toDomain drops row with unparseable leverage`() {
        val dto = LiquidityDto(
            rows = listOf(
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:30:00 UTC",
                    color = "GREEN",
                    leverage = "badlev", // bad leverage — must be dropped
                    price = "43000.0",
                ),
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:31:00 UTC",
                    color = "RED",
                    leverage = "8M",
                    price = "44000.0",
                ),
            ),
            status = "ok",
        )

        val result = dto.toDomain()
        assertEquals("toDomain must keep only the 1 row with parseable leverage", 1, result.levels.size)
        assertEquals(
            "The kept level must have the correct price",
            44000.0,
            result.levels[0].price,
            0.001,
        )
    }

    @Test
    fun `toDomain sorts levels by price DESCENDING`() {
        val dto = LiquidityDto(
            rows = listOf(
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:30:00 UTC",
                    color = "RED",
                    leverage = "8M",
                    price = "40000.0",
                ),
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:31:00 UTC",
                    color = "GREEN",
                    leverage = "5M",
                    price = "50000.0",
                ),
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:32:00 UTC",
                    color = "RED",
                    leverage = "3M",
                    price = "45000.0",
                ),
            ),
            status = "ok",
        )

        val result = dto.toDomain()
        assertEquals("Must have 3 levels", 3, result.levels.size)
        assertTrue(
            "Levels must be sorted price DESC: first price (${result.levels[0].price}) must be >= second (${result.levels[1].price})",
            result.levels[0].price >= result.levels[1].price,
        )
        assertTrue(
            "Levels must be sorted price DESC: second price (${result.levels[1].price}) must be >= third (${result.levels[2].price})",
            result.levels[1].price >= result.levels[2].price,
        )
        assertEquals("Highest price level must be 50000.0", 50000.0, result.levels[0].price, 0.001)
        assertEquals("Lowest price level must be 40000.0", 40000.0, result.levels[2].price, 0.001)
    }

    // =========================================================================
    // LiquidityMapData.isEmpty and maxNotional
    // =========================================================================

    @Test
    fun `isEmpty is true on empty LiquidityMapData`() {
        val data = LiquidityMapData(levels = emptyList(), lastUpdated = null)
        assertTrue("isEmpty must be true when levels list is empty", data.isEmpty)
    }

    @Test
    fun `isEmpty is false when levels list is non-empty`() {
        val level = LiquidityLevel(price = 43200.5, tier = HeatTier.HOT, notional = 8_000_000.0, timestamp = "2026-06-25 14:30:00 UTC")
        val data = LiquidityMapData(levels = listOf(level), lastUpdated = "2026-06-25 14:30:00 UTC")
        assertFalse("isEmpty must be false when levels list is non-empty", data.isEmpty)
    }

    @Test
    fun `maxNotional returns correct maximum across multiple levels`() {
        val levels = listOf(
            LiquidityLevel(price = 43200.0, tier = HeatTier.HOT, notional = 8_000_000.0, timestamp = "t1"),
            LiquidityLevel(price = 44000.0, tier = HeatTier.COOL, notional = 15_000_000.0, timestamp = "t2"),
            LiquidityLevel(price = 42000.0, tier = HeatTier.WARM, notional = 3_000_000.0, timestamp = "t3"),
        )
        val data = LiquidityMapData(levels = levels, lastUpdated = "t2")
        assertEquals("maxNotional must return 15_000_000.0", 15_000_000.0, data.maxNotional, 0.001)
    }

    @Test
    fun `maxNotional returns 0 on empty LiquidityMapData`() {
        val data = LiquidityMapData(levels = emptyList(), lastUpdated = null)
        assertEquals("maxNotional must return 0.0 when levels list is empty", 0.0, data.maxNotional, 0.001)
    }

    // =========================================================================
    // @SerialName JSON deserialization
    // =========================================================================

    @Test
    fun `@SerialName y_pixel deserializes into yPixel field of LiquidityRowDto`() {
        val json = """{"y_pixel":"125","y_range":"125-125","timestamp":"2026-06-25 14:30:00 UTC","color":"RED","leverage":"8.5M","price":"43200.5"}"""
        val dto = testJson.decodeFromString<LiquidityRowDto>(json)
        assertEquals(
            "\"y_pixel\" in JSON must deserialize into yPixel field",
            "125",
            dto.yPixel,
        )
    }

    @Test
    fun `@SerialName y_range deserializes into yRange field of LiquidityRowDto`() {
        val json = """{"y_pixel":"125","y_range":"125-125","timestamp":"2026-06-25 14:30:00 UTC","color":"RED","leverage":"8.5M","price":"43200.5"}"""
        val dto = testJson.decodeFromString<LiquidityRowDto>(json)
        assertEquals(
            "\"y_range\" in JSON must deserialize into yRange field",
            "125-125",
            dto.yRange,
        )
    }

    // =========================================================================
    // status "no_data" with empty rows → toDomain gives isEmpty == true
    // =========================================================================

    @Test
    fun `status no_data with empty rows toDomain gives isEmpty true`() {
        val dto = LiquidityDto(rows = emptyList(), status = "no_data")
        val result = dto.toDomain()
        assertTrue(
            "toDomain with status=no_data and empty rows must give isEmpty==true",
            result.isEmpty,
        )
    }

    // =========================================================================
    // lastUpdated from last row's timestamp
    // =========================================================================

    @Test
    fun `toDomain sets lastUpdated from last row timestamp`() {
        val dto = LiquidityDto(
            rows = listOf(
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:00:00 UTC",
                    color = "RED",
                    leverage = "8M",
                    price = "43000.0",
                ),
                LiquidityRowDto(
                    timestamp = "2026-06-25 14:30:00 UTC", // this is the last row
                    color = "GREEN",
                    leverage = "5M",
                    price = "44000.0",
                ),
            ),
            status = "ok",
        )

        val result = dto.toDomain()
        assertEquals(
            "lastUpdated must be taken from the last row's timestamp in the original list",
            "2026-06-25 14:30:00 UTC",
            result.lastUpdated,
        )
    }

    @Test
    fun `toDomain lastUpdated is null when rows is empty`() {
        val dto = LiquidityDto(rows = emptyList(), status = "ok")
        val result = dto.toDomain()
        assertNull(
            "lastUpdated must be null when rows list is empty",
            result.lastUpdated,
        )
    }
}
