package uk.woozy.fit.ui

import uk.woozy.fit.designsystem.LocalGlassInset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.ExerciseEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.ScheduledWorkoutEntity
import uk.woozy.fit.data.db.TemplateItemRow
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SpacedTitle
import uk.woozy.fit.model.Section
import uk.woozy.fit.model.formatDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dayLine = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

private fun ScheduledWorkoutEntity.localDate(): LocalDate = LocalDate.parse(date)

/** Train: kept short. The week card opens the full Training calendar; everything else is one tidy list. */
@Composable
fun TrainScreen(
    calendar: List<ScheduledWorkoutEntity>,
    doneDates: Set<String> = emptySet(),
    onOpen: (Long) -> Unit,
    onLibrary: () -> Unit,
    onRecords: () -> Unit,
    onNewWorkout: () -> Unit = {},
    onNewExercise: () -> Unit = {},
    onPast: () -> Unit = {},
    onProgrammes: () -> Unit = {},
    onTraining: () -> Unit = {},
) {
    val c = HqTheme.colors
    val today = rememberToday()
    val monday = remember(today) { today.with(DayOfWeek.MONDAY) }
    val byDate = calendar.groupBy { it.date }
    val upcoming = calendar.filter { it.localDate() >= today && it.status == "planned" }.take(8)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + LocalGlassInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Train")
        // Everfit's Training card: "Training (this week)", the strip, a short pep line. Tap it for the full Training page.
        Row(verticalAlignment = Alignment.Bottom) {
            HqText("Training", 18.sp, FontWeight.Bold)
            HqText(" (this week)", 12.sp, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
        }
        HqCard(Modifier.fillMaxWidth(), onClick = onTraining) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EverfitWeek(monday, today, byDate, doneDates, onDay = { d -> byDate[d.toString()]?.firstOrNull()?.let { onOpen(it.id) } ?: onTraining() })
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                val doneDays = (0..6).count { i -> val d = monday.plusDays(i.toLong()).toString(); d in doneDates || byDate[d].orEmpty().any { it.status == "done" } }
                val weekDays = (0..6).count { i -> val d = monday.plusDays(i.toLong()).toString(); d in doneDates || byDate[d].orEmpty().isNotEmpty() }
                HqText(weekMessage(doneDays, weekDays), 13.sp, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        HqCard(Modifier.fillMaxWidth()) {
            ListRow(Icons.Rounded.CalendarMonth, "Programmes", "Pick the days for each workout and edit it", onClick = onProgrammes)
            ListRow(Icons.Rounded.FitnessCenter, "Exercise library", "Search and filter every exercise", onClick = onLibrary)
            ListRow(Icons.Rounded.Star, "Personal records", "Best lifts and history", onClick = onRecords)
            ListRow(Icons.Rounded.FitnessCenter, "New workout", "Build your own and put it on the calendar", onClick = onNewWorkout)
            ListRow(Icons.Rounded.FitnessCenter, "New exercise", "Add your own to the library", onClick = onNewExercise)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One scheduled workout, laid out by section. Start opens the workout screen; Move picks another day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPreviewScreen(
    db: HqFitDatabase,
    scheduledId: Long,
    onBack: () -> Unit,
    onStart: (ScheduledWorkoutEntity) -> Unit,
    onEdit: (Long) -> Unit = {},
) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var reload by remember { mutableStateOf(0) }
    val scheduled by produceState<ScheduledWorkoutEntity?>(null, scheduledId, reload) { value = db.schedule().get(scheduledId) }
    val s = scheduled
    val itemsFlow = remember(s?.templateId) {
        s?.let { db.templates().observeItems(it.templateId) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val items by itemsFlow.collectAsState(initial = emptyList())
    var moving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Workout", onBack) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s != null) {
                SpacedTitle(s.templateName, color = c.ink, size = 22.sp)
                HqText(LocalDate.parse(s.date).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)), 14.sp, FontWeight.Medium, c.primary)
                if (s.date != s.originalDate) {
                    HqText("Moved from ${LocalDate.parse(s.originalDate).format(dayLine)}", 13.sp, color = c.muted)
                }
            }
            Section.entries.forEach { section ->
                val rows = items.filter { it.section == section.key }
                if (rows.isNotEmpty()) {
                    SectionLabel(section.title)
                    HqCard(Modifier.fillMaxWidth()) {
                        rows.forEach { ItemRow(it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HqPillButton(
                if (s?.status == "done") "Train again" else "Start workout",
                onClick = { s?.let(onStart) },
                modifier = Modifier.fillMaxWidth(),
            )
            HqPillButton("Move to another day", onClick = { moving = true }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
            HqPillButton("Edit workout", onClick = { s?.let { onEdit(it.templateId) } }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
        }
    }

    if (moving && s != null) {
        val from = LocalDate.parse(s.date)
        val today = remember { LocalDate.now() }
        ModalBottomSheet(
            onDismissRequest = { moving = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.card,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { SectionLabel("Move to") }
                (0..13).map { today.plusDays(it.toLong()) }.filter { it != from }.forEach { d ->
                    ListRow(
                        icon = Icons.Rounded.EventRepeat,
                        title = d.format(dayLine),
                        onClick = {
                            scope.launch {
                                db.schedule().move(s.id, d.toString())
                                reload++
                                moving = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(row: TemplateItemRow) {
    val c = HqTheme.colors
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HqText(row.exerciseName, 16.sp, FontWeight.Medium)
                HqText(detail(row), 13.sp, color = c.muted)
            }
            if (row.supersetGroup != null) HqText("Superset", 12.sp, FontWeight.SemiBold, c.primary)
        }
        if (row.note.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            HqText(row.note, 13.sp, color = c.muted)
        }
    }
}

private fun detail(r: TemplateItemRow): String {
    val parts = ArrayList<String>()
    val duration = r.durationSec
    if (duration != null) {
        parts += formatDuration(duration)
    } else {
        val reps = when {
            r.repsMin != null && r.repsMax != null && r.repsMin != r.repsMax -> "${r.repsMin}-${r.repsMax}"
            r.repsMax != null -> "${r.repsMax}"
            else -> ""
        }
        parts += if (reps.isEmpty()) "${r.sets} sets" else "${r.sets} x $reps"
    }
    r.weightKg?.let { parts += "${if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()} kg" }
    if (r.restSec > 0) parts += "rest ${formatDuration(r.restSec)}"
    return parts.joinToString(" · ")
}

/** Search the whole exercise list, narrowed by muscle and equipment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val c = HqTheme.colors
    var text by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    val muscles by produceState(emptyList<String>()) { value = db.exercises().muscles() }
    val equipments by produceState(emptyList<String>()) { value = db.exercises().equipments() }
    var results by remember { mutableStateOf(emptyList<ExerciseEntity>()) }
    LaunchedEffect(text, muscle, equipment) { results = db.exercises().searchFiltered(text.trim(), muscle, equipment) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Exercise library", onBack) }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { HqText("Search exercises", 13.sp, color = c.muted) },
            singleLine = true,
            shape = if (c.flat) androidx.compose.foundation.shape.RoundedCornerShape(22.dp) else androidx.compose.material3.OutlinedTextFieldDefaults.shape,
            colors = if (c.flat) androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, focusedBorderColor = c.primary,
                unfocusedContainerColor = c.card, focusedContainerColor = c.card,
            ) else androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        ChipRow("Muscle", muscles, muscle) { muscle = it }
        ChipRow("Equipment", equipments, equipment) { equipment = it }
        HqText(
            "${results.size}${if (results.size == 100) "+" else ""} shown",
            13.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { it.id }) { e ->
                if (c.flat) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(c.hairline))
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HqText(e.name, 16.sp, FontWeight.Medium)
                    HqText(listOf(e.muscle, e.equipment).filter { it.isNotBlank() }.joinToString(" · "), 13.sp, color = c.muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val c = HqTheme.colors
    val chipColors = if (c.flat) androidx.compose.material3.FilterChipDefaults.filterChipColors(
        containerColor = c.card, labelColor = c.ink, selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary,
    ) else androidx.compose.material3.FilterChipDefaults.filterChipColors()
    val chipBorder = if (c.flat) null else androidx.compose.material3.FilterChipDefaults.filterChipBorder(true, false)
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        item {
            FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { HqText("All ${label.lowercase()}", 13.sp, color = if (c.flat && selected.isEmpty()) c.onPrimary else c.ink) }, colors = chipColors, border = if (c.flat) null else chipBorder)
        }
        items(options) { o ->
            FilterChip(selected = selected == o, onClick = { onSelect(if (selected == o) "" else o) }, label = { HqText(o, 13.sp, color = if (c.flat && selected == o) c.onPrimary else c.ink) }, colors = chipColors, border = if (c.flat) null else chipBorder)
        }
    }
}
