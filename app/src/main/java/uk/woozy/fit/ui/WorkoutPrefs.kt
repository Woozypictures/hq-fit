package uk.woozy.fit.ui

import android.content.Context

/** Workout settings kept as plain preferences. */
object WorkoutPrefs {
    private fun p(c: Context) = c.getSharedPreferences("workout", Context.MODE_PRIVATE)
    val steps = listOf(1.0, 1.25, 2.0, 2.5, 5.0)

    fun weightStep(c: Context): Double = p(c).getFloat("step", 2.5f).toDouble()
    fun setWeightStep(c: Context, v: Double) = p(c).edit().putFloat("step", v.toFloat()).apply()
    fun autoRest(c: Context): Boolean = p(c).getBoolean("auto_rest", true)
    fun setAutoRest(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_rest", v).apply()
}
