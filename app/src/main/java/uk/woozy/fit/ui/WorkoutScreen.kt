package uk.woozy.fit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import uk.woozy.fit.RestTimer
import uk.woozy.fit.data.db.ExerciseEntity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.SessionSummary
import uk.woozy.fit.data.db.Sessions
import uk.woozy.fit.data.db.SetLogEntity
import uk.woozy.fit.designsystem.GradientHeader
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.KeypadKey
import uk.woozy.fit.designsystem.NumberKeypad
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.designsystem.SetRow
import uk.woozy.fit.designsystem.SetTableHeader
import uk.woozy.fit.designsystem.SpacedTitle
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.NumberEntry
import uk.woozy.fit.model.RecordType
import uk.woozy.fit.model.Section
import uk.woozy.fit.model.formatDuration

private const val WEIGHT_STEP = 2.5
private const val REP_STEP = 1.0

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private data class Editing(val setId: Long, val weight: Boolean, val buffer: String, val fresh: Boolean)

/**
 * The full-screen workout. Every change is written to the database as it happens, so closing the app or losing
 * the process leaves the session exactly as it was; Today shows "Resume workout".
 */
@Composable
fun ActiveWorkoutScreen(
    db: HqFitDatabase,
    sessionId: Long,
    onLeave: () -> Unit,
    onFinished: () -> Unit,
    onDiscarded: () -> Unit,
) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onLeave)

    val session by produceState<uk.woozy.fit.data.db.WorkoutSessionEntity?>(null, sessionId) { value = db.sessions().get(sessionId) }
    val setsFlow = remember(sessionId) { db.sessions().observeSets(sessionId) }
    val sets by setsFlow.collectAsState(initial = emptyList())
    val previous by produceState(emptyMap<Pair<Long, Int>, String>(), sessionId, sets.size) {
        value = sets.map { it.exerciseId to it.setIndex }.distinct().mapNotNull { (e, i) ->
            db.sessions().previous(e, i, sessionId)?.let { p ->
                (e to i) to "${NumberEntry.format(p.weightKg ?: 0.0)} x ${p.reps ?: 0}"
            }
        }.toMap()
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val context = LocalContext.current
    var restEnd by remember { mutableLongStateOf(RestTimer.endMs(context).takeIf { it > System.currentTimeMillis() } ?: 0L) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Pair<Int, PickMode>?>(null) }
    val restLeft = (restEnd - now).coerceAtLeast(0)
    LaunchedEffect(restEnd) {
        if (restEnd > 0) {
            delay((restEnd - System.currentTimeMillis()).coerceAtLeast(0))
            if (restEnd > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var lastDeleted by remember { mutableStateOf<SetLogEntity?>(null) }
    val isDone = session?.status == "done"

    fun toggle(set: SetLogEntity) {
        scope.launch {
            if (set.done) {
                db.sessions().updateDone(set.id, false, null)
            } else {
                if (set.durationSec == null && set.reps == null) {
                    db.sessions().updateValues(set.id, set.weightKg, set.targetRepsMax ?: set.targetRepsMin)
                }
                db.sessions().updateDone(set.id, true, System.currentTimeMillis())
                if (set.restSec > 0) {
                    restEnd = System.currentTimeMillis() + set.restSec * 1000L
                    if (Build.VERSION.SDK_INT >= 33 && !asked) {
                        asked = true
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    RestTimer.start(context, restEnd, set.exerciseName)
                }
            }
        }
    }

    fun key(k: KeypadKey) {
        val e = editing ?: return
        val set = sets.firstOrNull { it.id == e.setId } ?: return
        fun save(text: String) {
            editing = e.copy(buffer = text, fresh = false)
            scope.launch {
                if (e.weight) db.sessions().updateValues(set.id, text.toDoubleOrNull(), set.reps)
                else db.sessions().updateValues(set.id, set.weightKg, text.toDoubleOrNull()?.toInt())
            }
        }
        val base = if (e.fresh) "" else e.buffer
        when (k) {
            is KeypadKey.Digit -> save(NumberEntry.digit(base, k.digit))
            KeypadKey.Dot -> if (e.weight) save(NumberEntry.dot(base))
            KeypadKey.Backspace -> save(NumberEntry.backspace(e.buffer))
            KeypadKey.Plus -> save(NumberEntry.step(e.buffer, if (e.weight) WEIGHT_STEP else REP_STEP))
            KeypadKey.Minus -> save(NumberEntry.step(e.buffer, if (e.weight) -WEIGHT_STEP else -REP_STEP))
            KeypadKey.Done -> {
                editing = if (e.weight) Editing(set.id, false, set.reps?.toString().orEmpty(), true) else null
            }
        }
    }

    val groups = sets.groupBy { it.position }.toSortedMap().values.toList()
    val notTicked = sets.count { !it.done }

    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HqText("WORKOUT", 12.sp, FontWeight.SemiBold, c.onGradient, Modifier.weight(1f), letterSpacing = 1.sp)
                    HqText(session?.let { clock((it.endedMs ?: now) - it.startedMs) } ?: "", 16.sp, FontWeight.Bold, c.onGradient)
                }
                Spacer(Modifier.height(4.dp))
                SpacedTitle(session?.name ?: "", color = c.onGradient, size = 20.sp)
                if (restLeft > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HqText("Rest ${clock(restLeft + 999)}", 15.sp, FontWeight.SemiBold, c.onGradient, Modifier.weight(1f))
                        HqText(
                            "Skip", 14.sp, FontWeight.SemiBold, c.onGradient,
                            Modifier.clip(CircleShape).clickable(role = Role.Button) { restEnd = 0; RestTimer.cancel(context) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        lastDeleted?.let { gone ->
            Row(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                HqText("Set deleted", 14.sp, color = c.muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { db.sessions().restoreSet(gone); lastDeleted = null } }) { HqText("Undo", 14.sp, FontWeight.SemiBold, c.primary) }
                TextButton(onClick = { lastDeleted = null }) { HqText("Dismiss", 14.sp, color = c.muted) }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(groups, key = { _, g -> g.first().position }) { index, group ->
                val first = group.first()
                val newSection = index == 0 || groups[index - 1].first().section != first.section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (newSection) SectionLabel(Section.fromKey(first.section).title)
                    ExerciseCard(
                        group = group,
                        previous = previous,
                        editing = editing,
                        onToggle = ::toggle,
                        onEdit = { set, weight ->
                            val text = if (weight) set.weightKg?.let { NumberEntry.format(it) }.orEmpty() else set.reps?.toString().orEmpty()
                            editing = Editing(set.id, weight, text, true)
                        },
                        onAddSet = {
                            scope.launch {
                                val last = group.last()
                                db.sessions().insertSet(last.copy(id = 0, setIndex = last.setIndex + 1, reps = null, done = false, doneAtMs = null))
                            }
                        },
                        onDeleteLast = {
                            if (group.size > 1) scope.launch { db.sessions().deleteSet(group.last().id) }
                        },
                        onPick = { mode -> picking = first.position to mode },
                        onDeleteSet = { set -> lastDeleted = set; scope.launch { db.sessions().deleteSet(set.id) } },
                        onSetType = { set, t -> scope.launch { db.sessions().updateType(set.id, t) } },
                        onSetRpe = { set, r -> scope.launch { db.sessions().updateRpe(set.id, r) } },
                    )
                }
            }
        }

        if (editing != null) {
            val e = editing!!
            NumberKeypad(onKey = ::key, stepLabel = if (e.weight) "2.5 kg" else "1 rep")
        } else {
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HqPillButton(if (isDone) "Save changes" else "Finish workout", onClick = { if (isDone) { onLeave() } else if (notTicked > 0) confirmFinish = true else scope.launch { RestTimer.cancel(context); Sessions.finish(db, sessionId, System.currentTimeMillis()); onFinished() } }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onLeave) { HqText("Close, keep running", 14.sp, color = c.muted) }
                    if (!isDone) TextButton(onClick = { confirmDiscard = true }) { HqText("Discard", 14.sp, color = c.muted) }
                }
            }
        }
    }

    picking?.let { (position, mode) ->
        ExercisePickerSheet(
            db = db,
            onDismiss = { picking = null },
            onPick = { exercise ->
                picking = null
                scope.launch {
                    when (mode) {
                        PickMode.REPLACE -> Sessions.replace(db, sessionId, position, exercise)
                        PickMode.BEFORE -> Sessions.add(db, sessionId, position, exercise, Sessions.Where.BEFORE)
                        PickMode.AFTER -> Sessions.add(db, sessionId, position, exercise, Sessions.Where.AFTER)
                        PickMode.SUPERSET -> Sessions.add(db, sessionId, position, exercise, Sessions.Where.SUPERSET)
                    }
                }
            },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { HqText("Finish workout?", 18.sp, FontWeight.SemiBold) },
            text = { HqText("$notTicked sets are not ticked. They will not count.", 14.sp, color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    scope.launch { RestTimer.cancel(context); Sessions.finish(db, sessionId, System.currentTimeMillis()); onFinished() }
                }) { HqText("Finish", 15.sp, FontWeight.SemiBold, c.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { HqText("Keep going", 15.sp, color = c.muted) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { HqText("Discard this workout?", 18.sp, FontWeight.SemiBold) },
            text = { HqText("Everything logged in it is deleted. The day stays on your calendar.", 14.sp, color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    scope.launch { RestTimer.cancel(context); Sessions.discard(db, sessionId); onDiscarded() }
                }) { HqText("Discard", 15.sp, FontWeight.SemiBold, c.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { HqText("Cancel", 15.sp, color = c.muted) } },
        )
    }
}

@Composable
private fun ExerciseCard(
    group: List<SetLogEntity>,
    previous: Map<Pair<Long, Int>, String>,
    editing: Editing?,
    onToggle: (SetLogEntity) -> Unit,
    onEdit: (SetLogEntity, Boolean) -> Unit,
    onAddSet: () -> Unit,
    onDeleteLast: () -> Unit,
    onPick: (PickMode) -> Unit,
    onDeleteSet: (SetLogEntity) -> Unit,
    onSetType: (SetLogEntity, String) -> Unit,
    onSetRpe: (SetLogEntity, Double?) -> Unit,
) {
    val c = HqTheme.colors
    val first = group.first()
    var menu by remember { mutableStateOf(false) }
    HqCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HqText(first.exerciseName, 16.sp, FontWeight.SemiBold)
                    val timed = first.durationSec
                    val target = when {
                        timed != null -> formatDuration(timed)
                        first.targetRepsMin != null && first.targetRepsMax != null && first.targetRepsMin != first.targetRepsMax ->
                            "Target ${first.targetRepsMin}-${first.targetRepsMax} reps"
                        first.targetRepsMax != null -> "Target ${first.targetRepsMax} reps"
                        else -> ""
                    }
                    if (target.isNotEmpty()) HqText(target, 13.sp, color = c.muted)
                }
                if (first.supersetGroup != null) HqText("Superset", 12.sp, FontWeight.SemiBold, c.primary)
                Box {
                    Icon(
                        Icons.Rounded.MoreVert, contentDescription = "Exercise options", tint = c.muted,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }.padding(8.dp),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf(
                            "Replace exercise" to PickMode.REPLACE,
                            "Add exercise before" to PickMode.BEFORE,
                            "Add exercise after" to PickMode.AFTER,
                            "Add superset" to PickMode.SUPERSET,
                        ).forEach { (label, mode) ->
                            DropdownMenuItem(text = { HqText(label, 15.sp) }, onClick = { menu = false; onPick(mode) })
                        }
                    }
                }
            }
            if (first.note.isNotBlank()) {
                HqText(first.note, 13.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
            if (first.durationSec != null) {
                group.forEach { set -> TimedRow(set, onToggle) }
            } else {
                SetTableHeader()
                group.forEach { set ->
                    androidx.compose.runtime.key(set.id) {
                        val e = editing?.takeIf { it.setId == set.id }
                        var setMenu by remember { mutableStateOf(false) }
                        val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                            if (v == SwipeToDismissBoxValue.EndToStart && group.size > 1) { onDeleteSet(set); true } else false
                        })
                        SwipeToDismissBox(
                            state = dismiss,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(Modifier.fillMaxSize().background(c.track), contentAlignment = Alignment.CenterEnd) {
                                    HqText("Delete", 14.sp, FontWeight.SemiBold, c.muted, Modifier.padding(end = 20.dp))
                                }
                            },
                        ) {
                            Box(Modifier.background(c.card).combinedClickable(onClick = {}, onLongClick = { setMenu = true })) {
                                SetRow(
                                    number = when (set.setType) { "warmup" -> "W"; "drop" -> "D"; "failure" -> "F"; else -> set.setIndex.toString() },
                                    previous = previous[set.exerciseId to set.setIndex].orEmpty() + (set.rpe?.let { "  @${NumberEntry.format(it)}" } ?: ""),
                                    weight = if (e != null && e.weight) e.buffer else set.weightKg?.let { NumberEntry.format(it) }.orEmpty(),
                                    reps = if (e != null && !e.weight) e.buffer else set.reps?.toString().orEmpty(),
                                    done = set.done,
                                    onToggle = { onToggle(set) },
                                    onWeightClick = { onEdit(set, true) },
                                    onRepsClick = { onEdit(set, false) },
                                    weightActive = e?.weight == true,
                                    repsActive = e != null && !e.weight,
                                )
                                DropdownMenu(expanded = setMenu, onDismissRequest = { setMenu = false }) {
                                    listOf("normal" to "Normal set", "warmup" to "Warm-up set", "drop" to "Drop set", "failure" to "Failure set").forEach { (k, label) ->
                                        DropdownMenuItem(text = { HqText(label, 15.sp) }, onClick = { setMenu = false; onSetType(set, k) })
                                    }
                                    listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { r ->
                                        DropdownMenuItem(text = { HqText("RPE ${r.toInt()}", 15.sp) }, onClick = { setMenu = false; onSetRpe(set, r) })
                                    }
                                    DropdownMenuItem(text = { HqText("Clear RPE", 15.sp) }, onClick = { setMenu = false; onSetRpe(set, null) })
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onAddSet) { HqText("Add set", 14.sp, FontWeight.SemiBold, c.primary) }
                    if (group.size > 1) TextButton(onClick = onDeleteLast) { HqText("Delete last set", 14.sp, color = c.muted) }
                }
            }
            if (first.durationSec != null) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TimedRow(set: SetLogEntity, onToggle: (SetLogEntity) -> Unit) {
    val c = HqTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HqText(if (set.done) "Done" else "Not done", 15.sp, color = if (set.done) c.primary else c.muted, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (set.done) c.ink else c.track)
                .toggleable(value = set.done, role = Role.Checkbox, onValueChange = { onToggle(set) }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = if (set.done) "Done" else "Mark done",
                tint = if (set.done) c.page else c.muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** After finishing: how long, how much, any records, and how it felt. */
@Composable
fun WorkoutSummaryScreen(db: HqFitDatabase, sessionId: Long, onDone: () -> Unit, onEdit: () -> Unit = {}) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    val summary by produceState<SessionSummary?>(null, sessionId) { value = Sessions.summary(db, sessionId, System.currentTimeMillis()) }
    var feeling by remember { mutableStateOf<Int?>(null) }
    BackHandler(onBack = onDone)

    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)) {
                HqText("WORKOUT COMPLETE", 12.sp, FontWeight.SemiBold, c.onGradient)
                Spacer(Modifier.height(6.dp))
                SpacedTitle(summary?.name ?: "", color = c.onGradient, size = 20.sp)
            }
        }
        val s = summary
        Column(
            Modifier.weight(1f).padding(16.dp).then(Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s != null) {
                HqCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Time", clock(s.durationMs))
                        Stat("Sets", "${s.setsDone}/${s.setsTotal}")
                        Stat("Volume", "${formatWhole(s.volumeKg)} kg")
                    }
                }
                if (s.records.isNotEmpty()) {
                    SectionLabel("New records")
                    HqCard(Modifier.fillMaxWidth()) {
                        s.records.forEach { r ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                HqText(r.exerciseName, 16.sp, FontWeight.SemiBold)
                                HqText(
                                    r.records.joinToString(" · ") { it.label } +
                                        "  (${NumberEntry.format(r.best.weightKg)} kg x ${r.best.reps})",
                                    13.sp, color = c.primary,
                                )
                            }
                        }
                    }
                } else {
                    HqText("No new records this time. The first session of a lift sets the baseline.", 14.sp, color = c.muted)
                }
                SectionLabel("How was it?")
                SegmentedChoice(
                    options = listOf("1", "2", "3", "4", "5"),
                    selectedIndex = (feeling ?: 0) - 1,
                    onSelect = {
                        feeling = it + 1
                        scope.launch { db.sessions().setFeeling(sessionId, it + 1) }
                    },
                )
            }
        }
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            HqPillButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth(), style = PillStyle.Filled)
            HqPillButton("Edit sets", onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), style = PillStyle.Outline)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HqText(value, 20.sp, FontWeight.Bold)
        HqText(label, 12.sp, color = HqTheme.colors.muted, textAlign = TextAlign.Center)
    }
}

private enum class PickMode { REPLACE, BEFORE, AFTER, SUPERSET }

/** Search the library and pick one exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerSheet(db: HqFitDatabase, onDismiss: () -> Unit, onPick: (ExerciseEntity) -> Unit) {
    val c = HqTheme.colors
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<ExerciseEntity>()) }
    LaunchedEffect(text) { results = db.exercises().searchFiltered(text.trim(), "", "") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { HqText("Search exercises", 13.sp, color = c.muted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(results, key = { it.id }) { e ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(e) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        HqText(e.name, 16.sp, FontWeight.Medium)
                        HqText(listOf(e.muscle, e.equipment).filter { it.isNotBlank() }.joinToString(" · "), 13.sp, color = c.muted)
                    }
                }
            }
        }
    }
}
