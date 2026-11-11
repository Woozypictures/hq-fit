package uk.woozy.fit.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {
    @Test
    fun defaultTargetsMatchTheCronometerSetup() {
        val t = Targets()
        assertEquals(2297, t.energyKcal)
        assertEquals(150, t.proteinG)
        assertEquals(260, t.netCarbsG)
        assertEquals(70, t.fatG)
        assertEquals(8500, t.steps)
        assertEquals(80.0, t.goalWeightKg, 0.0)
    }

    @Test
    fun fixedMacrosTotal2270KcalAgainstA2297Target() {
        // This gap is why Cronometer shows its "totals don't match" warning; HQ Fit keeps the warning.
        assertEquals(2270, Targets().macroKcal)
    }

    @Test
    fun waterTargetIsEightGlassesOf250Ml() {
        assertEquals(2000, Targets().waterMl)
    }

    @Test
    fun themeModeRoundTripsAndDefaultsToLight() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromKey(it.key)) }
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("nonsense"))
    }
}
