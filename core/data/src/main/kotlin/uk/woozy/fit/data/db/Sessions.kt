package uk.woozy.fit.data.db

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
    suspend fun start(db: HqFitDatabase, scheduled: ScheduledWorkoutEntity, nowMs: Long): Long {
        db.sessions().active()?.let { return it.id }
        val sessionId = db.sessions().insertSession(
            WorkoutSessionEntity(scheduledId = scheduled.id, templateId = scheduled.templateId, name = scheduled.templateName, startedMs = nowMs),
        )
        val items = db.templates().items(scheduled.templateId)
        val rows = ArrayList<SetLogEntity>()
        items.forEach { item ->
            for (i in 1..item.sets) {
                val prev = if (item.durationSec == null) db.sessions().previous(item.exerciseId, i, sessionId) else null
                rows += SetLogEntity(
                    sessionId = sessionId, position = item.position, setIndex = i, section = item.section,
                    exerciseId = item.exerciseId, exerciseName = item.exerciseName, supersetGroup = item.supersetGroup,
                    durationSec = item.durationSec, targetRepsMin = item.repsMin, targetRepsMax = item.repsMax,
                    restSec = item.restSec, note = item.note,
                    weightKg = prev?.weightKg ?: item.weightKg, reps = null,
                )
            }
        }
        db.sessions().insertSets(rows)
        return sessionId
    }

    /** Marks the session done and its scheduled day done. Unticked sets stay as they were, not counted. */
    suspend fun finish(db: HqFitDatabase, sessionId: Long, nowMs: Long, feeling: Int? = null) {
        val s = db.sessions().get(sessionId) ?: return
        db.sessions().finish(sessionId, nowMs, feeling)
        s.scheduledId?.let { db.schedule().setStatus(it, "done") }
    }

    /** Throws the session away (a started-by-mistake workout). The calendar day stays planned. */
    suspend fun discard(db: HqFitDatabase, sessionId: Long) = db.sessions().deleteSession(sessionId)

    suspend fun summary(db: HqFitDatabase, sessionId: Long, nowMs: Long): SessionSummary? {
        val s = db.sessions().get(sessionId) ?: return null
        val sets = db.sessions().sets(sessionId)
        val done = sets.filter { it.done }
        val lifted = done.filter { it.durationSec == null && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
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
}
