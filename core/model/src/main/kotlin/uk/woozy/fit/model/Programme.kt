package uk.woozy.fit.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class Section(val key: String, val title: String) {
    WARM_UP("warm_up", "Warm up"),
    WORKOUT("workout", "Workout"),
    COOL_DOWN("cool_down", "Cool down"),
    ;

    companion object {
        fun fromKey(key: String): Section = entries.firstOrNull { it.key == key } ?: WORKOUT
    }
}

/** One line of a workout. Reps are a range ("8-12"); a timed item uses [durationSec] instead. */
data class PlanItem(
    val exercise: String,
    val section: Section,
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val durationSec: Int? = null,
    val weightKg: Double? = null,
    val restSec: Int = 0,
    /** Items with the same non-null group are a superset; rest goes after the last one. */
    val superset: Int? = null,
    val note: String = "",
    val muscle: String = "",
    val equipment: String = "",
) {
    val repsText: String
        get() = when {
            durationSec != null -> formatDuration(durationSec)
            repsMin != null && repsMax != null && repsMin != repsMax -> "$repsMin-$repsMax"
            repsMax != null -> "$repsMax"
            repsMin != null -> "$repsMin"
            else -> ""
        }
}

data class PlanWorkout(val name: String, val focus: String, val items: List<PlanItem>)

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** A weekday rule: this workout name on this weekday. */
data class DayRule(val weekday: DayOfWeek, val workout: String)

data class PlannedDay(val date: LocalDate, val workout: String)

object Scheduler {
    /**
     * Lays a programme onto the calendar: for [weeks] weeks starting at the Monday on or before [start],
     * every rule produces one dated workout. Dates before [start] are left out.
     */
    fun generate(start: LocalDate, weeks: Int, rules: List<DayRule>): List<PlannedDay> {
        val firstMonday = start.with(DayOfWeek.MONDAY)
        return (0 until weeks).flatMap { w ->
            rules.map { r -> PlannedDay(firstMonday.plusWeeks(w.toLong()).with(r.weekday), r.workout) }
        }.filter { it.date >= start }.sortedBy { it.date }
    }

    /** A scheduled day that has moved: the date changes, the original date stays what it was first. */
    data class Slot(val date: LocalDate, val originalDate: LocalDate)

    fun move(slot: Slot, to: LocalDate): Slot = slot.copy(date = to)

    fun isMissed(slot: Slot, today: LocalDate, done: Boolean): Boolean = !done && slot.date < today
}

/** The programme in use today, from the Everfit handover of 11 Nov 2026. Starting weights are working guesses. */
object FullBodyProgramme {
    const val NAME = "Full body A / B / C"
    const val WEEKS = 10

    private fun w(
        name: String, section: Section, sets: Int, min: Int?, max: Int?, kg: Double?, rest: Int,
        muscle: String, equipment: String, superset: Int? = null, note: String = "", duration: Int? = null,
    ) = PlanItem(name, section, sets, min, max, duration, kg, rest, superset, note, muscle, equipment)

    private fun warmUp() = listOf(
        w("Treadmill Walk", Section.WARM_UP, 1, null, null, null, 0, "cardiovascular system", "leverage machine", duration = 600, note = "Incline walk"),
        w("Shoulder Stretch", Section.WARM_UP, 1, null, null, null, 0, "delts", "body weight", duration = 120),
        w("Overhead Stretch", Section.WARM_UP, 1, null, null, null, 0, "delts", "body weight", duration = 60),
        w("Upward Stretch", Section.WARM_UP, 1, null, null, null, 0, "spine", "body weight", duration = 60),
        w("Triceps Stretch", Section.WARM_UP, 1, null, null, null, 0, "triceps", "body weight", duration = 60),
    )

    private fun prehab() = w(
        "External Rotation with Cable", Section.WORKOUT, 2, 15, 15, 5.0, 0, "delts", "cable",
        note = "Prehab for the left shoulder",
    )

    private fun coolDown() = listOf(
        w("Treadmill Walk", Section.COOL_DOWN, 1, null, null, null, 0, "cardiovascular system", "leverage machine", duration = 600, note = "Incline walk"),
    )

    private fun main(name: String, min: Int, max: Int, kg: Double?, rest: Int, muscle: String, eq: String, note: String = "") =
        w(name, Section.WORKOUT, 2, min, max, kg, rest, muscle, eq, note = note)

    private fun armPair(a: PlanItem, b: PlanItem) = listOf(a.copy(superset = 1, restSec = 0), b.copy(superset = 1, restSec = 90))

    val workouts: List<PlanWorkout> = listOf(
        PlanWorkout(
            "Fullbody A", "Quad focus",
            warmUp() + prehab() + listOf(
                main("Decline Chest Press Machine", 8, 12, 50.0, 180, "pectorals", "leverage machine"),
                main("Seated Cable Rows", 8, 12, 50.0, 180, "upper back", "cable"),
                main("Leg Press", 10, 15, 100.0, 180, "quads", "leverage machine", "Feet high on platform. Pain-free depth only (left knee)."),
                main("Hyperextensions (Back Extensions)", 10, 15, null, 180, "spine", "body weight"),
                main("Machine Lateral Raise", 12, 15, 35.0, 120, "delts", "leverage machine"),
            ) + armPair(
                main("Preacher Curl", 10, 12, 22.5, 0, "biceps", "ez barbell"),
                main("Cable Triceps Pushdown", 10, 12, 30.0, 0, "triceps", "cable"),
            ) + coolDown(),
        ),
        PlanWorkout(
            "Fullbody B", "Hinge focus",
            warmUp() + prehab() + listOf(
                main("Wide-Grip Lat Pulldown", 8, 12, 45.0, 180, "lats", "cable"),
                main("Leverage Chest Press", 8, 12, 50.0, 180, "pectorals", "leverage machine"),
                main(
                    "Romanian Deadlift With Dumbbells", 8, 10, 10.0, 180, "glutes", "dumbbell",
                    "Start light (per dumbbell) and learn the hinge. Smith machine RDL is fine instead.",
                ),
                main("Leg Extensions", 12, 15, 50.0, 120, "quads", "leverage machine"),
                main("Lying Leg Curls", 10, 12, 39.0, 120, "hamstrings", "leverage machine"),
            ) + armPair(
                main("Hammer Curl", 10, 12, 12.0, 0, "biceps", "dumbbell"),
                main("Cable Rope Overhead Tricep Extension", 10, 12, 30.0, 0, "triceps", "cable"),
            ) + coolDown(),
        ),
        PlanWorkout(
            "Fullbody C", "Upper and back focus",
            warmUp() + prehab() + listOf(
                main(
                    "Leverage Shoulder Press", 8, 12, 40.0, 180, "delts", "leverage machine",
                    "Only if pain-free for the left shoulder. If it hurts, swap to Landmine Press.",
                ),
                main("Machine-Assisted Pull-Up", 8, 12, 50.0, 180, "lats", "leverage machine", "50 kg is the assistance, not the load"),
                main("Hack Squat", 10, 12, 50.0, 180, "quads", "leverage machine", "Stop above the painful range for the left knee."),
                main("Barbell Hip Thrust", 10, 12, 40.0, 180, "glutes", "barbell", "Smith machine or barbell. Start moderate."),
                main("Reverse Machine Flyes", 12, 15, 50.0, 120, "delts", "leverage machine"),
            ) + armPair(
                main("Barbell Curl", 10, 12, 20.0, 0, "biceps", "barbell"),
                main("Machine Fly", 10, 12, 45.0, 0, "pectorals", "leverage machine"),
            ) + coolDown(),
        ),
    )

    /** Monday A, Wednesday B, Friday C. */
    val rules = listOf(
        DayRule(DayOfWeek.MONDAY, "Fullbody A"),
        DayRule(DayOfWeek.WEDNESDAY, "Fullbody B"),
        DayRule(DayOfWeek.FRIDAY, "Fullbody C"),
    )
}
