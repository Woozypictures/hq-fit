package uk.woozy.fit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.data.db.HistoryRow
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.model.NumberEntry
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.woozy.fit.model.DayValue
import uk.woozy.fit.model.SetPoint
import uk.woozy.fit.model.Strength
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK).withZone(ZoneId.systemDefault())

/** Best weight and estimated 1RM per lift, from finished workouts. */
@Composable
fun RecordsScreen(db: HqFitDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val c = HqTheme.colors
    val flow = remember(db) { db.sessions().observeRecords() }
    val allRows by flow.collectAsState(initial = emptyList())
    val exercises by remember(db) { db.exercises().observeAll() }.collectAsState(initial = emptyList())
    val muscleOf = remember(exercises) { exercises.associate { it.id to it.muscle } }
    var query by remember { mutableStateOf("") }
    val rows = remember(allRows, query) { allRows.filter { query.isBlank() || it.exerciseName.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Personal records", onBack) }
        androidx.compose.material3.OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { HqText("Search exercise", 13.sp, color = c.muted) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows.isEmpty()) {
                EmptyCard(if (allRows.isEmpty()) "No records yet" else "Nothing matches", if (allRows.isEmpty()) "Finish a workout and your best lifts appear here." else "Try a different name.")
            } else {
                HqCard(Modifier.fillMaxWidth()) {
                    rows.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onOpen(r.exerciseId) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                muscleOf[r.exerciseId]?.takeIf { it.isNotBlank() }?.let { HqText(it.uppercase(), 11.sp, FontWeight.SemiBold, c.muted) }
                                HqText(r.exerciseName, 16.sp, FontWeight.Medium)
                                HqText("${r.sessions} time${if (r.sessions == 1) "" else "s"}", 13.sp, color = c.muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                HqText("${NumberEntry.format(r.bestKg)} kg", 16.sp, FontWeight.SemiBold)
                                HqText("1RM ${NumberEntry.format(Math.round(r.bestE1rm * 10) / 10.0)}", 13.sp, color = c.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Every finished set of one lift, newest workout first. */
@Composable
fun ExerciseHistoryScreen(db: HqFitDatabase, exerciseId: Long, onBack: () -> Unit) {
    val c = HqTheme.colors
    val name by produceState("") { value = db.exercises().findById(exerciseId)?.name.orEmpty() }
    val history by produceState(emptyList<HistoryRow>(), exerciseId) { value = db.sessions().exerciseHistory(exerciseId) }
    // Everfit-style: newest first, each session with its workout name, estimated 1RM and volume.
    val bySession = history.groupBy { it.sessionId }.values.toList()
    var range by remember { mutableStateOf(HistoryRange.MONTHS_2) }
    val cutoff = range.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: Long.MIN_VALUE
    val chart = bySession.filter { it.first().startedMs >= cutoff }
        .map { s -> DayValue(s.first().startedMs / 86_400_000.0, s.maxOf { it.weightKg }) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar(name.ifEmpty { "History" }, onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bySession.isEmpty()) {
                HqText("No finished sets yet. History appears after your first workout with this exercise.", 14.sp, color = c.muted)
            } else {
                HqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HqText("Heaviest set per workout", 15.sp, FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HistoryRange.entries.forEach { r ->
                                val on = r == range
                                HqText(r.label, 13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) c.primary else c.muted,
                                    modifier = Modifier.clickable(role = Role.Button) { range = r }.padding(horizontal = 6.dp, vertical = 8.dp))
                            }
                        }
                        if (chart.isEmpty()) HqText("Nothing in this period.", 13.sp, color = c.muted)
                        else LineChart(chart, goal = null, unit = "kg")
                    }
                }
            }
            bySession.forEach { sets ->
                val first = sets.first()
                val points = sets.map { SetPoint(it.weightKg, it.reps) }
                val e1rm = points.maxOf { Strength.e1rm(it) }
                SectionLabel(dateFmt.format(Instant.ofEpochMilli(first.startedMs)) + if (first.sessionName.isNotBlank()) "  ·  ${first.sessionName}" else "")
                HqCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        HqText("1RM est. ${NumberEntry.format(Math.round(e1rm * 10) / 10.0)} kg", 13.sp, color = c.muted)
                        HqText("Volume ${NumberEntry.format(Math.round(Strength.volume(points)).toDouble())} kg", 13.sp, color = c.muted)
                    }
                    sets.forEach { s ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            HqText("Set ${s.setIndex}", 14.sp, color = c.muted, modifier = Modifier.weight(1f))
                            HqText("${NumberEntry.format(s.weightKg)} kg x ${s.reps}", 16.sp, FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private enum class HistoryRange(val label: String, val days: Long?) { MONTH_1("1 month", 31), MONTHS_2("2 months", 62), MONTHS_6("6 months", 183), ALL("All", null) }
