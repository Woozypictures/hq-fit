package uk.woozy.fit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthTest {
    @Test
    fun epleyKnownValues() {
        assertEquals(100.0, Strength.epley(100.0, 1), 0.0)
        assertEquals(133.333, Strength.epley(100.0, 10), 0.001)
        assertEquals(60.0, Strength.epley(50.0, 6), 0.001)
    }

    @Test
    fun brzyckiKnownValues() {
        assertEquals(133.333, Strength.brzycki(100.0, 10), 0.001) // 100 x 36 / 27
        assertEquals(100.0, Strength.brzycki(100.0, 1), 0.0)
        assertEquals(Strength.epley(40.0, 40), Strength.brzycki(40.0, 40), 0.0)
    }

    @Test
    fun volumeIsWeightTimesReps() {
        assertEquals(50.0 * 12 + 50.0 * 10, Strength.volume(listOf(SetPoint(50.0, 12), SetPoint(50.0, 10))), 0.0)
    }

    @Test
    fun noHistoryMeansNoRecord() {
        assertTrue(Strength.records(emptyList(), listOf(SetPoint(50.0, 12))).isEmpty())
    }

    @Test
    fun heavierWeightBeatsHeaviestAndUsuallyE1rm() {
        val r = Strength.records(listOf(SetPoint(50.0, 12)), listOf(SetPoint(55.0, 10)))
        assertTrue(RecordType.HEAVIEST in r)
        assertTrue(RecordType.ESTIMATED_1RM in r)
    }

    @Test
    fun moreRepsAtSameWeightIsE1rmAndVolumeButNotHeaviest() {
        val r = Strength.records(listOf(SetPoint(50.0, 10)), listOf(SetPoint(50.0, 12)))
        assertEquals(setOf(RecordType.ESTIMATED_1RM, RecordType.SET_VOLUME), r)
    }

    @Test
    fun equalOrWorseIsNoRecord() {
        assertTrue(Strength.records(listOf(SetPoint(50.0, 12)), listOf(SetPoint(50.0, 12), SetPoint(45.0, 12))).isEmpty())
    }

    @Test
    fun zeroWeightSetsAreIgnored() {
        assertTrue(Strength.records(listOf(SetPoint(0.0, 15)), listOf(SetPoint(0.0, 20))).isEmpty())
    }

    @Test
    fun keypadTyping() {
        assertEquals("5", NumberEntry.digit("", '5'))
        assertEquals("52", NumberEntry.digit("5", '2'))
        assertEquals("7", NumberEntry.digit("0", '7'))
        assertEquals("22.", NumberEntry.dot("22"))
        assertEquals("0.", NumberEntry.dot(""))
        assertEquals("22.", NumberEntry.dot("22."))
        assertEquals("22.5", NumberEntry.digit("22.", '5'))
        assertEquals("2", NumberEntry.backspace("22"))
        assertEquals("", NumberEntry.backspace(""))
    }

    @Test
    fun steppingNeverGoesNegativeAndDropsTrailingZero() {
        assertEquals("52.5", NumberEntry.step("50", 2.5))
        assertEquals("0", NumberEntry.step("1", -2.5))
        assertEquals("2.5", NumberEntry.step("", 2.5))
        assertEquals("50", NumberEntry.step("47.5", 2.5))
    }
}
