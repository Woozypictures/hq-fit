package uk.woozy.fit.data.db

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Keep my order: the plan's workouts are done in their order on whatever day you train. Today always offers the next
 * one in the order (a missed one comes first); starting it moves it to today. When the one you start was missed,
 * everything after it slides on by the same number of days, so nothing is left behind in the past.
 */
object Rotation {
    private const val KEY = "keep_order"

    suspend fun on(db: HqFitDatabase): Boolean = db.meta().get(KEY) == "1"
    suspend fun set(db: HqFitDatabase, on: Boolean) = db.meta().put(MetaEntity(KEY, if (on) "1" else "0"))

    /** The next planned workout in order, looking back two weeks for missed ones. */
    fun upNext(rows: List<ScheduledWorkoutEntity>, today: LocalDate): ScheduledWorkoutEntity? =
        rows.filter { it.status == "planned" && it.date >= today.minusDays(14).toString() }.minWithOrNull(compareBy({ it.date }, { it.id }))

    suspend fun upNext(db: HqFitDatabase, today: LocalDate): ScheduledWorkoutEntity? =
        upNext(db.schedule().between(today.minusDays(14).toString(), today.plusDays(120).toString()), today)

    /** Moves [row] to today; if it was missed, the planned ones after it move on by the same gap. */
    suspend fun bringToToday(db: HqFitDatabase, row: ScheduledWorkoutEntity, today: LocalDate) {
        val from = LocalDate.parse(row.date)
        if (from == today) return
        val late = ChronoUnit.DAYS.between(from, today)
        if (late > 0) {
            db.schedule().between(row.date, today.plusDays(365).toString())
                .filter { it.status == "planned" && it.id != row.id && (it.date > row.date || (it.date == row.date && it.id > row.id)) }
                .forEach { db.schedule().move(it.id, LocalDate.parse(it.date).plusDays(late).toString()) }
        }
        db.schedule().move(row.id, today.toString())
    }
}
