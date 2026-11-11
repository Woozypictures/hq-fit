package uk.woozy.fit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientsTest {
    private val oats = mapOf("energy" to 372.0, "protein" to 11.0, "carbs" to 60.0, "fibre" to 8.0, "fat" to 8.0)

    @Test
    fun scalingByGrams() {
        val s = Nutrients.scale(oats, 40.0)
        assertEquals(148.8, s.getValue("energy"), 1e-9)
        assertEquals(4.4, s.getValue("protein"), 1e-9)
        assertEquals(3.2, s.getValue("fat"), 1e-9)
    }

    @Test
    fun roundTripThroughText() {
        val back = Nutrients.decode(Nutrients.encode(oats))
        assertEquals(oats, back)
    }

    @Test
    fun badTextNeverCrashes() {
        assertTrue(Nutrients.decode("").isEmpty())
        assertEquals(mapOf("a" to 1.5), Nutrients.decode("a=1.5;broken;=3;b=x"))
    }

    @Test
    fun zeroAmountsAreNotStored() {
        assertEquals("energy=10.0", Nutrients.encode(mapOf("energy" to 10.0, "fat" to 0.0)))
    }

    @Test
    fun sumsAddByKey() {
        val total = Nutrients.sum(listOf(mapOf("energy" to 100.0, "fat" to 1.0), mapOf("energy" to 50.0, "protein" to 5.0)))
        assertEquals(150.0, total.getValue("energy"), 0.0)
        assertEquals(1.0, total.getValue("fat"), 0.0)
        assertEquals(5.0, total.getValue("protein"), 0.0)
    }

    @Test
    fun netCarbsAreCarbsMinusFibreNeverNegative() {
        assertEquals(52.0, Nutrients.netCarbs(oats), 0.0)
        assertEquals(0.0, Nutrients.netCarbs(mapOf("carbs" to 2.0, "fibre" to 5.0)), 0.0)
    }

    @Test
    fun macroEnergyUsesFourFourNine() {
        assertEquals(11 * 4 + 52 * 4 + 8 * 9.0, Nutrients.energyFromMacros(oats), 0.0)
    }

    @Test
    fun mealKeysRoundTripAndUnknownFallsBack() {
        Meal.entries.forEach { assertEquals(it, Meal.fromKey(it.key)) }
        assertEquals(Meal.SNACKS, Meal.fromKey("brunch"))
    }
}
