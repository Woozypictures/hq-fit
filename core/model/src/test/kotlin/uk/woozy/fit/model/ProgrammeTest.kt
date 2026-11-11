package uk.woozy.fit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ProgrammeTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun threeDaySplitFillsMondayWednesdayFriday() {
        val plan = Scheduler.generate(monday, 2, FullBodyProgramme.rules)
        assertEquals(6, plan.size)
        assertEquals(listOf("Fullbody A", "Fullbody B", "Fullbody C", "Fullbody A", "Fullbody B", "Fullbody C"), plan.map { it.workout })
        assertEquals(LocalDate.of(2026, 11, 4), plan[1].date)
        assertEquals(LocalDate.of(2026, 11, 9), plan[3].date)
    }

    @Test
    fun startingMidWeekDropsEarlierDays() {
        val plan = Scheduler.generate(LocalDate.of(2026, 11, 11), 1, FullBodyProgramme.rules)
        assertEquals(listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 13)), plan.map { it.date })
        assertEquals("Fullbody B", plan.first().workout)
    }

    @Test
    fun fourAndFiveDaySplitsWork() {
        val four = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).map { DayRule(it, "x") }
        val five = four + DayRule(DayOfWeek.SATURDAY, "x")
        assertEquals(8, Scheduler.generate(monday, 2, four).size)
        assertEquals(10, Scheduler.generate(monday, 2, five).size)
    }

    @Test
    fun rescheduleKeepsOriginalDate() {
        val slot = Scheduler.Slot(date = LocalDate.of(2026, 11, 4), originalDate = LocalDate.of(2026, 11, 4))
        val moved = Scheduler.move(slot, LocalDate.of(2026, 11, 5))
        assertEquals(LocalDate.of(2026, 11, 5), moved.date)
        assertEquals(LocalDate.of(2026, 11, 4), moved.originalDate)
        val movedAgain = Scheduler.move(moved, LocalDate.of(2026, 11, 6))
        assertEquals(LocalDate.of(2026, 11, 4), movedAgain.originalDate)
    }

    @Test
    fun missedMeansPastAndNotDone() {
        val slot = Scheduler.Slot(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4))
        val today = LocalDate.of(2026, 11, 5)
        assertTrue(Scheduler.isMissed(slot, today, done = false))
        assertFalse(Scheduler.isMissed(slot, today, done = true))
        assertFalse(Scheduler.isMissed(slot, LocalDate.of(2026, 11, 4), done = false))
    }

    @Test
    fun programmeMatchesTheHandover() {
        val (a, b, c) = FullBodyProgramme.workouts
        listOf(a, b, c).forEach { w ->
            assertEquals(5, w.items.count { it.section == Section.WARM_UP })
            assertEquals(1, w.items.count { it.section == Section.COOL_DOWN })
            // prehab + 5 main + 2 arm exercises
            assertEquals(8, w.items.count { it.section == Section.WORKOUT })
            val sup = w.items.filter { it.superset != null }
            assertEquals(2, sup.size)
            assertEquals(0, sup[0].restSec)
            assertEquals(90, sup[1].restSec)
            assertTrue(w.items.filter { it.section == Section.WORKOUT && it.superset == null && it.exercise != "External Rotation with Cable" }.all { it.sets == 2 })
        }
        val leg = a.items.first { it.exercise == "Leg Press" }
        assertEquals("10-15", leg.repsText)
        assertEquals(100.0, leg.weightKg!!, 0.0)
        assertEquals("10:00", a.items.first().repsText)
        assertEquals(10.0, b.items.first { it.exercise.startsWith("Romanian") }.weightKg!!, 0.0)
        assertEquals("Fullbody C", c.name)
    }
}
