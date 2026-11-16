package uk.woozy.fit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.woozy.fit.model.Progression.Move
import uk.woozy.fit.model.Progression.Rule
import uk.woozy.fit.model.Progression.Settings

class ProgressionTest {
    private fun s(vararg p: Pair<Double, Int>) = p.map { SetPoint(it.first, it.second) }
    private val dbl = Settings(Rule.DOUBLE, 8, 12, 2.5)
    private val lin = Settings(Rule.LINEAR, 5, 5, 2.5)

    @Test
    fun doubleTopOfRangeAddsWeightAndResetsReps() {
        val t = Progression.next(dbl, listOf(s(60.0 to 12, 60.0 to 12, 60.0 to 12)))!!
        assertEquals(Move.UP, t.move)
        assertEquals(62.5, t.weightKg, 0.0)
        assertEquals(8, t.reps)
    }

    @Test
    fun doubleBelowTopAimsForOneMoreRep() {
        val t = Progression.next(dbl, listOf(s(60.0 to 10, 60.0 to 9, 60.0 to 9)))!!
        assertEquals(Move.MORE_REPS, t.move)
        assertEquals(60.0, t.weightKg, 0.0)
        assertEquals(10, t.reps)
    }

    @Test
    fun linearHitAddsAStep() {
        val t = Progression.next(lin, listOf(s(100.0 to 5, 100.0 to 5, 100.0 to 5)))!!
        assertEquals(102.5, t.weightKg, 0.0)
        assertEquals(5, t.reps)
    }

    @Test
    fun linearMissHolds() {
        val t = Progression.next(lin, listOf(s(100.0 to 5, 100.0 to 4)))!!
        assertEquals(Move.HOLD, t.move)
        assertEquals(100.0, t.weightKg, 0.0)
    }

    @Test
    fun threeStalledSessionsDeload() {
        val stuck = s(100.0 to 4, 100.0 to 4)
        val t = Progression.next(lin, listOf(stuck, stuck, stuck))!!
        assertEquals(Move.DELOAD, t.move)
        assertEquals(90.0, t.weightKg, 0.0)
    }

    @Test
    fun improvingRepsIsNotAStall() {
        val t = Progression.next(lin, listOf(s(100.0 to 4, 100.0 to 4), s(100.0 to 4, 100.0 to 3), s(100.0 to 3, 100.0 to 3)))!!
        assertEquals(Move.HOLD, t.move)
    }

    @Test
    fun assistedGoesLighterWhenEveryRepIsDone() {
        val t = Progression.next(lin.copy(assisted = true), listOf(s(30.0 to 5, 30.0 to 5)))!!
        assertEquals(27.5, t.weightKg, 0.0)
    }

    @Test
    fun manualAndNoHistoryGiveNothing() {
        assertNull(Progression.next(dbl.copy(rule = Rule.MANUAL), listOf(s(60.0 to 12))))
        assertNull(Progression.next(dbl, emptyList()))
    }

    @Test
    fun defaultRuleFromTargets() {
        assertEquals(Rule.DOUBLE, Progression.defaultRule(8, 12))
        assertEquals(Rule.LINEAR, Progression.defaultRule(5, 5))
        assertEquals(Rule.LINEAR, Progression.defaultRule(null, null))
    }
}
