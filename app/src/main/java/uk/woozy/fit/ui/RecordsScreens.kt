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
    val rows by flow.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Personal records", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows.isEmpty()) {
                EmptyCard("No records yet", "Finish a workout and your best lifts appear here.")
            } else {
                HqCard(Modifier.fillMaxWidth()) {
                    rows.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onOpen(r.exerciseId) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                HqText(r.exerciseName, 16.sp, FontWeight.Medium)
                                HqText("${r.sessions} session${if (r.sessions == 1) "" else "s"}", 13.sp, color = c.muted)
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
    val bySession = history.groupBy { it.sessionId }.values
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar(name.ifEmpty { "History" }, onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            bySession.forEach { sets ->
                SectionLabel(dateFmt.format(Instant.ofEpochMilli(sets.first().startedMs)))
                HqCard(Modifier.fillMaxWidth()) {
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
