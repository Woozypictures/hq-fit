package uk.woozy.fit.ui

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

/** Train: the week strip and upcoming list come from the calendar; tap a day to see the workout. */
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
) {
    val c = HqTheme.colors
    val today = rememberToday()
    val monday = remember(today) { today.with(DayOfWeek.MONDAY) }
    val byDate = calendar.groupBy { it.date }
    val upcoming = calendar.filter { it.localDate() >= today && it.status == "planned" }.take(8)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Train")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (0..6).forEach { i ->
                        val day = monday.plusDays(i.toLong())
                        val isToday = day == today
                        val planned = byDate[day.toString()].orEmpty()
                        val first = planned.firstOrNull()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(if (first != null) Modifier.clickable(role = Role.Button, onClickLabel = first.templateName) { onOpen(first.id) } else Modifier),
                        ) {
                            HqText(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK), 12.sp, FontWeight.Medium, c.muted)
                            val isDone = first?.status == "done" || day.toString() in doneDates
                            val isMissed = !isDone && first != null && first.status == "planned" && day < today
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .then(
                                        when {
                                            isDone -> Modifier.background(c.protein)
                                            isToday -> Modifier.border(2.dp, c.primary, CircleShape)
                                            isMissed -> Modifier.border(2.dp, Color(0xFFE5484D), CircleShape)
                                            first != null -> Modifier.border(1.5.dp, c.primary.copy(alpha = 0.6f), CircleShape)
                                            else -> Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                HqText(
                                    if (isDone) "\u2713" else day.dayOfMonth.toString(), 14.sp,
                                    if (isToday || isDone) FontWeight.Bold else FontWeight.Normal,
                                    when {
                                        isDone -> Color.White
                                        isToday -> c.primary
                                        isMissed -> Color(0xFFE5484D)
                                        else -> c.ink
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                val weekAll = (0..6).flatMap { byDate[monday.plusDays(it.toLong()).toString()].orEmpty() }
                val doneDays = (0..6).count { i -> val d = monday.plusDays(i.toLong()).toString(); d in doneDates || byDate[d].orEmpty().any { it.status == "done" } }
                // A workout logged without a scheduled day still counts, so the total is days with a plan or a done workout.
                val weekDays = (0..6).count { i -> val d = monday.plusDays(i.toLong()).toString(); d in doneDates || byDate[d].orEmpty().isNotEmpty() }
                if (weekAll.isNotEmpty() || doneDays > 0) {
                    HqText("$doneDays of ${maxOf(weekDays, doneDays)} workouts done this week", 14.sp, FontWeight.Medium)
                }
                HqText(
                    if (upcoming.isEmpty()) "No workouts scheduled" else "Next: ${upcoming.first().templateName}, ${upcoming.first().localDate().format(dayLine)}",
                    14.sp, color = c.muted,
                )
            }
        }

        if (upcoming.isNotEmpty()) {
            SectionLabel("Coming up")
            HqCard(Modifier.fillMaxWidth()) {
                upcoming.forEach { w ->
                    val d = w.localDate()
                    val moved = w.date != w.originalDate
                    ListRow(
                        icon = Icons.Rounded.FitnessCenter,
                        title = w.templateName,
                        caption = d.format(dayLine) + if (moved) " · moved from ${LocalDate.parse(w.originalDate).format(dayLine)}" else "",
                        onClick = { onOpen(w.id) },
                    )
                }
            }
        }

        HqCard(Modifier.fillMaxWidth()) {
            ListRow(Icons.Rounded.CalendarMonth, "Programmes", "Pick the days for each workout and edit it", onClick = onProgrammes)
            ListRow(Icons.Rounded.FitnessCenter, "Exercise library", "Search and filter every exercise", onClick = onLibrary)
            ListRow(Icons.Rounded.Star, "Personal records", "Best lifts and history", onClick = onRecords)
            ListRow(Icons.Rounded.CalendarMonth, "Past workouts", "Open one to fix a weight or rep", onClick = onPast)
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
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        item {
            FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { HqText("All ${label.lowercase()}", 13.sp) })
        }
        items(options) { o ->
            FilterChip(selected = selected == o, onClick = { onSelect(if (selected == o) "" else o) }, label = { HqText(o, 13.sp) })
        }
    }
}
