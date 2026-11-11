package uk.woozy.fit.data.imports

import uk.woozy.fit.data.db.ExerciseEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.SetLogEntity
import uk.woozy.fit.data.db.WorkoutSessionEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Brings in the strength history copied from Everfit (they offer no export). The bundled file has one line per
 * exercise per workout day: date, workout name, which one of that name that day, exercise, and the sets as
 * "kgxreps" with "*n" meaning n identical sets, comma separated. Imported workouts carry [MARKER] in scheduledId
 * so they are never sent to Health Connect (Everfit already put them there), and re-running skips days already in.
 */
object EverfitImport {
    const val MARKER = -1L

    class Line(val date: String, val workout: String, val occurrence: Int, val exercise: String, val sets: List<Pair<Double, Int>>)

    fun parseSets(text: String): List<Pair<Double, Int>> = text.split(',').flatMap { part ->
        val p = part.trim()
        if (p.isEmpty()) return@flatMap emptyList()
        val times = p.substringAfter('*', "1").toIntOrNull() ?: 1
        val core = p.substringBefore('*')
        val kg = core.substringBefore('x').toDoubleOrNull() ?: return@flatMap emptyList()
        val reps = core.substringAfter('x', "").toIntOrNull() ?: return@flatMap emptyList()
        List(times) { kg to reps }
    }

    fun parse(text: String): List<Line> = text.lineSequence().mapNotNull { l ->
        val f = l.split('\t')
        if (f.size < 5) return@mapNotNull null
        val sets = parseSets(f[4])
        if (sets.isEmpty()) null else Line(f[0], f[1], f[2].toIntOrNull() ?: 1, f[3], sets)
    }.toList()

    /** Returns the number of workouts added. */
    suspend fun import(db: HqFitDatabase, text: String, zone: ZoneId = ZoneId.systemDefault()): Int {
        val dao = db.sessions()
        val byDay = parse(text).groupBy { Triple(it.date, it.workout, it.occurrence) }
        var added = 0
        for ((key, lines) in byDay) {
            val (date, workout, occ) = key
            val start = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() + (occ - 1) * 3L * 3_600_000L
            if (dao.countAt(start, workout) > 0) continue
            val sessionId = dao.insertSession(
                WorkoutSessionEntity(scheduledId = MARKER, templateId = null, name = workout, startedMs = start, endedMs = start + 3_600_000L, status = "done"),
            )
            val rows = ArrayList<SetLogEntity>()
            lines.forEachIndexed { pos, line ->
                val ex = db.exercises().findByName(line.exercise)
                    ?: ExerciseEntity(name = line.exercise, muscle = "", equipment = "", custom = true).let { it.copy(id = db.exercises().insert(it)) }
                line.sets.forEachIndexed { i, (kg, reps) ->
                    rows += SetLogEntity(
                        sessionId = sessionId, position = pos, setIndex = i, section = "Main", exerciseId = ex.id, exerciseName = ex.name,
                        supersetGroup = null, durationSec = null, targetRepsMin = null, targetRepsMax = null, restSec = 0, note = "",
                        weightKg = kg, reps = reps, done = true, doneAtMs = start,
                    )
                }
            }
            dao.insertSets(rows)
            added++
        }
        return added
    }
}
