package uk.woozy.fit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatesTest {
    @Test
    fun perSideBreaksDownHeaviestFirst() {
        assertEquals(listOf(25.0, 15.0), Plates.perSide(100.0))
        assertEquals(listOf(20.0, 2.5), Plates.perSide(65.0))
        assertEquals(listOf(1.25), Plates.perSide(22.5))
        assertEquals(listOf(5.0), Plates.perSide(30.0))
    }

    @Test
    fun perSideNullWhenBarOnlyOrImpossible() {
        assertNull(Plates.perSide(20.0))
        assertNull(Plates.perSide(15.0))
        assertNull(Plates.perSide(21.0))
    }

    @Test
    fun labelJoinsPlates() {
        assertEquals("20 + 5 + 1.25", Plates.label(listOf(20.0, 5.0, 1.25)))
    }

    @Test
    fun warmupsRampAndRoundDown() {
        val w = Plates.warmups(100.0, 2.5)
        assertEquals(listOf(40.0 to 10, 60.0 to 5, 80.0 to 3), w.map { it.weightKg to it.reps })
    }

    @Test
    fun warmupsDropRepeatsAndRespectFloor() {
        val w = Plates.warmups(25.0, 2.5, floorKg = 20.0)
        // 40% and 60% both fall to the empty bar: one set at 20, then 80% (20 again) is dropped.
        assertEquals(listOf(20.0), w.map { it.weightKg })
        assertTrue(Plates.warmups(0.0).isEmpty())
    }
}
