package uk.woozy.fit.model

import kotlin.math.floor
import kotlin.math.round

/**
 * Works out the next target for an exercise from its last sessions (GymMane-style), so the workout can show
 * "Today 62.5 kg x 8, weight up" and fill it in with one tap.
 *
 * Rules:
 * - [Rule.DOUBLE]: work in a rep range. Every set at the top of the range -> add a step and go back to the
 *   bottom. Otherwise keep the weight and aim for one more rep on each set.
 * - [Rule.LINEAR]: a fixed rep target. Every set at the target -> add a step. Otherwise keep the weight.
 * - [Rule.MANUAL]: no suggestion.
 * Either way, [deloadAfter] sessions in a row that missed and did not improve -> go about 10% lighter.
 * Assisted machines (more help = lighter) move the other way: a "step up" takes weight off.
 */
object Progression {
    enum class Rule(val key: String, val label: String) {
        DOUBLE("double", "Double progression"),
        LINEAR("linear", "Linear"),
        MANUAL("manual", "Manual");
        companion object {
            fun fromKey(k: String?): Rule? = entries.firstOrNull { it.key == k }
        }
    }

    enum class Move { UP, MORE_REPS, HOLD, DELOAD, START }

    data class Target(val weightKg: Double, val reps: Int, val move: Move, val why: String)

    data class Settings(
        val rule: Rule,
        val repsMin: Int,
        val repsMax: Int,
        val stepKg: Double,
        val deloadAfter: Int = 3,
        val assisted: Boolean = false,
    )

    /**
     * The rule to use when none was chosen: a rep range means double progression, a single number means linear.
     */
    fun defaultRule(repsMin: Int?, repsMax: Int?): Rule =
        if (repsMin != null && repsMax != null && repsMax > repsMin) Rule.DOUBLE else Rule.LINEAR

    /** [sessions] holds the working sets of past sessions, most recent first. Null when there is nothing to go on. */
    fun next(settings: Settings, sessions: List<List<SetPoint>>): Target? {
        if (settings.rule == Rule.MANUAL) return null
        val last = sessions.firstOrNull()?.filter { it.reps > 0 } ?: return null
        if (last.isEmpty()) return null
        val lo = settings.repsMin.coerceAtLeast(1)
        val hi = settings.repsMax.coerceAtLeast(lo)
        val load = last.maxOf { it.weightKg }
        val step = settings.stepKg.coerceAtLeast(0.0)
        val goal = hi
        fun hit(s: List<SetPoint>): Boolean {
            val top = s.filter { it.reps > 0 }
            val w = top.maxOfOrNull { it.weightKg } ?: return false
            return top.filter { it.weightKg >= w - 1e-9 }.all { it.reps >= goal }
        }
        if (hit(last)) {
            val up = if (settings.assisted) (load - step).coerceAtLeast(0.0) else load + step
            val reps = if (settings.rule == Rule.DOUBLE) lo else hi
            val why = if (settings.assisted) "Every rep done, less help" else "Every rep done, weight up"
            return Target(roundTo(up, step), reps, Move.UP, why)
        }
        if (stallStreak(sessions, ::hit) >= settings.deloadAfter && settings.deloadAfter > 0) {
            val lighter = if (settings.assisted) load * 1.1 else load * 0.9
            return Target(roundTo(lighter, step), lo, Move.DELOAD, "Lighter: ${settings.deloadAfter} sessions without every rep")
        }
        return if (settings.rule == Rule.DOUBLE) {
            val firstReps = last.filter { it.weightKg >= load - 1e-9 }.minOf { it.reps }
            val reps = (firstReps + 1).coerceIn(lo, hi)
            Target(load, reps, Move.MORE_REPS, "Same weight, one more rep")
        } else {
            Target(load, hi, Move.HOLD, "Same weight until every set hits $hi")
        }
    }

    /**
     * How many sessions in a row (most recent first) missed and did not beat the session before at the same weight.
     */
    fun stallStreak(sessions: List<List<SetPoint>>, hit: (List<SetPoint>) -> Boolean): Int {
        var n = 0
        for (i in sessions.indices) {
            val s = sessions[i]
            if (hit(s)) break
            val older = sessions.getOrNull(i + 1)
            if (older != null) {
                val w = s.maxOfOrNull { it.weightKg } ?: 0.0
                val ow = older.maxOfOrNull { it.weightKg } ?: 0.0
                val reps = s.filter { it.weightKg >= w - 1e-9 }.sumOf { it.reps }
                val oreps = older.filter { it.weightKg >= ow - 1e-9 }.sumOf { it.reps }
                if (w > ow + 1e-9 || (kotlin.math.abs(w - ow) < 1e-9 && reps > oreps)) break
            }
            n++
        }
        return n
    }

    /** Rounds down to the nearest [step] (to the nearest 0.01 kg when there is no step). */
    fun roundTo(kg: Double, step: Double): Double {
        val v = if (step > 0) floor(kg / step + 1e-9) * step else kg
        return round(v * 100.0) / 100.0
    }

    /** "62.5 kg x 8" */
    fun label(t: Target): String = "${NumberEntry.format(t.weightKg)} kg x ${t.reps}"
}
