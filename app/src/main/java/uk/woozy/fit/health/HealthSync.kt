package uk.woozy.fit.health

import kotlinx.coroutines.sync.withLock
import android.content.Context
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.imports.CronometerImport
import uk.woozy.fit.model.Nutrients
import uk.woozy.fit.data.db.WeightEntryEntity
import java.time.Instant

/** Sends HQ Fit weights, finished workouts, food and water to Health Connect and pulls other apps' weights in. */
object HealthSync {
    /** See [uk.woozy.fit.model.MealTime]. */
    internal fun mealTime(date: String, meal: String, createdMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        uk.woozy.fit.model.MealTime.at(date, meal, createdMs, zone)

    private fun prefs(c: Context) = c.getSharedPreferences("health", Context.MODE_PRIVATE)

    fun lastStatus(c: Context): String = prefs(c).getString("status", "Not synced yet") ?: "Not synced yet"
    private fun setStatus(c: Context, s: String) = prefs(c).edit().putString("status", s).putLong("at", System.currentTimeMillis()).apply()

    /** When Health Connect was last synced (any outcome), or 0. Shown as a freshness line on Home. */
    fun lastSyncMs(c: Context): Long = prefs(c).getLong("at", 0L)

    /** Writes the last 90 days of own weights and workouts (safe to repeat) and imports other apps' weights. */
    /** One sync at a time: opening the app, a pull to refresh and an auto-sync after logging can all ask at once. */
    private val lock = kotlinx.coroutines.sync.Mutex()

    /**
     * [quick] is the auto-sync after something is logged: only today and yesterday are sent, and only weights newer
     * than the last import are read back, so it costs a handful of Health Connect calls instead of hundreds.
     */
    suspend fun run(c: Context, db: HqFitDatabase, hc: HealthGateway, nowMs: Long = System.currentTimeMillis(), quick: Boolean = false): String =
        lock.withLock { runLocked(c, db, hc, nowMs, quick) }

    private suspend fun runLocked(c: Context, db: HqFitDatabase, hc: HealthGateway, nowMs: Long, quick: Boolean): String {
        if (!hc.available) return "Health Connect is not available on this phone".also { setStatus(c, it) }
        return try {
            // Each kind is sent only when its own permission is on, so one switch left off does not stop the rest.
            val g = hc.granted()
            fun can(k: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) =
                androidx.health.connect.client.permission.HealthPermission.getWritePermission(k) in g
            if (g.intersect(HealthGateway.writePermissions).isEmpty()) {
                return "Grant Health Connect access to start syncing".also { setStatus(c, it) }
            }
            val full = !quick && nowMs - prefs(c).getLong("full", 0L) > 7L * 86_400_000L
            var wrote = 0
            run {
                // A full 90-day pass at most weekly; otherwise only the last 3 days, so opening the app stays light
                // and Health Connect's write limits are not hit.
                val days = if (full) 90L else if (quick) 1L else 3L
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
            // Scale weigh-ins other apps wrote (Feelfit and the like): the full three years on a weekly pass, otherwise
            // only since the last import (less two days, for late writers), so a weigh-in shows up within seconds.
            val lastImp = prefs(c).getLong("imp", 0L)
            val impFrom = if (full || lastImp == 0L) nowMs - 3L * 365 * 86_400_000L else lastImp - 2L * 86_400_000L
            hc.importedWeights(Instant.ofEpochMilli(impFrom), Instant.ofEpochMilli(nowMs)).forEach {
                // Same weigh-in from another app (Feelfit saving a reading the scale screen already logged, or a late
                // writer): anything within a minute, or within 15 minutes and 0.5 kg, is the same weigh-in. Own entries win.
                val near = db.weights().since(it.epochMs - 900_000).any { w -> kotlin.math.abs(w.epochMs - it.epochMs) <= 900_000 && kotlin.math.abs(w.kg - it.kg) <= 0.5 }
                if (!near && db.weights().countBetween(it.epochMs - 60_000, it.epochMs + 60_000) == 0) {
                    db.weights().insert(WeightEntryEntity(epochMs = it.epochMs, kg = Math.round(it.kg * 10.0) / 10.0, source = "import"))
                    imported++
                }
            }
            prefs(c).edit().putLong("imp", nowMs).apply()
            "Synced: $wrote sent, $imported weights imported".also { setStatus(c, it) }
        } catch (e: Exception) {
            "Sync failed: ${e.javaClass.simpleName}".also { setStatus(c, it) }
        }
    }
}
