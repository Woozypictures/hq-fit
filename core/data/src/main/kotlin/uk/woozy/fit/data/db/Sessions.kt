package uk.woozy.fit.data.db

import uk.woozy.fit.model.Plates
import uk.woozy.fit.model.RecordType
import uk.woozy.fit.model.SetPoint
import uk.woozy.fit.model.Strength

/** One exercise's records after a session. */
data class ExerciseRecord(val exerciseName: String, val records: Set<RecordType>, val best: SetPoint)

data class SessionSummary(
    val name: String,
    val durationMs: Long,
    val setsDone: Int,
    val setsTotal: Int,
    val volumeKg: Double,
    val records: List<ExerciseRecord>,
)

/** Starting, finishing and summarising workouts. */
object Sessions {
    /**
     * Starts the scheduled workout, or returns the one already running. Sets are laid out from the template; the
     * weight starts at last time's weight for that set number, else the programme's.
     */
    suspend fun start(db: HqFitDatabase, scheduled: ScheduledWorkoutEntity, nowMs: Long, stepKg: Double = 2.5, barKg: Double = 20.0): Long {
        db.sessions().active()?.let { return it.id }
        val sessionId = db.sessions().insertSession(
            WorkoutSessionEntity(scheduledId = scheduled.id, templateId = scheduled.templateId, name = scheduled.templateName, startedMs = nowMs),
        )
        val items = db.templates().items(scheduled.templateId)
        val rows = ArrayList<SetLogEntity>()
        val prefs = db.exercisePrefs().all().associateBy { it.exerciseId }
        items.forEach { item ->
            val pref = prefs[item.exerciseId]
            // A note pinned last time (seat height, grip) carries over when the programme has none of its own.
            val note = item.note.ifBlank { db.sessions().lastNote(item.exerciseId, sessionId).orEmpty() }
            for (i in 1..item.sets) {
                val prev = if (item.durationSec == null) db.sessions().previous(item.exerciseId, i, sessionId) else null
                rows += SetLogEntity(
                    sessionId = sessionId, position = item.position, setIndex = i, section = item.section,
                    exerciseId = item.exerciseId, exerciseName = item.exerciseName, supersetGroup = item.supersetGroup,
                    durationSec = item.durationSec, targetRepsMin = item.repsMin, targetRepsMax = item.repsMax,
                    restSec = pref?.restSec ?: item.restSec, note = note,
                    weightKg = prev?.weightKg ?: item.weightKg, reps = null,
                )
            }
        }
        db.sessions().insertSets(rows)
        // Exercises set to start with warm-ups get them now (barbell lifts never go below the empty bar).
        items.filter { prefs[it.exerciseId]?.warmups == true && it.durationSec == null }.map { it.position }.distinct().forEach { pos ->
            val item = items.first { it.position == pos }
            val barbell = db.sessions().exercise(item.exerciseId)?.equipment?.contains("barbell", ignoreCase = true) == true
            addWarmups(db, sessionId, pos, prefs[item.exerciseId]?.stepKg ?: stepKg, if (barbell) prefs[item.exerciseId]?.barKg ?: barKg else null)
        }
        return sessionId
    }

    /** Marks the session done and its scheduled day done. Unticked sets stay as they were, not counted. */
    suspend fun finish(db: HqFitDatabase, sessionId: Long, nowMs: Long, feeling: Int? = null) {
        val s = db.sessions().get(sessionId) ?: return
        // A workout logged for a past day (started more than 12 hours ago) gets a nominal hour, not days.
        val end = if (nowMs - s.startedMs > 12 * 3_600_000L) s.startedMs + 3_600_000L else nowMs
        db.sessions().finish(sessionId, end, feeling)
        s.scheduledId?.let { db.schedule().setStatus(it, "done") }
    }

    /**
     * Logs a workout for a past day (GymMane's "Log a workout" on the calendar): plans [templateId] on [date],
     * starts it at 18:00 that day and returns the session to fill in. Finishing gives it an hour.
     */
    suspend fun startPast(db: HqFitDatabase, templateId: Long, date: java.time.LocalDate, stepKg: Double, barKg: Double): Long? {
        val t = db.templates().get(templateId) ?: return null
        val d = date.toString()
        val sid = db.schedule().insert(ScheduledWorkoutEntity(programmeId = null, templateId = t.id, templateName = t.name, date = d, originalDate = d))
        val sched = db.schedule().get(sid) ?: return null
        val at = date.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return start(db, sched, at, stepKg, barKg)
    }

    /** Throws the session away (a started-by-mistake workout). The calendar day stays planned. */
    suspend fun discard(db: HqFitDatabase, sessionId: Long) = db.sessions().deleteSession(sessionId)

    suspend fun summary(db: HqFitDatabase, sessionId: Long, nowMs: Long): SessionSummary? {
        val s = db.sessions().get(sessionId) ?: return null
        val sets = db.sessions().sets(sessionId)
        val done = sets.filter { it.done }
        val lifted = done.filter { it.durationSec == null && it.setType != "warmup" && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
            .map { it to SetPoint(it.weightKg!!, it.reps!!) }
        val records = lifted.groupBy { it.first.exerciseId }.mapNotNull { (exerciseId, pairs) ->
            val today = pairs.map { it.second }
            val history = db.sessions().history(exerciseId, sessionId).map { SetPoint(it.weightKg!!, it.reps!!) }
            val broken = Strength.records(history, today)
            if (broken.isEmpty()) null else ExerciseRecord(pairs.first().first.exerciseName, broken, today.maxBy { Strength.e1rm(it) })
        }
        return SessionSummary(
            name = s.name,
            durationMs = (s.endedMs ?: nowMs) - s.startedMs,
            setsDone = done.size,
            setsTotal = sets.size,
            volumeKg = Strength.volume(lifted.map { it.second }),
            records = records,
        )
    }

    /**
     * Puts ramp-up warm-up sets (see [Plates.warmups]) before the first working set of the exercise at [position],
     * replacing any unticked warm-ups already there. They get negative set numbers so the working sets keep 1, 2, 3
     * (and so "last time" still lines up), show as W, and are left out of volume, records and history.
     * Returns how many were added; 0 when there is no working weight yet.
     */
    suspend fun addWarmups(db: HqFitDatabase, sessionId: Long, position: Int, stepKg: Double, barKg: Double?): Int {
        val group = db.sessions().sets(sessionId).filter { it.position == position }
        val work = group.filter { it.setType != "warmup" }.minByOrNull { it.setIndex } ?: return 0
        if (work.durationSec != null) return 0
        val kg = work.weightKg ?: db.sessions().previous(work.exerciseId, work.setIndex, sessionId)?.weightKg ?: return 0
        val ramp = Plates.warmups(kg, stepKg, barKg ?: 0.0)
        if (ramp.isEmpty()) return 0
        db.sessions().deleteOpenWarmups(sessionId, position)
        val firstIndex = (group.filter { it.setType == "warmup" && it.done }.minOfOrNull { it.setIndex } ?: 0) - ramp.size
        db.sessions().insertSets(
            ramp.mapIndexed { i, w ->
                work.copy(
                    id = 0, setIndex = firstIndex + i, setType = "warmup", weightKg = w.weightKg, reps = w.reps,
                    targetRepsMin = w.reps, targetRepsMax = w.reps, restSec = 60, done = false, doneAtMs = null, rpe = null,
                )
            },
        )
        return ramp.size
    }

    /**
     * Makes a workout (template) from what you actually did in a session: each exercise with its working-set count,
     * the rep range you hit, the last weight, rest, superset and note. Warm-ups are left out. Returns the new id.
     */
    suspend fun saveAsTemplate(db: HqFitDatabase, sessionId: Long, name: String): Long? {
        val sets = db.sessions().sets(sessionId).filter { it.setType != "warmup" }
        if (sets.isEmpty()) return null
        val templateId = db.templates().insert(WorkoutTemplateEntity(name = name.ifBlank { "Saved workout" }))
        val items = sets.groupBy { it.position }.toSortedMap().values.mapIndexed { i, g ->
            val f = g.first()
            val done = g.filter { it.done }.ifEmpty { g }
            val reps = done.mapNotNull { it.reps }
            TemplateItemEntity(
                templateId = templateId, section = f.section, position = i, exerciseId = f.exerciseId, sets = g.size,
                repsMin = reps.minOrNull() ?: f.targetRepsMin, repsMax = reps.maxOrNull() ?: f.targetRepsMax,
                durationSec = f.durationSec, weightKg = done.lastOrNull { it.weightKg != null }?.weightKg,
                restSec = g.maxOf { it.restSec }, supersetGroup = f.supersetGroup, note = f.note,
            )
        }
        db.templates().insertItems(items)
        return templateId
    }

    /** Swaps the exercise in a position for another. Weights stay, ticks and reps are cleared. */
    suspend fun replace(db: HqFitDatabase, sessionId: Long, position: Int, exercise: ExerciseEntity) =
        db.sessions().replaceExercise(sessionId, position, exercise.id, exercise.name)

    enum class Where { BEFORE, AFTER, SUPERSET }

    /**
     * Adds an exercise (two sets) next to the one at [position]. A superset joins the new exercise to the anchor,
     * giving both a shared group; rest then belongs after the second one.
     */
    suspend fun add(db: HqFitDatabase, sessionId: Long, position: Int, exercise: ExerciseEntity, where: Where) {
        val anchor = db.sessions().sets(sessionId).firstOrNull { it.position == position } ?: return
        val newPosition = when (where) {
            Where.BEFORE -> { db.sessions().shiftFrom(sessionId, position); position }
            Where.AFTER, Where.SUPERSET -> { db.sessions().shiftAfter(sessionId, position); position + 1 }
        }
        var group: Int? = null
        if (where == Where.SUPERSET) {
            group = anchor.supersetGroup ?: (db.sessions().maxSuperset(sessionId) + 1)
            db.sessions().setSuperset(sessionId, position, group)
        }
        db.sessions().insertSets(
            (1..2).map { i ->
                SetLogEntity(
                    sessionId = sessionId, position = newPosition, setIndex = i, section = anchor.section,
                    exerciseId = exercise.id, exerciseName = exercise.name, supersetGroup = group,
                    durationSec = null, targetRepsMin = null, targetRepsMax = null,
                    restSec = if (where == Where.SUPERSET) (if (i == 2) 90 else 0) else 120, note = "",
                    weightKg = null, reps = null,
                )
            },
        )
    }
}
