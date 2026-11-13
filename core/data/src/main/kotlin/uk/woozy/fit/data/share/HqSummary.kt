package uk.woozy.fit.data.share

import uk.woozy.fit.data.db.Diary
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.model.Nutrients
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** What HQ is allowed to see: today's totals and targets, the latest weight, and this week's training. */
data class HqSummary(
    val date: String,
    val kcal: Double,
    val proteinG: Double,
    val netCarbsG: Double,
    val fatG: Double,
    val waterMl: Int,
    val kcalTarget: Double?,
    val proteinTarget: Double?,
    val netCarbsTarget: Double?,
    val fatTarget: Double?,
    val weightKg: Double?,
    val weightAtMs: Long?,
    val workoutToday: String?,
    val workoutTodayDone: Boolean,
    val workoutsDoneThisWeek: Int,
    val workoutsPlannedThisWeek: Int,
    /** Most recent Sunday on or before today, and which of its check-in items are ticked. */
    val checkInDate: String,
    val checkInMood: Boolean,
    val checkInMetrics: Boolean,
    val checkInPhoto: Boolean,
    /** Daily step target, so HQ shows the same goal as HQ Fit. Added after version 1 shipped; old readers ignore it. */
    val stepsTarget: Double? = null,
)

object HqSummaryBuilder {
    suspend fun build(db: HqFitDatabase, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): HqSummary {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val totals = Diary.totals(db.diary().day(today.toString()))
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val water = db.water().totalBetween(start, end)
        val targets = db.targets().all().associate { it.key to it.value }
        val d = uk.woozy.fit.model.Targets()
        val weight = db.weights().latest()
        val monday: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = db.schedule().between(monday.toString(), monday.plusDays(6).toString())
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val ticks = db.checkIns().forDate(sunday.toString()).filter { it.done }.map { it.item }.toSet()
        val todays = week.firstOrNull { it.date == today.toString() }
        // A workout logged without a scheduled day still counts, the same way the Train screen counts it.
        val sessionDays = db.sessions().doneSince(monday.atStartOfDay(zone).toInstant().toEpochMilli())
            .map { Instant.ofEpochMilli(it.startedMs).atZone(zone).toLocalDate().toString() }.toSet()
        val doneDays = (week.filter { it.status == "done" }.map { it.date } + sessionDays).toSet()
        val plannedDays = (week.filter { it.status != "skipped" }.map { it.date } + sessionDays).toSet()
        return HqSummary(
            date = today.toString(),
            kcal = totals[Nutrients.ENERGY] ?: 0.0,
            proteinG = totals[Nutrients.PROTEIN] ?: 0.0,
            netCarbsG = Nutrients.netCarbs(totals),
            fatG = totals[Nutrients.FAT] ?: 0.0,
            waterMl = water,
            // Targets never edited are not stored; send the app's defaults so HQ shows exactly what HQ Fit shows.
            kcalTarget = targets[Nutrients.ENERGY] ?: d.energyKcal.toDouble(),
            proteinTarget = targets[Nutrients.PROTEIN] ?: d.proteinG.toDouble(),
            netCarbsTarget = targets[Nutrients.CARBS] ?: d.netCarbsG.toDouble(),
            fatTarget = targets[Nutrients.FAT] ?: d.fatG.toDouble(),
            weightKg = weight?.kg,
            weightAtMs = weight?.epochMs,
            workoutToday = todays?.templateName,
            workoutTodayDone = todays?.status == "done",
            workoutsDoneThisWeek = doneDays.size,
            workoutsPlannedThisWeek = maxOf(plannedDays.size, doneDays.size),
            checkInDate = sunday.toString(),
            checkInMood = "mood" in ticks,
            checkInMetrics = "metrics" in ticks,
            checkInPhoto = "photo" in ticks,
            stepsTarget = targets["steps"] ?: d.steps.toDouble(),
        )
    }
}

/** One past day with at least one diary entry. Days with no entries are left out so HQ keeps its older data for them. */
data class HqDay(val date: String, val kcal: Double, val proteinG: Double)
data class HqWeight(val epochMs: Long, val kg: Double)

/** The last 30 days, for HQ's weekly and 30-day charts. Added after version 1 shipped as separate paths; the today row is unchanged. */
object HqHistoryBuilder {
    suspend fun days(db: HqFitDatabase, nowMs: Long, n: Int = 30, zone: ZoneId = ZoneId.systemDefault()): List<HqDay> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return (n downTo 0).mapNotNull { back ->
            val d = today.minusDays(back.toLong()).toString()
            val rows = db.diary().day(d)
            if (rows.isEmpty()) null else {
                val t = Diary.totals(rows)
                HqDay(d, t[Nutrients.ENERGY] ?: 0.0, t[Nutrients.PROTEIN] ?: 0.0)
            }
        }
    }

    suspend fun weights(db: HqFitDatabase, nowMs: Long, n: Int = 30): List<HqWeight> {
        val from = nowMs - n * 86_400_000L
        return db.weights().observeAllOnce().filter { it.epochMs >= from }.sortedBy { it.epochMs }.map { HqWeight(it.epochMs, it.kg) }
    }
}
