package uk.woozy.fit.health

import android.content.Context
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.WeightEntryEntity
import java.time.Instant

/** Sends HQ Fit weights and finished workouts to Health Connect and pulls other apps' weights in. */
object HealthSync {
    private fun prefs(c: Context) = c.getSharedPreferences("health", Context.MODE_PRIVATE)

    fun writeEnabled(c: Context) = prefs(c).getBoolean("write", false)
    fun setWriteEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("write", on).apply()
    fun lastStatus(c: Context): String = prefs(c).getString("status", "Not synced yet") ?: "Not synced yet"
    private fun setStatus(c: Context, s: String) = prefs(c).edit().putString("status", s).apply()

    /** Writes the last 90 days of own weights and workouts (safe to repeat) and imports other apps' weights. */
    suspend fun run(c: Context, db: HqFitDatabase, hc: HealthGateway, nowMs: Long = System.currentTimeMillis()): String {
        if (!hc.available) return "Health Connect is not available on this phone".also { setStatus(c, it) }
        return try {
            var wrote = 0
            if (writeEnabled(c)) {
                val from = nowMs - 90L * 86_400_000L
                db.weights().ownSince(from).forEach { hc.writeWeight(it.id, it.epochMs, it.kg); wrote++ }
                db.sessions().doneSince(from).forEach { hc.writeWorkout(it.id, it.name, it.startedMs, it.endedMs ?: it.startedMs); wrote++ }
            }
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
