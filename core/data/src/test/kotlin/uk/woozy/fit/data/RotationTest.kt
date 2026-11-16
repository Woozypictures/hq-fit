package uk.woozy.fit.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.woozy.fit.data.db.Rotation
import uk.woozy.fit.data.db.ScheduledWorkoutEntity

class RotationTest {
    private fun row(id: Long, date: String, status: String = "planned") = ScheduledWorkoutEntity(id = id, programmeId = 1, templateId = id, templateName = "W$id", date = date, originalDate = date, status = status)

    @Test fun missedComesFirst() {
        val rows = listOf(row(1, "2026-11-14", "done"), row(2, "2026-11-15"), row(3, "2026-11-18"))
        assertEquals(2L, Rotation.upNext(rows, LocalDate.parse("2026-11-16"))?.id)
    }

    @Test fun nextFutureOneWhenNothingMissed() {
        val rows = listOf(row(1, "2026-11-14", "done"), row(3, "2026-11-18"), row(4, "2026-11-20"))
        assertEquals(3L, Rotation.upNext(rows, LocalDate.parse("2026-11-16"))?.id)
    }

    @Test fun ignoresVeryOldMissed() {
        val rows = listOf(row(1, "2026-10-01"), row(3, "2026-11-18"))
        assertEquals(3L, Rotation.upNext(rows, LocalDate.parse("2026-11-16"))?.id)
    }
}
