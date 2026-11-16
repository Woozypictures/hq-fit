package uk.woozy.fit

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.content.pm.PackageManager
import android.os.Bundle
import uk.woozy.fit.data.share.DeskSync
import android.net.Uri
import kotlinx.coroutines.runBlocking
import uk.woozy.fit.data.share.HqSummary
import uk.woozy.fit.data.share.HqHistoryBuilder
import uk.woozy.fit.data.share.HqSummaryBuilder

/**
 * A read-only summary for the HQ app: content://uk.woozy.fit.summary/today (plus /history and /weights for the last 30 days). Protected by a signature permission, so
 * only an app signed with the same key (HQ) can read it. Insert, update and delete are refused. If the database
 * cannot be opened (first run, or a phone that needs the recovery key) the query returns an empty cursor.
 */
class SummaryProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply { addURI(AUTHORITY, "today", TODAY)
        addURI(AUTHORITY, "history", HISTORY)
        addURI(AUTHORITY, "weights", WEIGHTS)
        addURI(AUTHORITY, "muscles", MUSCLES)
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val kind = matcher.match(uri)
        if (kind == UriMatcher.NO_MATCH) return null
        val ctx = context ?: return null
        val container = (ctx.applicationContext as HqFitApplication).container
        synchronized(LOCK) { if (container.database.db == null) runBlocking { container.database.start() } }
        val db = container.database.db
        if (kind == HISTORY) {
            val c = MatrixCursor(arrayOf("date", "kcal", "protein_g", "version"))
            if (db != null) runBlocking { HqHistoryBuilder.days(db, System.currentTimeMillis()) }.forEach { c.addRow(arrayOf<Any?>(it.date, it.kcal, it.proteinG, 1)) }
            return c
        }
        if (kind == MUSCLES) {
            // Muscle map for HQ: working sets per region over 7 and 30 days, and when each was last worked.
            val c = MatrixCursor(arrayOf("region", "name", "week_sets", "month_sets", "last_hit_ms", "version"))
            if (db != null) {
                val sec = uk.woozy.fit.ui.MuscleCatalog.get(ctx)
                val now = System.currentTimeMillis()
                val week = runBlocking { uk.woozy.fit.data.Muscles.load(db, now - 7 * 86_400_000L, sec) }
                val month = runBlocking { uk.woozy.fit.data.Muscles.load(db, now - 30 * 86_400_000L, sec) }
                uk.woozy.fit.data.Muscles.REGIONS.forEach { r ->
                    c.addRow(arrayOf<Any?>(r.key, r.name, week.sets[r.key] ?: 0.0, month.sets[r.key] ?: 0.0, month.lastHit[r.key], 1))
                }
            }
            return c
        }
        if (kind == WEIGHTS) {
            val c = MatrixCursor(arrayOf("weight_at_ms", "weight_kg", "version"))
            if (db != null) runBlocking { HqHistoryBuilder.weights(db, System.currentTimeMillis()) }.forEach { c.addRow(arrayOf<Any?>(it.epochMs, it.kg, 1)) }
            return c
        }
        val summary: HqSummary? = if (db == null) null else runBlocking { HqSummaryBuilder.build(db, System.currentTimeMillis()) }
        val cursor = MatrixCursor(COLUMNS)
        if (summary != null) cursor.addRow(row(summary))
        return cursor
    }

    private fun row(s: HqSummary): Array<Any?> = arrayOf(
        s.date, s.kcal, s.proteinG, s.netCarbsG, s.fatG, s.waterMl,
        s.kcalTarget, s.proteinTarget, s.netCarbsTarget, s.fatTarget,
        s.weightKg, s.weightAtMs, s.workoutToday, if (s.workoutTodayDone) 1 else 0,
        s.workoutsDoneThisWeek, s.workoutsPlannedThisWeek,
        s.checkInDate, if (s.checkInMood) 1 else 0, if (s.checkInMetrics) 1 else 0, if (s.checkInPhoto) 1 else 0, 1,
        s.stepsTarget,
    )

    /**
     * Desktop sync for HQ: `desk_export` returns routines, calendar and recent training as JSON; `desk_apply` runs the
     * desktop's queued changes (extras "ops", a JSON array). Same signature permission as the summary.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (ctx.checkCallingPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) throw SecurityException("not allowed")
        val container = (ctx.applicationContext as HqFitApplication).container
        synchronized(LOCK) { if (container.database.db == null) runBlocking { container.database.start() } }
        val db = container.database.db ?: return Bundle().apply { putString("error", "locked") }
        val now = System.currentTimeMillis()
        val json = when (method) {
            "desk_export" -> runBlocking { DeskSync.export(db, now) }.toString()
            "desk_apply" -> runBlocking { DeskSync.apply(db, extras?.getString("ops") ?: "[]", now) }.toString()
            else -> return null
        }
        return Bundle().apply { putString("json", json) }
    }

    override fun getType(uri: Uri): String? = if (matcher.match(uri) != UriMatcher.NO_MATCH) "vnd.android.cursor.item/vnd.uk.woozy.fit.summary" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")

    companion object {
        const val AUTHORITY = "uk.woozy.fit.summary"
        private const val PERMISSION = "uk.woozy.fit.permission.READ_SUMMARY"
        private const val TODAY = 1
        private const val HISTORY = 2
        private const val WEIGHTS = 3
        private const val MUSCLES = 4
        internal val LOCK = Any()

        /** Column names HQ should read. [version] lets HQ ignore a layout it does not know. */
        val COLUMNS = arrayOf(
            "date", "kcal", "protein_g", "net_carbs_g", "fat_g", "water_ml",
            "kcal_target", "protein_target_g", "net_carbs_target_g", "fat_target_g",
            "weight_kg", "weight_at_ms", "workout_today", "workout_today_done",
            "workouts_done_week", "workouts_planned_week",
            "checkin_date", "checkin_mood", "checkin_metrics", "checkin_photo", "version",
            "steps_target",
        )
    }
}
