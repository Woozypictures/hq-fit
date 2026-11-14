package uk.woozy.fit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
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
import uk.woozy.fit.designsystem.glass
import uk.woozy.fit.designsystem.glassSource
import androidx.compose.foundation.layout.windowInsetsPadding
import uk.woozy.fit.designsystem.SetTableHeader
import uk.woozy.fit.designsystem.SpacedTitle
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.NumberEntry
import uk.woozy.fit.model.RecordType
import uk.woozy.fit.model.Section
import uk.woozy.fit.model.formatDuration

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
    onHistory: (Long) -> Unit,
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
    val weightStep = remember { WorkoutPrefs.weightStep(context) }
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
                if (set.restSec > 0 && WorkoutPrefs.autoRest(context)) {
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
            KeypadKey.Plus -> save(NumberEntry.step(e.buffer, if (e.weight) weightStep else REP_STEP))
            KeypadKey.Minus -> save(NumberEntry.step(e.buffer, if (e.weight) -weightStep else -REP_STEP))
            KeypadKey.Done -> {
                editing = if (e.weight) Editing(set.id, false, set.reps?.toString().orEmpty(), true) else null
            }
        }
    }

    val groups = sets.groupBy { it.position }.toSortedMap().values.toList()
    val notTicked = sets.count { !it.done }
    val sectionKeys = groups.map { it.first().section }.distinct()
    val finishIndex = sectionKeys.size
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { sectionKeys.size + 1 })
    val phases = remember { androidx.compose.runtime.mutableStateMapOf<String, SectionPhase>() }
    var infoFor by remember { mutableStateOf<String?>(null) }
    var noteFor by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var menu by remember { mutableStateOf(false) }

    fun pagesOf(sec: String): List<List<List<SetLogEntity>>> {
        val out = mutableListOf<MutableList<List<SetLogEntity>>>()
        groups.filter { it.first().section == sec }.forEach { g ->
            val sg = g.first().supersetGroup
            val prev = out.lastOrNull()
            if (sg != null && prev != null && prev.first().first().supersetGroup == sg) prev.add(g) else out.add(mutableListOf(g))
        }
        return out
    }

    // Work out where each section stands from the ticked sets, then open on the first unfinished one.
    var jumped by remember { mutableStateOf(false) }
    LaunchedEffect(sectionKeys, isDone) {
        sectionKeys.forEach { sec ->
            val mine = sets.filter { it.section == sec }
            if (sec !in phases || (isDone && phases[sec] == SectionPhase.INTRO)) {
                phases[sec] = when {
                    isDone -> SectionPhase.ACTIVE
                    mine.isNotEmpty() && mine.all { it.done } -> SectionPhase.COMPLETE
                    mine.any { it.done } -> SectionPhase.ACTIVE
                    else -> SectionPhase.INTRO
                }
            }
        }
        if (!jumped && sectionKeys.isNotEmpty()) {
            jumped = true
            val i = sectionKeys.indexOfFirst { phases[it] != SectionPhase.COMPLETE }
            if (i > 0) pager.scrollToPage(i)
        }
    }
    // Swiping past the last section finishes the workout (or leaves, when editing a finished one).
    LaunchedEffect(pager.settledPage, sectionKeys.size) {
        if (sectionKeys.isNotEmpty() && pager.settledPage == finishIndex) {
            if (isDone) onLeave()
            else if (notTicked > 0) confirmFinish = true
            else { RestTimer.cancel(context); Sessions.finish(db, sessionId, System.currentTimeMillis()); onFinished() }
        }
    }

    @Composable
    fun ExerciseBlock(group: List<SetLogEntity>) {
        val first = group.first()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExerciseHero(first.exerciseName, onClick = { infoFor = first.exerciseName })
            ExerciseCard(
                group = group,
                previous = previous,
                editing = editing,
                showThumb = false,
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
                onDeleteLast = { if (group.size > 1) scope.launch { db.sessions().deleteSet(group.last().id) } },
                onPick = { mode -> picking = first.position to mode },
                onDeleteSet = { set -> lastDeleted = set; scope.launch { db.sessions().deleteSet(set.id) } },
                onSetType = { set, t -> scope.launch { db.sessions().updateType(set.id, t) } },
                onSetRpe = { set, r -> scope.launch { db.sessions().updateRpe(set.id, r) } },
                onInfo = { infoFor = first.exerciseName },
                onHistory = { onHistory(first.exerciseId) },
                onNote = { noteFor = first.position to first.note },
                onMarkAll = { scope.launch { markAllDone(db, group) } },
            )
        }
    }

    val backdrop = uk.woozy.fit.designsystem.rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Column(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown, contentDescription = "Close, keep running", tint = c.onGradient,
                        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onLeave).padding(8.dp),
                    )
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        HqText(
                            "Total Duration: " + (session?.let { clock((it.endedMs ?: now) - it.startedMs) } ?: ""),
                            13.sp, FontWeight.SemiBold, c.onGradient,
                        )
                        Spacer(Modifier.height(6.dp))
                        Dots(sectionKeys.size + 1, pager.currentPage, c.onGradient)
                    }
                    Box {
                        Icon(
                            Icons.Rounded.MoreVert, contentDescription = "Workout options", tint = c.onGradient,
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }.padding(8.dp),
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { HqText(if (isDone) "Save and close" else "Finish workout now", 15.sp) },
                                onClick = {
                                    menu = false
                                    if (isDone) onLeave() else if (notTicked > 0) confirmFinish = true
                                    else scope.launch { RestTimer.cancel(context); Sessions.finish(db, sessionId, System.currentTimeMillis()); onFinished() }
                                },
                            )
                            if (!isDone) DropdownMenuItem(text = { HqText("Discard workout", 15.sp) }, onClick = { menu = false; confirmDiscard = true })
                        }
                    }
                }
                if (restLeft > 0 && !c.flat) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        HqText("Rest ${clock(restLeft + 999)}", if (c.flat) 24.sp else 15.sp, FontWeight.Bold, c.onGradient, Modifier.weight(1f), letterSpacing = if (c.flat) (-0.5).sp else androidx.compose.ui.unit.TextUnit.Unspecified)
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

        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).glassSource(backdrop),
            userScrollEnabled = phases[sectionKeys.getOrNull(pager.currentPage)] != SectionPhase.ACTIVE,
            beyondViewportPageCount = 1,
        ) { page ->
            if (page >= sectionKeys.size) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { HqText("Finishing", 16.sp, color = c.muted) }
            } else {
                val sec = sectionKeys[page]
                val title = Section.fromKey(sec).title
                val pages = pagesOf(sec)
                val count = pages.sumOf { it.size }
                when (phases[sec] ?: SectionPhase.INTRO) {
                    SectionPhase.INTRO -> SectionIntro(title, count, pages.flatten(), onStart = { phases[sec] = SectionPhase.ACTIVE })
                    SectionPhase.COMPLETE -> SectionComplete(
                        title, count, last = page == sectionKeys.lastIndex,
                        onEdit = { phases[sec] = SectionPhase.ACTIVE },
                    )
                    SectionPhase.ACTIVE -> {
                        val inner = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
                        var innerJumped by remember { mutableStateOf(false) }
                        LaunchedEffect(pages.size) {
                            if (!innerJumped && pages.isNotEmpty()) {
                                innerJumped = true
                                val i = pages.indexOfFirst { pg -> pg.any { g -> g.any { !it.done } } }
                                if (i > 0) inner.scrollToPage(i)
                            }
                        }
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Close, contentDescription = "Back to section overview", tint = c.muted,
                                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { phases[sec] = SectionPhase.INTRO }.padding(10.dp),
                                )
                                HqText(title, 16.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Spacer(Modifier.size(44.dp))
                            }
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = inner,
                                modifier = Modifier.weight(1f),
                                key = { pages.getOrNull(it)?.first()?.first()?.position ?: it },
                            ) { i ->
                                val pg = pages.getOrNull(i) ?: return@HorizontalPager
                                val allDone = pg.all { g -> g.all { it.done } }
                                var wasDone by remember { mutableStateOf(allDone) }
                                LaunchedEffect(allDone) {
                                    if (allDone && !wasDone && i < pages.lastIndex) {
                                        delay(500)
                                        inner.animateScrollToPage(i + 1)
                                    }
                                    wasDone = allDone
                                }
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    if (pg.size > 1) HqText("Superset", 12.sp, FontWeight.SemiBold, c.primary)
                                    pg.forEach { g -> androidx.compose.runtime.key(g.first().position) { ExerciseBlock(g) } }
                                    if (pg.size > 1 && !allDone) {
                                        TextButton(onClick = { scope.launch { pg.forEach { g -> markAllDone(db, g) } } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                            HqText("Mark all supersets", 14.sp, FontWeight.SemiBold, c.primary)
                                        }
                                    }
                                    if (i == pages.lastIndex) {
                                        HqPillButton(
                                            "Complete section",
                                            onClick = {
                                                phases[sec] = SectionPhase.COMPLETE
                                                if (page < sectionKeys.lastIndex) scope.launch { delay(1200); pager.animateScrollToPage(page + 1) }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Dots(pages.size, inner.currentPage, c.primary, c.track)
                            }
                        }
                    }
                }
            }
        }
        infoFor?.let { ExerciseDetailSheet(db, it) { infoFor = null } }
        noteFor?.let { (pos, current) ->
            var text by remember(pos) { mutableStateOf(current) }
            AlertDialog(
                onDismissRequest = { noteFor = null },
                title = { HqText("Exercise note", 18.sp, FontWeight.SemiBold) },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { HqText("Seat height, grip, how it felt", 14.sp, color = c.muted) }, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { scope.launch { db.sessions().updateNote(sessionId, pos, text.trim()) }; noteFor = null }) { HqText("Save", 15.sp, FontWeight.SemiBold, c.primary) } },
                dismissButton = { TextButton(onClick = { noteFor = null }) { HqText("Cancel", 15.sp, color = c.muted) } },
            )
        }

        if (editing != null) {
            val e = editing!!
            NumberKeypad(onKey = ::key, stepLabel = if (e.weight) "${NumberEntry.format(weightStep)} kg" else "1 rep")
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
        if (c.flat && restLeft > 0 && editing == null) {
            val gStyle = remember(c) { uk.woozy.fit.designsystem.glassStyle(c.dark, c.ink, c.muted) }
            Row(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                    .padding(bottom = 16.dp).height(60.dp)
                    .glass(backdrop, gStyle, androidx.compose.foundation.shape.RoundedCornerShape(30.dp))
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HqText("Rest", 14.sp, FontWeight.Medium, c.muted)
                Spacer(Modifier.width(12.dp))
                HqText(clock(restLeft + 999), 26.sp, FontWeight.Bold, c.ink, letterSpacing = (-0.5).sp)
                Spacer(Modifier.width(20.dp))
                HqText(
                    "Skip", 14.sp, FontWeight.SemiBold, c.primary,
                    Modifier.clip(CircleShape).clickable(role = Role.Button) { restEnd = 0; RestTimer.cancel(context) }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
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
            onDismissRequest = { confirmFinish = false; scope.launch { pager.animateScrollToPage((finishIndex - 1).coerceAtLeast(0)) } },
            title = { HqText("Finish workout?", 18.sp, FontWeight.SemiBold) },
            text = { HqText("$notTicked sets are not ticked. They will not count.", 14.sp, color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    scope.launch { RestTimer.cancel(context); Sessions.finish(db, sessionId, System.currentTimeMillis()); onFinished() }
                }) { HqText("Finish", 15.sp, FontWeight.SemiBold, c.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false; scope.launch { pager.animateScrollToPage((finishIndex - 1).coerceAtLeast(0)) } }) { HqText("Keep going", 15.sp, color = c.muted) } },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    onInfo: () -> Unit,
    onMarkAll: () -> Unit,
    onHistory: () -> Unit,
    onNote: () -> Unit,
    showThumb: Boolean = true,
) {
    val c = HqTheme.colors
    val first = group.first()
    HqCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClickLabel = "Show how to do it", onClick = onInfo), verticalAlignment = Alignment.CenterVertically) {
                    if (showThumb) ExerciseThumb(first.exerciseName, 44.dp, Modifier.padding(end = 10.dp))
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
                }
                if (first.supersetGroup != null) HqText("Superset", 12.sp, FontWeight.SemiBold, c.primary)
            }
            ExerciseIconRow(
                hasNote = first.note.isNotBlank(),
                onHistory = onHistory, onNote = onNote,
                onReplace = { onPick(PickMode.REPLACE) },
                onAdd = onPick, onInfo = onInfo, onMarkAll = onMarkAll,
            )
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
            if (group.any { !it.done }) {
                TextButton(onClick = onMarkAll, modifier = Modifier.padding(horizontal = 8.dp)) { HqText("Mark all complete", 14.sp, FontWeight.SemiBold, c.primary) }
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
        // Everfit-style timer for timed exercises: tap to count down; at zero it buzzes and ticks the set off.
        val total = set.durationSec ?: 0
        var left by androidx.compose.runtime.saveable.rememberSaveable(set.id) { mutableStateOf(total) }
        var running by androidx.compose.runtime.saveable.rememberSaveable(set.id) { mutableStateOf(false) }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        LaunchedEffect(running) {
            while (running && left > 0) { kotlinx.coroutines.delay(1000); left -= 1 }
            if (running && left <= 0) {
                running = false
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                if (!set.done) onToggle(set)
            }
        }
        Column(Modifier.weight(1f)) {
            if (total > 0 && !set.done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HqText("%d:%02d".format(left / 60, left % 60), 26.sp, FontWeight.Bold, if (running) c.primary else c.ink, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.clip(CircleShape).background(if (running) c.track else c.primary)
                            .clickable(role = Role.Button) { if (left <= 0) left = total; running = !running }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { HqText(if (running) "Pause" else if (left < total) "Resume" else "Start", 14.sp, FontWeight.SemiBold, if (running) c.ink else c.onPrimary) }
                    if (left < total && !running) HqText("Reset", 14.sp, FontWeight.SemiBold, c.muted, modifier = Modifier.padding(start = 12.dp).clickable(role = Role.Button) { left = total })
                }
                if (total > 0) uk.woozy.fit.designsystem.HqProgressBar(1f - left / total.toFloat(), c.primary, Modifier.padding(top = 8.dp, end = 12.dp), height = 4.dp)
            } else HqText(if (set.done) "Done" else "Not done", 15.sp, color = if (set.done) c.primary else c.muted)
        }
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
    val sets by produceState(emptyList<SetLogEntity>(), sessionId) { value = db.sessions().sets(sessionId).filter { it.done } }
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
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
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
                if (sets.isNotEmpty()) {
                    SectionLabel("Workout details")
                    HqCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            sets.groupBy { it.position }.toSortedMap().values.forEach { group ->
                                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    HqText(group.first().exerciseName, 15.sp, FontWeight.SemiBold)
                                    HqText(
                                        group.joinToString("  ·  ") { st ->
                                            val secs = st.durationSec
                                            if (secs != null) "${secs / 60} min"
                                            else "${NumberEntry.format(st.weightKg ?: 0.0)} kg x ${st.reps ?: 0}"
                                        },
                                        13.sp, color = c.muted,
                                    )
                                }
                            }
                        }
                    }
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
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(e) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExerciseThumb(e.name, 40.dp, Modifier.padding(end = 12.dp))
                        Column {
                            HqText(e.name, 16.sp, FontWeight.Medium)
                            HqText(listOf(e.muscle, e.equipment).filter { it.isNotBlank() }.joinToString(" · "), 13.sp, color = c.muted)
                        }
                    }
                }
            }
        }
    }
}


private enum class SectionPhase { INTRO, ACTIVE, COMPLETE }

/** Ticks every remaining set of one exercise, filling the target reps where none were typed. No rest timer. */
private suspend fun markAllDone(db: HqFitDatabase, group: List<SetLogEntity>) {
    val now = System.currentTimeMillis()
    group.filter { !it.done }.forEach { set ->
        if (set.durationSec == null && set.reps == null) {
            db.sessions().updateValues(set.id, set.weightKg, set.targetRepsMax ?: set.targetRepsMin)
        }
        db.sessions().updateDone(set.id, true, now)
    }
}

@Composable
private fun Dots(count: Int, current: Int, on: androidx.compose.ui.graphics.Color, off: androidx.compose.ui.graphics.Color = on.copy(alpha = 0.35f)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(if (i == current) 8.dp else 6.dp).clip(CircleShape).background(if (i == current) on else off))
        }
    }
}

/** Before a section starts: its name, size, Start Section and the exercises it holds. */
@Composable
private fun SectionIntro(title: String, count: Int, groups: List<List<SetLogEntity>>, onStart: () -> Unit) {
    val c = HqTheme.colors
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HqText(title, 24.sp, FontWeight.Bold)
                HqText("$count exercises", 14.sp, color = c.muted)
                Spacer(Modifier.height(8.dp))
                HqPillButton("Start Section", onClick = onStart)
            }
        }
        itemsIndexed(groups, key = { _, g -> g.first().position }) { _, g ->
            val first = g.first()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(first.exerciseName, 56.dp, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    HqText(first.exerciseName, 16.sp, FontWeight.SemiBold)
                    val target = when {
                        first.durationSec != null -> formatDuration(first.durationSec!!)
                        first.targetRepsMin != null && first.targetRepsMax != null && first.targetRepsMin != first.targetRepsMax -> "${first.targetRepsMin}-${first.targetRepsMax} reps"
                        first.targetRepsMax != null -> "${first.targetRepsMax} reps"
                        else -> ""
                    }
                    if (target.isNotEmpty()) HqText(target, 13.sp, color = c.muted)
                    if (first.supersetGroup != null) HqText("Superset", 12.sp, FontWeight.SemiBold, c.primary)
                }
                HqText("x${g.size}", 14.sp, color = c.muted)
            }
        }
    }
}

/** Shown after Complete section: a badge with the exercise count. The last section says to swipe to finish. */
@Composable
private fun SectionComplete(title: String, count: Int, last: Boolean, onEdit: () -> Unit) {
    val c = HqTheme.colors
    val bits = listOf(c.primary, c.protein, c.fat, c.carbs, c.warn)
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            listOf(0.12f to 0.08f, 0.3f to 0.15f, 0.5f to 0.06f, 0.7f to 0.14f, 0.88f to 0.1f, 0.2f to 0.3f, 0.82f to 0.32f, 0.6f to 0.24f).forEachIndexed { i, (x, y) ->
                rotate(20f * i + 15f, androidx.compose.ui.geometry.Offset(w * x, h * y)) {
                    drawRect(bits[i % bits.size], androidx.compose.ui.geometry.Offset(w * x, h * y), androidx.compose.ui.geometry.Size(14f, 24f))
                }
            }
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val path = androidx.compose.ui.graphics.Path()
                    for (k in 0 until 6) {
                        val ang = Math.toRadians(60.0 * k - 90)
                        val x = cx + (r * Math.cos(ang)).toFloat()
                        val y = cy + (r * Math.sin(ang)).toFloat()
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, c.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HqText("$count", 40.sp, FontWeight.Bold, c.onPrimary)
                    HqText(if (count == 1) "exercise" else "exercises", 13.sp, color = c.onPrimary)
                }
            }
            Spacer(Modifier.height(20.dp))
            HqText("COMPLETED", 12.sp, FontWeight.SemiBold, c.primary, letterSpacing = 1.sp)
            HqText(title, 22.sp, FontWeight.Bold)
            if (last) {
                Spacer(Modifier.height(16.dp))
                HqText("Swipe to complete workout", 15.sp, FontWeight.SemiBold, c.muted)
            }
        }
        TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { HqText("Edit", 14.sp, FontWeight.SemiBold, c.primary) }
    }
}


/** Everfit-style row under the exercise name: history, note, replace, add, more. */
@Composable
private fun ExerciseIconRow(
    hasNote: Boolean,
    onHistory: () -> Unit,
    onNote: () -> Unit,
    onReplace: () -> Unit,
    onAdd: (PickMode) -> Unit,
    onInfo: () -> Unit,
    onMarkAll: () -> Unit,
) {
    val c = HqTheme.colors
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    @Composable
    fun Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = c.muted, onClick: () -> Unit) {
        Icon(
            icon, contentDescription = label, tint = tint,
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        )
    }
    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Tool(Icons.Rounded.History, "Exercise history", onClick = onHistory)
        Tool(Icons.Rounded.EditNote, if (hasNote) "Edit note" else "Add note", tint = if (hasNote) c.primary else c.muted, onClick = onNote)
        Tool(Icons.Rounded.SwapHoriz, "Replace exercise", onClick = onReplace)
        Box {
            Tool(Icons.Rounded.Add, "Add exercise", onClick = { addMenu = true })
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                listOf("Add exercise before" to PickMode.BEFORE, "Add exercise after" to PickMode.AFTER, "Add superset" to PickMode.SUPERSET).forEach { (label, mode) ->
                    DropdownMenuItem(text = { HqText(label, 15.sp) }, onClick = { addMenu = false; onAdd(mode) })
                }
            }
        }
        Box {
            Tool(Icons.Rounded.MoreVert, "More options", onClick = { moreMenu = true })
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(text = { HqText("How to do it", 15.sp) }, onClick = { moreMenu = false; onInfo() })
                DropdownMenuItem(text = { HqText("Mark all complete", 15.sp) }, onClick = { moreMenu = false; onMarkAll() })
            }
        }
    }
}
