package uk.woozy.fit.model

/** A weight on a day, [day] counted in days from any fixed starting point. */
data class DayValue(val day: Double, val value: Double)

object Trend {
    /**
     * Least-squares slope in units per day. Needs at least three points spread over at least a week, otherwise null:
     * weighing rarely gives few points, and a slope from two readings a day apart would be noise.
     */
    fun slopePerDay(points: List<DayValue>): Double? {
        if (points.size < 3) return null
        if (points.maxOf { it.day } - points.minOf { it.day } < 7.0) return null
        val n = points.size
        val mx = points.sumOf { it.day } / n
        val my = points.sumOf { it.value } / n
        val sxx = points.sumOf { (it.day - mx) * (it.day - mx) }
        if (sxx == 0.0) return null
        return points.sumOf { (it.day - mx) * (it.value - my) } / sxx
    }

    /** Days until [goal] is reached at [slope] from [current], or null when the trend heads away from it or is flat. */
    fun daysToGoal(current: Double, slope: Double, goal: Double): Double? {
        val gap = goal - current
        if (gap == 0.0) return 0.0
        if (slope == 0.0 || (gap > 0) != (slope > 0)) return null
        return gap / slope
    }

    /** Mean of the last [window] values at each position (shorter at the start). */
    fun movingAverage(values: List<Double>, window: Int): List<Double> =
        values.indices.map { i -> values.subList(maxOf(0, i - window + 1), i + 1).average() }
}
