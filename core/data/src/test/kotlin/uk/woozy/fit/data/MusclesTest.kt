package uk.woozy.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusclesTest {
    @Test fun catalogueNamesMapToRegions() {
        assertEquals("chest", Muscles.region("pectorals"))
        assertEquals("shoulders", Muscles.region("rear deltoids"))
        assertEquals("triceps", Muscles.region("triceps"))
        assertEquals("lats", Muscles.region("latissimus dorsi"))
        assertEquals("upperback", Muscles.region("rhomboids"))
        assertEquals("lowerback", Muscles.region("spine"))
        assertEquals("glutes", Muscles.region("abductors"))
        assertEquals("adductors", Muscles.region("adductors"))
        assertEquals("abs", Muscles.region("lower abs"))
        assertEquals("obliques", Muscles.region("obliques"))
        assertEquals("calves", Muscles.region("soleus"))
        assertNull(Muscles.region("cardiovascular system"))
    }

    @Test fun benchCountsChestFullAndHelpersHalf() {
        val s = Muscles.shares("pectorals", listOf("triceps", "shoulders"))
        assertEquals(mapOf("chest" to 1.0, "triceps" to 0.5, "shoulders" to 0.5), s)
    }

    @Test fun secondaryInSameRegionIsNotDoubleCounted() {
        assertEquals(mapOf("abs" to 1.0), Muscles.shares("abs", listOf("lower abs", "core")))
    }
}
