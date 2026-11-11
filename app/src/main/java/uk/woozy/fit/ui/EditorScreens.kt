package uk.woozy.fit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.ExerciseEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.ScheduledWorkoutEntity
import uk.woozy.fit.data.db.TemplateItemEntity
import uk.woozy.fit.data.db.TemplateItemRow
import uk.woozy.fit.data.db.WorkoutTemplateEntity
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.model.Section
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun fmt(d: Double?): String = d?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()

/**
 * Edit a saved workout: rename, change sets, reps, weight, rest and notes, add or remove exercises, reorder,
 * link supersets, and put it on the calendar. Changes apply to future sessions; past sessions keep their own copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(db: HqFitDatabase, templateId: Long, onBack: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    val template by produceState<WorkoutTemplateEntity?>(null, templateId) { value = db.templates().get(templateId) }
    val items by remember(templateId) { db.templates().observeItems(templateId) }.collectAsState(initial = emptyList())
    var name by remember(template?.id) { mutableStateOf(template?.name.orEmpty()) }
    var editing by remember { mutableStateOf<TemplateItemRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun renumber(order: List<Long>) { order.forEachIndexed { i, id -> db.templates().setPosition(id, i) } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Edit workout", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it; scope.launch { if (it.isNotBlank()) db.templates().rename(templateId, it.trim()) } },
                label = { HqText("Name", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Section.entries.forEach { section ->
                val rows = items.filter { it.section == section.key }
                if (rows.isNotEmpty()) {
                    SectionLabel(section.title)
                    HqCard(Modifier.fillMaxWidth()) {
                        rows.forEach { r ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { editing = r }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    HqText(r.exerciseName, 16.sp, FontWeight.Medium)
                                    HqText(editorDetail(r), 13.sp, color = c.muted)
                                }
                                if (r.supersetGroup != null) HqText("Superset", 12.sp, FontWeight.SemiBold, c.primary)
                            }
                        }
                    }
                }
            }
            message?.let { HqText(it, 13.sp, color = c.muted) }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HqPillButton("Add exercise", onClick = { adding = true }, modifier = Modifier.fillMaxWidth())
            HqPillButton("Put on the calendar", onClick = { scheduling = true }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
        }
    }

    if (adding) {
        ExercisePickerSheet(db, onDismiss = { adding = false }, onPick = { e ->
            adding = false
            scope.launch {
                val last = db.templates().itemEntities(templateId)
                db.templates().insertItem(
                    TemplateItemEntity(
                        templateId = templateId, section = Section.WORKOUT.key, position = (last.maxOfOrNull { it.position } ?: -1) + 1,
                        exerciseId = e.id, sets = 3, repsMin = 8, repsMax = 12, durationSec = null, weightKg = null, restSec = 90, supersetGroup = null, note = "",
                    ),
                )
            }
        })
    }

    val r = editing
    if (r != null) {
        var sets by remember(r.id) { mutableStateOf(r.sets.toString()) }
        var repsMin by remember(r.id) { mutableStateOf(r.repsMin?.toString().orEmpty()) }
        var repsMax by remember(r.id) { mutableStateOf(r.repsMax?.toString().orEmpty()) }
        var weight by remember(r.id) { mutableStateOf(fmt(r.weightKg)) }
        var rest by remember(r.id) { mutableStateOf(r.restSec.toString()) }
        var seconds by remember(r.id) { mutableStateOf(r.durationSec?.toString().orEmpty()) }
        var note by remember(r.id) { mutableStateOf(r.note) }
        var section by remember(r.id) { mutableStateOf(Section.fromKey(r.section)) }
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HqText(r.exerciseName, 18.sp, FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section.entries.forEach { s ->
                        HqPillButton(s.title, onClick = { section = s }, style = if (section == s) PillStyle.Filled else PillStyle.Outline, modifier = Modifier.weight(1f))
                    }
                }
                NumberField("Sets", sets) { sets = it.filter(Char::isDigit) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Reps from", repsMin, Modifier.weight(1f)) { repsMin = it.filter(Char::isDigit) }
                    NumberField("Reps to", repsMax, Modifier.weight(1f)) { repsMax = it.filter(Char::isDigit) }
                }
                NumberField("Weight (kg)", weight) { weight = it }
                NumberField("Rest (seconds)", rest) { rest = it.filter(Char::isDigit) }
                NumberField("Timed item: seconds (leave empty for reps)", seconds) { seconds = it.filter(Char::isDigit) }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { HqText("Note", 13.sp, color = c.muted) }, modifier = Modifier.fillMaxWidth())
                HqPillButton("Save", modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        db.templates().updateItem(
                            r.id, section.key, sets.toIntOrNull()?.coerceAtLeast(1) ?: r.sets, repsMin.toIntOrNull(), repsMax.toIntOrNull(),
                            seconds.toIntOrNull(), weight.toDoubleOrNull(), rest.toIntOrNull() ?: r.restSec, note.trim(), r.supersetGroup,
                        )
                        editing = null
                    }
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HqPillButton("Move up", style = PillStyle.Outline, modifier = Modifier.weight(1f), onClick = {
                        scope.launch {
                            val order = db.templates().itemEntities(templateId).map { it.id }.toMutableList()
                            val i = order.indexOf(r.id)
                            if (i > 0) { order[i] = order[i - 1].also { order[i - 1] = order[i] }; renumber(order) }
                            editing = null
                        }
                    })
                    HqPillButton("Move down", style = PillStyle.Outline, modifier = Modifier.weight(1f), onClick = {
                        scope.launch {
                            val order = db.templates().itemEntities(templateId).map { it.id }.toMutableList()
                            val i = order.indexOf(r.id)
                            if (i in 0 until order.lastIndex) { order[i] = order[i + 1].also { order[i + 1] = order[i] }; renumber(order) }
                            editing = null
                        }
                    })
                }
                HqPillButton(if (r.supersetGroup != null) "Remove from superset" else "Superset with the one above", style = PillStyle.Outline, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        val all = db.templates().itemEntities(templateId)
                        val me = all.firstOrNull { it.id == r.id }
                        if (me != null) {
                            if (me.supersetGroup != null) {
                                db.templates().updateItem(me.id, me.section, me.sets, me.repsMin, me.repsMax, me.durationSec, me.weightKg, me.restSec, me.note, null)
                            } else {
                                val prev = all.getOrNull(all.indexOfFirst { it.id == me.id } - 1)
                                if (prev != null) {
                                    val g = prev.supersetGroup ?: ((all.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
                                    if (prev.supersetGroup == null) db.templates().updateItem(prev.id, prev.section, prev.sets, prev.repsMin, prev.repsMax, prev.durationSec, prev.weightKg, prev.restSec, prev.note, g)
                                    db.templates().updateItem(me.id, me.section, me.sets, me.repsMin, me.repsMax, me.durationSec, me.weightKg, me.restSec, me.note, g)
                                }
                            }
                        }
                        editing = null
                    }
                })
                HqPillButton("Remove exercise", style = PillStyle.Outline, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch { db.templates().deleteItem(r.id); editing = null }
                })
            }
        }
    }

    if (scheduling) {
        val today = remember { LocalDate.now() }
        ModalBottomSheet(onDismissRequest = { scheduling = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { SectionLabel("Add to the calendar on") }
                (0..13).map { today.plusDays(it.toLong()) }.forEach { d ->
                    ListRow(
                        icon = androidx.compose.material.icons.Icons.Rounded.EventRepeat,
                        title = d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)),
                        onClick = {
                            scope.launch {
                                val t = db.templates().get(templateId) ?: return@launch
                                db.schedule().insertAll(listOf(ScheduledWorkoutEntity(programmeId = null, templateId = t.id, templateName = t.name, date = d.toString(), originalDate = d.toString())))
                                message = "Added to ${d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))}"
                                scheduling = false
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun editorDetail(r: TemplateItemRow): String {
    val bits = ArrayList<String>()
    if (r.durationSec != null) bits += "${r.durationSec} s" else {
        val reps = when {
            r.repsMin != null && r.repsMax != null && r.repsMin != r.repsMax -> "${r.repsMin}-${r.repsMax}"
            r.repsMax != null -> "${r.repsMax}"
            else -> ""
        }
        bits += if (reps.isEmpty()) "${r.sets} sets" else "${r.sets} x $reps"
    }
    r.weightKg?.let { bits += "${fmt(it)} kg" }
    bits += "rest ${r.restSec} s"
    return bits.joinToString(" · ")
}

/** A new exercise of your own, for the library and for workouts. */
@Composable
fun NewExerciseScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackBar("New exercise", onBack)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { HqText("Name", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = muscle, onValueChange = { muscle = it }, label = { HqText("Main muscle, e.g. Chest", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = equipment, onValueChange = { equipment = it }, label = { HqText("Equipment, e.g. Dumbbell", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { HqText("How to do it (optional)", 13.sp, color = c.muted) }, modifier = Modifier.fillMaxWidth())
        HqPillButton("Save exercise", enabled = name.isNotBlank() && !saved, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                db.exercises().insert(ExerciseEntity(name = name.trim(), muscle = muscle.trim(), equipment = equipment.trim(), instructions = notes.trim(), custom = true))
                saved = true
            }
        })
        if (saved) HqText("Saved. Find it in the library or when adding an exercise to a workout.", 13.sp, color = c.muted)
    }
}

/** Create an empty workout and open it for editing. */
suspend fun createWorkout(db: HqFitDatabase, name: String): Long = db.templates().insert(WorkoutTemplateEntity(name = name))
