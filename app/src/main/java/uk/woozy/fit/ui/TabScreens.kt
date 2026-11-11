package uk.woozy.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import uk.woozy.fit.data.db.Diary
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.formatOne
import uk.woozy.fit.model.DayValue
import uk.woozy.fit.model.Nutrients
import uk.woozy.fit.model.Trend
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.Targets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun TabPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

private data class ProgressData(
    val weights: List<DayValue>,
    val latestKg: Double?,
    val slopePerDay: Double?,
    val goalDate: LocalDate?,
    val kcalDays: List<Double>,
    val kcalLabels: List<String>,
    val kcalTarget: Double?,
    val weekLabels: List<String>,
    val weekDone: List<Double>,
    val weekPlanned: List<Int>,
    val weekVolume: List<Double>,
)

private const val GOAL_KG = 80.0

private suspend fun loadProgress(db: HqFitDatabase): ProgressData {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val all = db.weights().observeAllOnce()
    val from90 = today.minusDays(120).atStartOfDay(zone).toInstant().toEpochMilli()
    val weights = all.filter { it.epochMs >= from90 }.sortedBy { it.epochMs }
        .map { DayValue(it.epochMs / 86_400_000.0, it.kg) }
    val recent = weights.filter { it.day >= weights.maxOfOrNull { w -> w.day }?.minus(60.0) ?: 0.0 }
    val slope = Trend.slopePerDay(recent)
    val latest = all.firstOrNull()?.kg
    val goalDate = if (latest != null && slope != null) Trend.daysToGoal(latest, slope, GOAL_KG)?.takeIf { it < 1500 }?.let { today.plusDays(it.toLong()) } else null

    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val diary = db.diary().between(days.first().toString(), days.last().toString()).groupBy { it.date }
    val kcal = days.map { d -> diary[d.toString()]?.let { Diary.totals(it)[Nutrients.ENERGY] } ?: 0.0 }
    val target = db.targets().all().firstOrNull { it.key == Nutrients.ENERGY }?.value

    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = (7 downTo 0).map { monday.minusWeeks(it.toLong()) }
    val sched = db.schedule().between(weeks.first().toString(), weeks.last().plusDays(6).toString())
    val sessions = db.sessions().doneSince(weeks.first().atStartOfDay(zone).toInstant().toEpochMilli())
    val sets = sessions.flatMap { s -> db.sessions().sets(s.id).filter { it.done && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.map { s to it } }
    return ProgressData(
        weights = weights, latestKg = latest, slopePerDay = slope, goalDate = goalDate,
        kcalDays = kcal, kcalLabels = days.map { it.dayOfMonth.toString() }, kcalTarget = target,
        weekLabels = weeks.map { it.dayOfMonth.toString() },
        weekDone = weeks.map { w -> sched.count { it.status == "done" && LocalDate.parse(it.date) in w..w.plusDays(6) }.toDouble() },
        weekPlanned = weeks.map { w -> sched.count { it.status != "skipped" && LocalDate.parse(it.date) in w..w.plusDays(6) } },
        weekVolume = weeks.map { w ->
            sets.filter { (s, _) -> Instant.ofEpochMilli(s.startedMs).atZone(zone).toLocalDate() in w..w.plusDays(6) }.sumOf { (_, l) -> l.weightKg!! * l.reps!! }
        },
    )
}

/** Progress: weight against the goal, calories, and training. Needs the data to exist; an empty card says what feeds it. */
@Composable
fun ProgressScreen(db: HqFitDatabase, onRecords: () -> Unit, onScale: () -> Unit) {
    val c = HqTheme.colors
    val data by produceState<ProgressData?>(null, db) { value = loadProgress(db) }
    TabPage {
        ScreenTitle("Progress")
        val d = data
        if (d == null) { HqText("Loading", 14.sp, color = c.muted); return@TabPage }

        SectionLabel("Weight")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.latestKg == null) {
                    HqText("No weights yet. Pair your scale or type one in.", 14.sp, color = c.muted)
                } else {
                    HqText("${formatOne(d.latestKg)} kg", 28.sp, FontWeight.Bold)
                    HqText("${formatOne(d.latestKg - GOAL_KG)} kg to your ${GOAL_KG.toInt()} kg goal", 14.sp, color = c.muted)
                    LineChart(d.weights, GOAL_KG, "kg")
                    val s = d.slopePerDay
                    HqText(
                        when {
                            s == null -> "Trend needs three weigh-ins across at least a week."
                            d.goalDate != null -> "Trend ${formatOne(s * 7)} kg a week. At this pace you reach ${GOAL_KG.toInt()} kg around ${d.goalDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK))}."
                            else -> "Trend ${formatOne(s * 7)} kg a week, not heading to the goal."
                        },
                        13.sp, color = c.muted,
                    )
                }
                HqPillButton("Scale and weight", style = PillStyle.Outline, onClick = onScale)
            }
        }

        SectionLabel("Calories, last 14 days")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val logged = d.kcalDays.filter { it > 0 }
                if (logged.isEmpty()) {
                    HqText("Nothing logged yet.", 14.sp, color = c.muted)
                } else {
                    HqText("${formatWhole(logged.average())} kcal average on ${logged.size} logged days", 15.sp, FontWeight.Medium)
                    BarChart(d.kcalDays, d.kcalLabels, d.kcalTarget)
                    HqText("Days with nothing logged are left out of the average, not counted as zero.", 12.sp, color = c.muted)
                }
            }
        }

        SectionLabel("Training, last 8 weeks")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HqText("Sessions done each week", 15.sp, FontWeight.Medium)
                BarChart(d.weekDone, d.weekLabels, d.weekPlanned.maxOrNull()?.toDouble())
                HqText("This week ${d.weekDone.last().toInt()} of ${d.weekPlanned.last()} planned. Labels are the Monday of each week.", 12.sp, color = c.muted)
                HqText("Volume lifted each week (kg)", 15.sp, FontWeight.Medium)
                BarChart(d.weekVolume, d.weekLabels)
            }
        }

        SectionLabel("Strength")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText("Personal records and lift history", 14.sp, color = c.muted)
                HqPillButton("Open records", style = PillStyle.Outline, onClick = onRecords)
            }
        }
    }
}
