package uk.woozy.fit.health

import android.content.Context
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.imports.CronometerImport
import uk.woozy.fit.model.Nutrients
import uk.woozy.fit.data.db.WeightEntryEntity
import java.time.Instant

/** Sends HQ Fit weights, finished workouts, food and water to Health Connect and pulls other apps' weights in. */
object HealthSync {
    /**
     * When a diary entry happened, for Health Connect. Normally the moment it was logged; but food added for another
     * day (yesterday's dinner, typed in this morning) is placed on that day, at a usual time for the meal, so it
     * counts towards the right day in Samsung Health and HQ.
     */
    internal fun mealTime(date: String, meal: String, createdMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
        val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return createdMs
        if (Instant.ofEpochMilli(createdMs).atZone(zone).toLocalDate() == day) return createdMs
        val hour = when (meal) { "breakfast" -> 8; "lunch" -> 13; "dinner" -> 19; else -> 16 }
        return day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("health", Context.MODE_PRIVATE)

    fun lastStatus(c: Context): String = prefs(c).getString("status", "Not synced yet") ?: "Not synced yet"
    private fun setStatus(c: Context, s: String) = prefs(c).edit().putString("status", s).apply()

    /** Writes the last 90 days of own weights and workouts (safe to repeat) and imports other apps' weights. */
    suspend fun run(c: Context, db: HqFitDatabase, hc: HealthGateway, nowMs: Long = System.currentTimeMillis()): String {
        if (!hc.available) return "Health Connect is not available on this phone".also { setStatus(c, it) }
        return try {
            // Each kind is sent only when its own permission is on, so one switch left off does not stop the rest.
            val g = hc.granted()
            fun can(k: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) =
                androidx.health.connect.client.permission.HealthPermission.getWritePermission(k) in g
            if (g.intersect(HealthGateway.writePermissions).isEmpty()) {
                return "Grant Health Connect access to start syncing".also { setStatus(c, it) }
            }
            val full = nowMs - prefs(c).getLong("full", 0L) > 7L * 86_400_000L
            var wrote = 0
            run {
                // A full 90-day pass at most weekly; otherwise only the last 3 days, so opening the app stays light
                // and Health Connect's write limits are not hit.
                val days = if (full) 90L else 3L
                val from = nowMs - days * 86_400_000L
                val today = java.time.LocalDate.now()
                if (can(androidx.health.connect.client.records.NutritionRecord::class)) db.diary().between(today.minusDays(days).toString(), today.toString())
                    .filter { it.name != CronometerImport.ENTRY_NAME }
                    .forEach { hc.writeMeal(it.id, it.name, it.meal, mealTime(it.date, it.meal, it.createdMs), Nutrients.decode(it.nutrients)); wrote++ }
                if (can(androidx.health.connect.client.records.HydrationRecord::class)) db.water().since(from).forEach { hc.writeWater(it.id, it.epochMs, it.ml); wrote++ }
                if (can(androidx.health.connect.client.records.WeightRecord::class)) db.weights().ownSince(from).forEach { hc.writeWeight(it.id, it.epochMs, it.kg); wrote++ }
                if (can(androidx.health.connect.client.records.ExerciseSessionRecord::class)) db.sessions().doneSince(from).filter { it.scheduledId != uk.woozy.fit.data.imports.EverfitImport.MARKER }.forEach { hc.writeWorkout(it.id, it.name, it.startedMs, it.endedMs ?: it.startedMs); wrote++ }
            }
            if (full) prefs(c).edit().putLong("full", nowMs).apply()
            var imported = 0
            hc.importedWeights(Instant.ofEpochMilli(nowMs - 3L * 365 * 86_400_000L), Instant.ofEpochMilli(nowMs)).forEach {
                if (db.weights().countBetween(it.epochMs - 60_000, it.epochMs + 60_000) == 0) {
                    db.weights().insert(WeightEntryEntity(epochMs = it.epochMs, kg = Math.round(it.kg * 10.0) / 10.0, source = "import"))
                    imported++
                }
            }
            "Synced: $wrote sent, $imported weights imported".also { setStatus(c, it) }
        } catch (e: Exception) {
            "Sync failed: ${e.javaClass.simpleName}".also { setStatus(c, it) }
        }
    }
}
