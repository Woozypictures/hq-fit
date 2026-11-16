package uk.woozy.fit.data.db

import uk.woozy.fit.model.Progression
import uk.woozy.fit.model.SetPoint

/** Next targets for exercises, from your settings for each exercise and its last finished sessions. */
object Coach {
    /** The settings in force for [exerciseId]: your own, else the programme's rep range, else the app's step. */
    fun settings(pref: ExercisePrefEntity?, repsMin: Int?, repsMax: Int?, globalStepKg: Double): Progression.Settings {
        val lo = pref?.repsMin ?: repsMin ?: repsMax ?: 8
        val hi = (pref?.repsMax ?: repsMax ?: lo).coerceAtLeast(lo)
        val rule = Progression.Rule.fromKey(pref?.rule) ?: Progression.defaultRule(lo, hi)
        return Progression.Settings(
            rule = rule, repsMin = lo, repsMax = hi,
            stepKg = pref?.stepKg ?: globalStepKg,
            deloadAfter = pref?.deloadAfter ?: 3,
            assisted = pref?.assisted ?: false,
        )
    }

    /** Working sets of the last [limit] finished sessions of this exercise, most recent first. */
    suspend fun recent(db: HqFitDatabase, exerciseId: Long, limit: Int = 6): List<List<SetPoint>> =
        db.sessions().exerciseHistory(exerciseId)
            .groupBy { it.sessionId }.values
            .take(limit)
            .map { rows -> rows.map { SetPoint(it.weightKg, it.reps) } }
            .filter { it.isNotEmpty() }

    suspend fun next(db: HqFitDatabase, exerciseId: Long, repsMin: Int?, repsMax: Int?, globalStepKg: Double): Progression.Target? {
        val pref = db.exercisePrefs().get(exerciseId)
        return Progression.next(settings(pref, repsMin, repsMax, globalStepKg), recent(db, exerciseId))
    }
}
