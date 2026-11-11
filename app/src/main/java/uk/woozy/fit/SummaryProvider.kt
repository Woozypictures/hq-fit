package uk.woozy.fit

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.runBlocking
import uk.woozy.fit.data.share.HqSummary
import uk.woozy.fit.data.share.HqSummaryBuilder

/**
 * A read-only summary for the HQ app: content://uk.woozy.fit.summary/today. Protected by a signature permission, so
 * only an app signed with the same key (HQ) can read it. Insert, update and delete are refused. If the database
 * cannot be opened (first run, or a phone that needs the recovery key) the query returns an empty cursor.
 */
class SummaryProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply { addURI(AUTHORITY, "today", TODAY) }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (matcher.match(uri) != TODAY) return null
        val ctx = context ?: return null
        val container = (ctx.applicationContext as HqFitApplication).container
        synchronized(LOCK) { if (container.database.db == null) runBlocking { container.database.start() } }
        val db = container.database.db
        val summary: HqSummary? = if (db == null) null else runBlocking { HqSummaryBuilder.build(db, System.currentTimeMillis()) }
        val cursor = MatrixCursor(COLUMNS)
        if (summary != null) cursor.addRow(row(summary))
        return cursor
    }

    private fun row(s: HqSummary): Array<Any?> = arrayOf(
        s.date, s.kcal, s.proteinG, s.netCarbsG, s.fatG, s.waterMl,
        s.kcalTarget, s.proteinTarget, s.netCarbsTarget, s.fatTarget,
        s.weightKg, s.weightAtMs, s.workoutToday, if (s.workoutTodayDone) 1 else 0,
        s.workoutsDoneThisWeek, s.workoutsPlannedThisWeek, 1,
    )

    override fun getType(uri: Uri): String? = if (matcher.match(uri) == TODAY) "vnd.android.cursor.item/vnd.uk.woozy.fit.summary" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")

    companion object {
        const val AUTHORITY = "uk.woozy.fit.summary"
        private const val TODAY = 1
        private val LOCK = Any()

        /** Column names HQ should read. [version] lets HQ ignore a layout it does not know. */
        val COLUMNS = arrayOf(
            "date", "kcal", "protein_g", "net_carbs_g", "fat_g", "water_ml",
            "kcal_target", "protein_target_g", "net_carbs_target_g", "fat_target_g",
            "weight_kg", "weight_at_ms", "workout_today", "workout_today_done",
            "workouts_done_week", "workouts_planned_week", "version",
        )
    }
}
