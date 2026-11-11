package uk.woozy.fit.data.backup

import uk.woozy.fit.data.db.DiaryEntryEntity
import uk.woozy.fit.data.db.SetLogEntity
import uk.woozy.fit.data.db.WeightEntryEntity
import uk.woozy.fit.data.db.WorkoutSessionEntity
import uk.woozy.fit.model.Nutrients
import java.time.Instant
import java.time.ZoneId

/** Plain CSV copies of your own data, for spreadsheets and for leaving HQ Fit if you ever want to. */
object CsvExport {
    fun cell(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun line(vararg v: Any?) = v.joinToString(",") { cell(it) } + "\n"

    private fun num(d: Double?): String = d?.let { if (it == Math.floor(it) && Math.abs(it) < 1e12) it.toLong().toString() else "%.2f".format(java.util.Locale.UK, it) } ?: ""

    fun weights(rows: List<WeightEntryEntity>, zone: ZoneId = ZoneId.systemDefault()): String =
        line("date_time", "kg", "source") + rows.sortedBy { it.epochMs }.joinToString("") {
            line(Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDateTime().withNano(0), num(it.kg), it.source)
        }

    fun diary(rows: List<DiaryEntryEntity>): String =
        line("date", "meal", "name", "grams", "kcal", "protein_g", "carbs_g", "fibre_g", "fat_g") + rows.sortedWith(compareBy({ it.date }, { it.createdMs })).joinToString("") {
            val n = Nutrients.decode(it.nutrients)
            line(it.date, it.meal, it.name, num(it.grams), num(n[Nutrients.ENERGY]), num(n[Nutrients.PROTEIN]), num(n[Nutrients.CARBS]), num(n[Nutrients.FIBRE]), num(n[Nutrients.FAT]))
        }

    fun sets(sessions: List<WorkoutSessionEntity>, sets: List<SetLogEntity>, zone: ZoneId = ZoneId.systemDefault()): String {
        val byId = sessions.associateBy { it.id }
        return line("date", "workout", "exercise", "set", "type", "kg", "reps", "seconds", "done") +
            sets.sortedWith(compareBy({ byId[it.sessionId]?.startedMs ?: 0L }, { it.position }, { it.setIndex })).joinToString("") {
                val s = byId[it.sessionId]
                line(
                    s?.let { x -> Instant.ofEpochMilli(x.startedMs).atZone(zone).toLocalDate() }, s?.name, it.exerciseName, it.setIndex + 1,
                    it.setType, num(it.weightKg), it.reps, it.durationSec, if (it.done) 1 else 0,
                )
            }
    }
}
