package uk.woozy.fit.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Phase 6 regression tests: decimals, zeros, midnight and daylight-saving edges. */
class RegressionTest {
    private val london = ZoneId.of("Europe/London")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    // ---- keypad and decimal weights (2.5 kg plates, zero, negative) ----
    @Test fun halfKiloStepsAdd() {
        assertEquals("5", NumberEntry.step("2.5", 2.5))
        assertEquals("2.5", NumberEntry.step("", 2.5))
        assertEquals("0", NumberEntry.step("0", -2.5))
        assertEquals("0", NumberEntry.step("1", -2.5))
    }

    @Test fun keypadTypingEdges() {
        assertEquals("5", NumberEntry.digit("0", '5'))
        assertEquals("0.", NumberEntry.dot(""))
        assertEquals("2.5", NumberEntry.dot("2").let { NumberEntry.digit(it, '5') })
        assertEquals("2.5", NumberEntry.dot("2.5"))
        assertEquals("", NumberEntry.backspace(""))
    }

    @Test fun formattingHasNoFloatNoise() {
        assertEquals("0.3", NumberEntry.format(0.1 + 0.2))
        assertEquals("100", NumberEntry.format(100.0))
        assertEquals("2.25", NumberEntry.format(2.25))
    }

    // ---- strength maths with decimals and zeros ----
    @Test fun estimatedOneRepMaxWithDecimals() {
        assertEquals(100 * (1 + 5 / 30.0), Strength.e1rm(SetPoint(100.0, 5)), 1e-9)
        assertEquals(2.5 * (1 + 10 / 30.0), Strength.e1rm(SetPoint(2.5, 10)), 1e-9)
        assertEquals(0.0, Strength.e1rm(SetPoint(0.0, 8)), 0.0)
        assertEquals(60.0, Strength.e1rm(SetPoint(60.0, 0)), 0.0)
    }

    @Test fun volumeSumsAndHandlesEmpty() {
        assertEquals(0.0, Strength.volume(emptyList()), 0.0)
        assertEquals(2.5 * 8 + 5.0 * 5, Strength.volume(listOf(SetPoint(2.5, 8), SetPoint(5.0, 5))), 1e-9)
    }

    // ---- macros ----
    @Test fun scalingToZeroAndLargeAmounts() {
        val oats = mapOf("energy" to 372.0, "protein" to 11.0)
        assertEquals(0.0, Nutrients.scale(oats, 0.0).getValue("energy"), 0.0)
        assertEquals(930.0, Nutrients.scale(oats, 250.0).getValue("energy"), 1e-9)
    }

    @Test fun netCarbsOfSummedFoodsNeverNegative() {
        val total = Nutrients.sum(listOf(mapOf("carbs" to 3.0, "fibre" to 1.0), mapOf("carbs" to 1.0, "fibre" to 6.0)))
        assertEquals(0.0, Nutrients.netCarbs(total), 0.0)
    }

    // ---- midnight and daylight-saving: which day Health Connect files a meal under ----
    @Test fun mealLoggedOnItsOwnDayKeepsItsTime() {
        val t = ms("2026-11-14T23:50:00Z")
        assertEquals(t, MealTime.at("2026-11-14", "dinner", t, london))
    }

    @Test fun lateNightEntryForYesterdayLandsOnYesterday() {
        val created = ms("2026-11-15T00:05:00Z")
        assertEquals(ms("2026-11-14T19:00:00Z"), MealTime.at("2026-11-14", "dinner", created, london))
    }

    @Test fun usualHoursPerMeal() {
        val created = ms("2026-11-16T09:00:00Z")
        assertEquals(ms("2026-11-14T08:00:00Z"), MealTime.at("2026-11-14", "breakfast", created, london))
        assertEquals(ms("2026-11-14T13:00:00Z"), MealTime.at("2026-11-14", "lunch", created, london))
        assertEquals(ms("2026-11-14T16:00:00Z"), MealTime.at("2026-11-14", "snacks", created, london))
    }

    @Test fun daylightSavingDaysUseLocalClockTime() {
        // Clocks went forward on 29 March 2026 (13:00 BST is 12:00Z) and back on 25 October 2026 (13:00 GMT is 13:00Z).
        assertEquals(ms("2026-03-29T12:00:00Z"), MealTime.at("2026-03-29", "lunch", ms("2026-03-30T10:00:00Z"), london))
        assertEquals(ms("2026-10-25T13:00:00Z"), MealTime.at("2026-10-25", "lunch", ms("2026-10-26T10:00:00Z"), london))
    }

    @Test fun badDateKeepsCreatedTime() {
        val t = ms("2026-11-14T10:00:00Z")
        assertEquals(t, MealTime.at("not-a-date", "lunch", t, london))
    }
}
