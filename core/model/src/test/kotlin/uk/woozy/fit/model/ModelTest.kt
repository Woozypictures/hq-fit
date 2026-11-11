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

class TrendTest {
    private fun pts(vararg v: Pair<Double, Double>) = v.map { DayValue(it.first, it.second) }

    @org.junit.Test
    fun fallingWeightHasNegativeSlopeAndAGoalDate() {
        val p = pts(0.0 to 90.0, 7.0 to 89.0, 14.0 to 88.0)
        val slope = Trend.slopePerDay(p)!!
        org.junit.Assert.assertEquals(-1.0 / 7.0, slope, 1e-9)
        org.junit.Assert.assertEquals(70.0, Trend.daysToGoal(88.0, slope, 78.0)!!, 1e-6)
    }

    @org.junit.Test
    fun tooFewOrTooCloseReadingsGiveNoSlope() {
        org.junit.Assert.assertNull(Trend.slopePerDay(pts(0.0 to 90.0, 7.0 to 89.0)))
        org.junit.Assert.assertNull(Trend.slopePerDay(pts(0.0 to 90.0, 1.0 to 89.0, 2.0 to 88.0)))
    }

    @org.junit.Test
    fun movingTheWrongWayHasNoGoalDate() {
        org.junit.Assert.assertNull(Trend.daysToGoal(88.0, 0.1, 80.0))
        org.junit.Assert.assertNull(Trend.daysToGoal(88.0, 0.0, 80.0))
        org.junit.Assert.assertEquals(0.0, Trend.daysToGoal(80.0, 0.1, 80.0)!!, 0.0)
    }

    @org.junit.Test
    fun movingAverageShortensAtTheStart() {
        org.junit.Assert.assertEquals(listOf(1.0, 1.5, 2.5), Trend.movingAverage(listOf(1.0, 2.0, 3.0), 2))
    }
}
