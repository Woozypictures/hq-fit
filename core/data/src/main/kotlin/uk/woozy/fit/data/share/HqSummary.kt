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
)

object HqSummaryBuilder {
    suspend fun build(db: HqFitDatabase, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): HqSummary {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val totals = Diary.totals(db.diary().day(today.toString()))
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val water = db.water().totalBetween(start, end)
        val targets = db.targets().all().associate { it.key to it.value }
        val weight = db.weights().latest()
        val monday: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = db.schedule().between(monday.toString(), monday.plusDays(6).toString())
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val ticks = db.checkIns().forDate(sunday.toString()).filter { it.done }.map { it.item }.toSet()
        val todays = week.firstOrNull { it.date == today.toString() }
        return HqSummary(
            date = today.toString(),
            kcal = totals[Nutrients.ENERGY] ?: 0.0,
            proteinG = totals[Nutrients.PROTEIN] ?: 0.0,
            netCarbsG = Nutrients.netCarbs(totals),
            fatG = totals[Nutrients.FAT] ?: 0.0,
            waterMl = water,
            kcalTarget = targets[Nutrients.ENERGY],
            proteinTarget = targets[Nutrients.PROTEIN],
            netCarbsTarget = targets[Nutrients.CARBS],
            fatTarget = targets[Nutrients.FAT],
            weightKg = weight?.kg,
            weightAtMs = weight?.epochMs,
            workoutToday = todays?.templateName,
            workoutTodayDone = todays?.status == "done",
            workoutsDoneThisWeek = week.count { it.status == "done" },
            workoutsPlannedThisWeek = week.count { it.status != "skipped" },
            checkInDate = sunday.toString(),
            checkInMood = "mood" in ticks,
            checkInMetrics = "metrics" in ticks,
            checkInPhoto = "photo" in ticks,
        )
    }
}
