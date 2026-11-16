package uk.woozy.fit.data

import java.io.BufferedReader
import uk.woozy.fit.data.db.HqFitDatabase

/**
 * Muscle map maths. Every exercise's main muscle counts a full set and each helping (secondary) muscle half a set,
 * grouped into the 17 regions the body diagram draws. Warm-ups never count. Secondary muscles come from the bundled
 * catalogue (exercises.tsv), matched by name, so no database change is needed; custom exercises count their main muscle only.
 */
object Muscles {
    data class Region(val key: String, val name: String)

    val REGIONS = listOf(
        Region("chest", "Chest"), Region("shoulders", "Shoulders"), Region("biceps", "Biceps"), Region("triceps", "Triceps"),
        Region("forearms", "Forearms"), Region("abs", "Abs"), Region("obliques", "Obliques"), Region("traps", "Traps"),
        Region("lats", "Lats"), Region("upperback", "Upper back"), Region("lowerback", "Lower back"), Region("glutes", "Glutes"),
        Region("quads", "Quads"), Region("hamstrings", "Hamstrings"), Region("calves", "Calves"), Region("adductors", "Adductors"),
    )
    val NAMES = REGIONS.associate { it.key to it.name }

    /** The regions worth nudging about when they fall behind (small helpers like forearms are left out). */
    val MAIN = listOf("chest", "shoulders", "biceps", "triceps", "abs", "lats", "upperback", "glutes", "quads", "hamstrings", "calves")

    /** Weekly working sets that count as fully trained on the map (a common 10-a-week guide). */
    const val WEEK_FULL = 10.0

    /** Any catalogue, Everfit or typed muscle name to a region key, or null for things the map does not draw (cardio, feet). */
    fun region(raw: String): String? {
        val m = raw.trim().lowercase()
        if (m.isEmpty()) return null
        return when {
            m.contains("pector") || m.contains("chest") || m.contains("serratus") -> "chest"
            m.contains("rear delt") || m.contains("delt") || m.contains("shoulder") || m.contains("rotator") -> "shoulders"
            m.contains("tricep") -> "triceps"
            m.contains("bicep") || m.contains("brachialis") -> "biceps"
            m.contains("forearm") || m.contains("wrist") || m.contains("grip") || m == "hands" -> "forearms"
            m.contains("oblique") -> "obliques"
            m.contains("abs") || m.contains("abdom") || m == "core" || m.contains("waist") -> "abs"
            m.contains("trap") || m.contains("levator") || m.contains("sternocleido") || m == "neck" -> "traps"
            m.contains("lat") -> "lats"
            m.contains("lower back") || m == "spine" || m.contains("erector") -> "lowerback"
            m.contains("upper back") || m.contains("rhomboid") || m == "back" -> "upperback"
            m.contains("glute") || m.contains("abductor") || m == "hips" -> "glutes"
            m.contains("adductor") || m.contains("inner thigh") || m == "groin" -> "adductors"
            m.contains("hamstring") -> "hamstrings"
            m.contains("quad") || m.contains("hip flexor") || m == "legs" || m.contains("upper legs") -> "quads"
            m.contains("calf") || m.contains("calves") || m.contains("soleus") || m.contains("shin") || m.contains("lower legs") -> "calves"
            else -> null
        }
    }

    /** Lower-case exercise name to its secondary muscles, read from the bundled catalogue. */
    fun secondaries(reader: BufferedReader): Map<String, List<String>> {
        val out = HashMap<String, List<String>>(6000)
        reader.useLines { lines ->
            lines.forEach { line ->
                val f = line.split('\t')
                if (f.size >= 6 && f[1].isNotBlank() && f[5].isNotBlank()) out[f[1].trim().lowercase()] = f[5].split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        return out
    }

    /** Sets per region and when each region was last worked (ms). */
    data class Load(val sets: Map<String, Double>, val lastHit: Map<String, Long>, val workouts: Int)

    /** Region shares for one exercise: main muscle 1, each different helping region 0.5. */
    fun shares(primary: String, secondary: List<String>): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        region(primary)?.let { out[it] = 1.0 }
        secondary.mapNotNull { region(it) }.distinct().forEach { r -> if (r !in out) out[r] = 0.5 }
        return out
    }

    suspend fun load(db: HqFitDatabase, sinceMs: Long, secondary: Map<String, List<String>>): Load {
        val sets = HashMap<String, Double>()
        val last = HashMap<String, Long>()
        val cache = HashMap<Long, Map<String, Double>>()
        val sessions = db.sessions().doneSince(sinceMs)
        sessions.forEach { s ->
            db.sessions().sets(s.id).filter { it.done && it.setType != "warmup" }.forEach { l ->
                val share = cache.getOrPut(l.exerciseId) {
                    val ex = db.exercises().findById(l.exerciseId)
                    shares(ex?.muscle ?: "", secondary[(ex?.name ?: l.exerciseName).lowercase()].orEmpty())
                }
                val at = l.doneAtMs ?: s.startedMs
                share.forEach { (r, v) ->
                    sets[r] = (sets[r] ?: 0.0) + v
                    // Recovery follows the main muscle only: a helper doing half the work is not "just trained".
                    if (v >= 1.0 && at > (last[r] ?: 0L)) last[r] = at
                }
            }
        }
        return Load(sets, last, sessions.size)
    }

    /** Main regions under a third of the weekly guide, fewest first (at most three). Empty when nothing was trained. */
    fun behind(load: Load, weeks: Double = 1.0): List<String> {
        if (load.workouts == 0) return emptyList()
        return MAIN.map { it to (load.sets[it] ?: 0.0) }.filter { it.second < WEEK_FULL * weeks / 3 }.sortedBy { it.second }.take(3).map { it.first }
    }
}
