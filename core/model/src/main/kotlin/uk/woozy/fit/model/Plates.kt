package uk.woozy.fit.model

import kotlin.math.floor
import kotlin.math.round

/** Plate maths for barbell exercises, and the warm-up ramp before the first working set. */
object Plates {
    val standard = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    /**
     * The plates to load on each side for [totalKg] on a [barKg] bar, heaviest first. Null when the weight is not
     * above the bar or cannot be made exactly with [plates] (then the nearest lower load is not guessed at).
     */
    fun perSide(totalKg: Double, barKg: Double = 20.0, plates: List<Double> = standard): List<Double>? {
        var side = (totalKg - barKg) / 2.0
        if (side <= 0.0) return null
        val out = mutableListOf<Double>()
        for (p in plates.sortedDescending()) {
            while (side >= p - 1e-9) { out += p; side -= p }
        }
        return if (side > 1e-6) null else out
    }

    /** "20 + 5 + 1.25" for a list from [perSide]. */
    fun label(plates: List<Double>): String = plates.joinToString(" + ") { NumberEntry.format(it) }

    /** One warm-up set: weight and reps. */
    data class Warmup(val weightKg: Double, val reps: Int)

    /**
     * Three ramp-up sets before a working weight: 40% x 10, 60% x 5, 80% x 3, each rounded down to [stepKg] and
     * never below [floorKg] (an empty bar). Sets that would repeat the same weight are dropped. Empty when the
     * working weight is too light to warm up for.
     */
    fun warmups(workingKg: Double, stepKg: Double = 2.5, floorKg: Double = 0.0): List<Warmup> {
        if (workingKg <= 0.0 || stepKg <= 0.0) return emptyList()
        val ramp = listOf(0.4 to 10, 0.6 to 5, 0.8 to 3)
        val out = mutableListOf<Warmup>()
        ramp.forEach { (pct, reps) ->
            val w = maxOf(floorKg, floor(workingKg * pct / stepKg) * stepKg)
            val kg = round(w * 100.0) / 100.0
            if (kg > 0.0 && kg < workingKg && out.none { it.weightKg == kg }) out += Warmup(kg, reps)
        }
        return out
    }
}
