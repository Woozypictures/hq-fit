package uk.woozy.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.SetLogEntity
import uk.woozy.fit.data.db.WorkoutSessionEntity
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.NumberEntry
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * History (GymMane style): a month calendar with the month's totals, then a card for each workout with its best
 * set per exercise. Tap a day to see just that day; open a card to see its summary and fix any set.
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun PastWorkoutsScreen(db: HqFitDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit, onTimeline: () -> Unit = {}, onLogPast: ((LocalDate, Long) -> Unit)? = null) {
    val c = HqTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    val sessions by produceState(emptyList<WorkoutSessionEntity>(), db) { value = db.sessions().doneSince(0L).reversed() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var logDay by remember { mutableStateOf<LocalDate?>(null) }
    val templates by produceState(emptyList<uk.woozy.fit.data.db.WorkoutTemplateEntity>(), db) { value = db.templates().all() }
    fun dateOf(s: WorkoutSessionEntity): LocalDate = Instant.ofEpochMilli(s.startedMs).atZone(zone).toLocalDate()
    val inMonth = sessions.filter { YearMonth.from(dateOf(it)) == month }
    val setsBy by produceState(emptyMap<Long, List<SetLogEntity>>(), inMonth.map { it.id }) {
        value = inMonth.associate { s -> s.id to db.sessions().sets(s.id).filter { it.done && it.setType != "warmup" } }
    }
    val shown = inMonth.filter { day == null || dateOf(it) == day }
    val imported = { s: WorkoutSessionEntity -> s.scheduledId == uk.woozy.fit.data.imports.EverfitImport.MARKER }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("History", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            uk.woozy.fit.designsystem.SegmentedChoice(listOf("Calendar", "Timeline"), 0, onSelect = { if (it == 1) onTimeline() })
            HqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ChevronLeft, "Previous month", tint = c.ink, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { month = month.minusMonths(1); day = null }.padding(8.dp))
                        HqText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)), 17.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        val atNow = month >= YearMonth.now()
                        Icon(Icons.Rounded.ChevronRight, "Next month", tint = if (atNow) c.track else c.ink, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !atNow, role = Role.Button) { month = month.plusMonths(1); day = null }.padding(8.dp))
                    }
                    val allSets = inMonth.flatMap { setsBy[it.id].orEmpty() }
                    val minutes = inMonth.filterNot(imported).sumOf { ((it.endedMs ?: it.startedMs) - it.startedMs) / 60_000 }
                    val volume = allSets.filter { it.durationSec == null }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
                    Row(Modifier.fillMaxWidth()) {
                        Total("Workouts", "${inMonth.size}", Modifier.weight(1f))
                        Total("Time", if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m", Modifier.weight(1f))
                        Total("Sets", "${allSets.size}", Modifier.weight(1f))
                        Total("Volume", if (volume >= 1000) "${NumberEntry.format(Math.round(volume / 100.0) / 10.0)} t" else "${formatWhole(volume)} kg", Modifier.weight(1f))
                    }
                    val trainedDays = inMonth.map { dateOf(it) }.toSet()
                    MonthGrid(month, trainedDays, day, canLog = onLogPast != null) { d ->
                        if (d in trainedDays) day = if (day == d) null else d else logDay = d
                    }
                }
            }
            if (shown.isEmpty()) {
                HqText(if (day != null) "Nothing logged that day." else "No workouts this month.", 14.sp, color = c.muted, modifier = Modifier.padding(8.dp))
            }
            if (onLogPast != null) HqText("Tap an empty day to log a workout you did then.", 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 8.dp))
            shown.forEach { s ->
                val sets = setsBy[s.id].orEmpty()
                HqCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button) { onOpen(s.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val d = dateOf(s)
                            Column(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(c.track).padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                HqText(d.format(DateTimeFormatter.ofPattern("EEE", Locale.UK)).uppercase(), 10.sp, FontWeight.SemiBold, c.muted)
                                HqText("${d.dayOfMonth}", 18.sp, FontWeight.Bold)
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                HqText(s.name, 16.sp, FontWeight.SemiBold, maxLines = 2)
                                val minutes = ((s.endedMs ?: s.startedMs) - s.startedMs) / 60_000
                                val vol = sets.filter { it.durationSec == null }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
                                HqText(
                                    listOfNotNull(
                                        if (!imported(s)) Instant.ofEpochMilli(s.startedMs).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) else null,
                                        if (!imported(s) && minutes > 0) "$minutes min" else null,
                                        "${sets.size} sets",
                                        if (vol > 0) "${formatWhole(vol)} kg" else null,
                                    ).joinToString(" · "),
                                    13.sp, color = c.muted,
                                )
                            }
                        }
                        sets.groupBy { it.position }.toSortedMap().values.forEach { g ->
                            val best = g.filter { it.durationSec == null && it.weightKg != null && it.reps != null }.maxByOrNull { (it.weightKg ?: 0.0) * (1 + (it.reps ?: 0) / 30.0) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                HqText("${g.size}×", 13.sp, color = c.muted, modifier = Modifier.padding(end = 8.dp))
                                HqText(g.first().exerciseName, 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                best?.let { HqText("${NumberEntry.format(it.weightKg ?: 0.0)}×${it.reps}", 14.sp, FontWeight.Medium) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    logDay?.let { d ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { logDay = null }, containerColor = c.card) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HqText("Log a workout", 19.sp, FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                HqText(d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)), 14.sp, color = c.muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                if (templates.isEmpty()) HqText("Make a workout in Programmes first.", 14.sp, color = c.muted)
                templates.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { logDay = null; onLogPast?.invoke(d, t.id) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HqText(t.name, 16.sp, FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.Rounded.ChevronRight, null, tint = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Total(label: String, value: String, modifier: Modifier) {
    val c = HqTheme.colors
    Column(modifier) {
        HqText(value, 18.sp, FontWeight.Bold)
        HqText(label.uppercase(), 10.sp, FontWeight.SemiBold, c.muted, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun MonthGrid(month: YearMonth, trained: Set<LocalDate>, selected: LocalDate?, canLog: Boolean = false, onPick: (LocalDate) -> Unit) {
    val c = HqTheme.colors
    val today = LocalDate.now()
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1 // Monday first
    val cells = lead + month.lengthOfMonth()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { HqText(it, 11.sp, FontWeight.SemiBold, c.muted, Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
        (0 until (cells + 6) / 7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { col ->
                    val i = week * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (i in 0 until month.lengthOfMonth()) {
                            val d = first.plusDays(i.toLong())
                            val on = d in trained
                            val sel = d == selected
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (sel) c.ink else if (on) c.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable(enabled = on || (canLog && !d.isAfter(today)), role = Role.Button) { onPick(d) },
                                contentAlignment = Alignment.Center,
                            ) {
                                HqText(
                                    "${d.dayOfMonth}", 13.sp, if (on || d == today) FontWeight.SemiBold else FontWeight.Normal,
                                    if (sel) c.page else if (on) c.onPrimary else if (d.isAfter(today)) c.track else c.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
