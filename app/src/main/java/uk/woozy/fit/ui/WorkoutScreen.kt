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
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.ui.input.pointer.pointerInput
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

/** A short crisp tick (Android's CONFIRM where available), for finishing an exercise or a section. */
internal fun tick(v: android.view.View) {
    v.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
}

private data class Editing(val setId: Long, val weight: Boolean, val buffer: String, val fresh: Boolean)

/**
 * The full-screen workout. Every change is written to the database as it happens, so closing the app or losing
 * the process leaves the session exactly as it was; Today shows "Resume workout".
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    var confirmLeave by remember { mutableStateOf(false) }

    val session by produceState<uk.woozy.fit.data.db.WorkoutSessionEntity?>(null, sessionId) { value = db.sessions().get(sessionId) }
    val setsFlow = remember(sessionId) { db.sessions().observeSets(sessionId) }
    val sets by setsFlow.collectAsState(initial = emptyList())
    // Last session's actual weight and reps for each set: shown in the Previous column and as ghost text.
    val prevVals by produceState(emptyMap<Pair<Long, Int>, Pair<Double?, Int?>>(), sessionId, sets.size) {
        value = sets.map { it.exerciseId to it.setIndex }.distinct().mapNotNull { (e, i) ->
            db.sessions().previous(e, i, sessionId)?.let { p -> (e to i) to (p.weightKg to p.reps) }
        }.toMap()
    }
    val previous = prevVals.mapValues { (_, v) -> "${NumberEntry.format(v.first ?: 0.0)} x ${v.second ?: 0}" }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val context = LocalContext.current
    val weightStep = remember { WorkoutPrefs.weightStep(context) }
    val stepKeys = remember { WorkoutPrefs.stepKeys(context) }
    val barKg = remember { WorkoutPrefs.barKg(context) }
    // Equipment per exercise, for plates per side on barbell lifts.
    val equipment by produceState(emptyMap<Long, String>(), sets.map { it.exerciseId }.toSet()) {
        value = sets.map { it.exerciseId }.distinct().associateWith { db.sessions().exercise(it)?.equipment.orEmpty() }
    }
    var locked by remember { mutableStateOf(false) }
    // 3-2-1 before the first set: the section waiting to open and the number showing.
    var countdown by remember { mutableStateOf(0) }
    var countdownSec by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
    var restSheet by remember { mutableStateOf(false) }
    // Next targets (progression), refreshed when an exercise's settings change.
    var prefsTick by remember { mutableStateOf(0) }
    var settingsFor by remember { mutableStateOf<SetLogEntity?>(null) }
    val targets by produceState(emptyMap<Long, uk.woozy.fit.model.Progression.Target?>(), sets.map { it.exerciseId }.toSet(), prefsTick) {
        value = sets.filter { it.durationSec == null }.groupBy { it.exerciseId }.mapValues { (id, rows) ->
            val f = rows.first()
            uk.woozy.fit.data.db.Coach.next(db, id, f.targetRepsMin, f.targetRepsMax, weightStep)
        }
    }
    var restEnd by remember { mutableLongStateOf(RestTimer.endMs(context).takeIf { it > System.currentTimeMillis() } ?: 0L) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by remember { mutableStateOf(false) }
    // Once, on the first workout: ask Android to let the rest timer and sync run on time with the screen locked.
    var batteryAsk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        val seen = context.getSharedPreferences("battery", android.content.Context.MODE_PRIVATE).getBoolean("asked", false)
        if (!seen && pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) batteryAsk = true
    }
    if (batteryAsk) {
        val done = { batteryAsk = false; context.getSharedPreferences("battery", android.content.Context.MODE_PRIVATE).edit().putBoolean("asked", true).apply() }
        SheetDialog(
            onDismissRequest = done,
            title = "Keep the rest timer on time",
            message = "Let Woozy Fit run without battery limits, so the rest alarm and sync are not delayed when the screen is locked between sets.",
            confirm = "Allow",
            onConfirm = {
                done()
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + context.packageName)))
                }
            },
            dismiss = "Not now",
        )
    }
    var picking by remember { mutableStateOf<Pair<Int, PickMode>?>(null) }
    val restLeft = (restEnd - now).coerceAtLeast(0)
    LaunchedEffect(restEnd) {
        if (restEnd > 0) {
            delay((restEnd - System.currentTimeMillis()).coerceAtLeast(0))
            if (restEnd > 0) uk.woozy.fit.designsystem.restOverPulse(context)
        }
    }

    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var lastDeleted by remember { mutableStateOf<SetLogEntity?>(null) }
    val isDone = session?.status == "done"
    BackHandler { if (locked) Unit else if (isDone) onLeave() else confirmLeave = true }
    var pendingDelete by remember { mutableStateOf<SetLogEntity?>(null) }
    // The undo pill shows for a few seconds after a set is deleted, then goes.
    LaunchedEffect(lastDeleted) { if (lastDeleted != null) { delay(4000); lastDeleted = null } }

    fun toggle(set: SetLogEntity) {
        scope.launch {
            if (set.done) {
                db.sessions().updateDone(set.id, false, null)
            } else {
                if (set.durationSec == null && set.reps == null) {
                    // A tick accepts the ghost: last time's reps (and weight when none is set), else the plan.
                    val prev = prevVals[set.exerciseId to set.setIndex]
                    db.sessions().updateValues(set.id, set.weightKg ?: prev?.first, prev?.second ?: set.targetRepsMax ?: set.targetRepsMin)
                }
                db.sessions().updateDone(set.id, true, System.currentTimeMillis())
                if (set.restSec > 0 && WorkoutPrefs.autoRest(context)) {
                    restEnd = System.currentTimeMillis() + set.restSec * 1000L
                    if (Build.VERSION.SDK_INT >= 33 && !asked) {
                        asked = true
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Tell the notification what comes next: the next unticked set (this exercise first), its weight and reps.
                    val order = sets.sortedWith(compareBy({ it.position }, { it.setIndex }))
                    val nx = order.firstOrNull { it.id != set.id && !it.done && it.position == set.position }
                        ?: order.firstOrNull { it.id != set.id && !it.done && it.position > set.position }
                    val nextLine = nx?.let { n ->
                        val of = sets.count { it.position == n.position }
                        val load = if (n.durationSec != null) formatDuration(n.durationSec!!)
                            else listOfNotNull((n.weightKg ?: prevVals[n.exerciseId to n.setIndex]?.first ?: set.weightKg)?.let { "${NumberEntry.format(it)} kg" },
                                (n.reps ?: prevVals[n.exerciseId to n.setIndex]?.second ?: n.targetRepsMax)?.let { "$it reps" }).joinToString(" x ")
                        "Set ${n.setIndex} of $of" + (if (load.isNotEmpty()) " \u00b7 $load" else "")
                    } ?: "Last set done. Finish when ready."
                    val ticked = sets.count { it.done } + 1
                    RestTimer.start(context, restEnd, nx?.exerciseName ?: set.exerciseName, next = nextLine,
                        progress = "${session?.name ?: "Workout"} \u00b7 $ticked of ${sets.size} sets")
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
                if (e.weight) {
                    editing = Editing(set.id, false, set.reps?.toString().orEmpty(), true)
                } else {
                    // Auto-advance (Everfit/Strong style): Done on reps ticks the set, starts rest, carries the weight
                    // forward and opens the next unticked set in this exercise on its weight.
                    val group = sets.filter { it.position == set.position }.sortedBy { it.setIndex }
                    val next = group.firstOrNull { it.setIndex > set.setIndex && !it.done }
                    if (!set.done) toggle(set)
                    if (next != null) {
                        val w = next.weightKg ?: set.weightKg
                        if (next.weightKg == null && w != null) scope.launch { db.sessions().updateValues(next.id, w, next.reps) }
                        editing = Editing(next.id, true, w?.let { NumberEntry.format(it) }.orEmpty(), true)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else editing = null
                }
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
    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000); countdown -= 1 }
        else countdownSec?.let { sec -> tick(view); phases[sec] = SectionPhase.ACTIVE; countdownSec = null }
    }
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
        val barbell = equipment[first.exerciseId].orEmpty().contains("barbell", true) || first.exerciseName.contains("barbell", true)
        val loadSet = group.firstOrNull { !it.done && it.setType != "warmup" } ?: group.lastOrNull { it.setType != "warmup" }
        val plates = if (!barbell) null else loadSet?.weightKg?.let { w -> uk.woozy.fit.model.Plates.perSide(w, barKg)?.let { "Per side: ${uk.woozy.fit.model.Plates.label(it)} kg" } }
        val warm: (() -> Unit)? = if (isDone || first.durationSec != null || group.any { it.setType == "warmup" }) null else ({
            scope.launch {
                val n = Sessions.addWarmups(db, sessionId, first.position, weightStep, if (barbell) barKg else null)
                toast = if (n > 0) "$n warm-up sets added" else "Set a working weight first"
            }
        })
        val target = targets[first.exerciseId]
        val nextLine = target?.let { "Today ${uk.woozy.fit.model.Progression.label(it)} \u00b7 ${it.why}" }
        val open = group.filter { !it.done && it.setType != "warmup" }
        val useTarget: (() -> Unit)? = if (target == null || isDone || open.none { it.weightKg != target.weightKg || it.reps != target.reps }) null else ({
            scope.launch {
                open.forEach { db.sessions().updateValues(it.id, target.weightKg, target.reps) }
                toast = "Target filled in"
            }
        })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExerciseHero(first.exerciseName, onClick = { infoFor = first.exerciseName })
            ExerciseCard(
                group = group,
                previous = previous,
                prevVals = prevVals,
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
                onDeleteLast = { if (group.size > 1) { val gone = group.last(); lastDeleted = gone; scope.launch { db.sessions().deleteSet(gone.id) } } },
                onPick = { mode -> picking = first.position to mode },
                onDeleteSet = { set -> pendingDelete = set },
                onSetType = { set, t -> scope.launch { db.sessions().updateType(set.id, t) } },
                onSetRpe = { set, r -> scope.launch { db.sessions().updateRpe(set.id, r) } },
                onInfo = { infoFor = first.exerciseName },
                onHistory = { onHistory(first.exerciseId) },
                onNote = { noteFor = first.position to first.note },
                onMarkAll = { tick(view); scope.launch { markAllDone(db, group) } },
                plates = plates,
                onWarmup = warm,
                nextLine = nextLine,
                onUseTarget = useTarget,
                onSettings = { settingsFor = first },
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
                        val working = sets.filter { it.setType != "warmup" }
                        if (working.isNotEmpty()) {
                            val doneN = working.count { it.done }
                            val vol = working.filter { it.done && it.durationSec == null }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
                            Spacer(Modifier.height(6.dp))
                            HqText("$doneN of ${working.size} sets" + (if (vol > 0) " \u00b7 ${formatWhole(vol)} kg" else ""), 12.sp, color = c.onGradient.copy(alpha = 0.85f))
                            uk.woozy.fit.designsystem.HqProgressBar(doneN / working.size.toFloat(), c.onGradient, Modifier.padding(top = 6.dp).width(160.dp), height = 3.dp)
                        }
                    }
                    Box {
                        Icon(
                            Icons.Rounded.MoreVert, contentDescription = "Workout options", tint = c.onGradient,
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }.padding(8.dp),
                        )
                        uk.woozy.fit.designsystem.GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            uk.woozy.fit.designsystem.GlassDropdownItem(
                                text = { HqText(if (isDone) "Save and close" else "Finish workout now", 15.sp) },
                                onClick = {
                                    menu = false
                                    if (isDone) onLeave() else if (notTicked > 0) confirmFinish = true
                                    else scope.launch { RestTimer.cancel(context); Sessions.finish(db, sessionId, System.currentTimeMillis()); onFinished() }
                                },
                            )
                            if (!isDone) uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("Lock screen", 15.sp) }, onClick = { menu = false; locked = true })
                            if (!isDone) uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("Discard workout", 15.sp) }, onClick = { menu = false; confirmDiscard = true })
                        }
                    }
                }
                if (restLeft > 0 && !c.flat) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        HqText("Rest ${clock(restLeft + 999)}", if (c.flat) 24.sp else 15.sp, FontWeight.Bold, c.onGradient, Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Change rest") { restSheet = true }, letterSpacing = if (c.flat) (-0.5).sp else androidx.compose.ui.unit.TextUnit.Unspecified)
                        HqText(
                            "Skip", 14.sp, FontWeight.SemiBold, c.onGradient,
                            Modifier.clip(CircleShape).clickable(role = Role.Button) { restEnd = 0; RestTimer.cancel(context) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
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
                    SectionPhase.INTRO -> SectionIntro(title, count, pages.flatten(), onStart = {
                        if (!isDone && sets.none { it.done } && WorkoutPrefs.countdown(context)) { countdownSec = sec; countdown = 3 }
                        else phases[sec] = SectionPhase.ACTIVE
                    })
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
                                    if (allDone && !wasDone) {
                                        tick(view)
                                        delay(500)
                                        if (i < pages.lastIndex) {
                                            toast = "Next: " + pages[i + 1].first().first().exerciseName
                                            inner.animateScrollToPage(i + 1)
                                        }
                                        else {
                                            // Last exercise of the section done: complete it and glide on to the next section, already started.
                                            phases[sec] = SectionPhase.COMPLETE
                                            if (page < sectionKeys.lastIndex) {
                                                delay(1200)
                                                pager.animateScrollToPage(page + 1)
                                                sectionKeys.getOrNull(page + 1)?.let { nx -> if (phases[nx] == SectionPhase.INTRO) phases[nx] = SectionPhase.ACTIVE }
                                            }
                                        }
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
                                        TextButton(onClick = { tick(view); scope.launch { pg.forEach { g -> markAllDone(db, g) } } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                            HqText("Mark all supersets", 14.sp, FontWeight.SemiBold, c.primary)
                                        }
                                    }
                                    if (i == pages.lastIndex) {
                                        HqPillButton(
                                            "Complete section",
                                            onClick = {
                                                tick(view)
                                                phases[sec] = SectionPhase.COMPLETE
                                                if (page < sectionKeys.lastIndex) scope.launch {
                                                    delay(1200); pager.animateScrollToPage(page + 1)
                                                    sectionKeys.getOrNull(page + 1)?.let { nx -> if (phases[nx] == SectionPhase.INTRO) phases[nx] = SectionPhase.ACTIVE }
                                                }
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
        infoFor?.let { n ->
            val f = sets.firstOrNull { it.exerciseName == n }
            ExerciseDetailSheet(
                db, n, onDismiss = { infoFor = null },
                note = f?.note, onEditNote = f?.let { s -> { noteFor = s.position to s.note } },
                repsMin = f?.targetRepsMin, repsMax = f?.targetRepsMax,
            )
        }
        noteFor?.let { (pos, current) ->
            var text by remember(pos) { mutableStateOf(current) }
            SheetDialog(
                onDismissRequest = { noteFor = null },
                title = "Exercise note",
                message = "Shows when you open the exercise's picture, and carries over to next time.",
                confirm = "Save",
                onConfirm = { scope.launch { db.sessions().updateNote(sessionId, pos, text.trim()) }; noteFor = null },
            ) { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { HqText("Seat height, grip, how it felt", 14.sp, color = c.muted) }, modifier = Modifier.fillMaxWidth()) }
        }

        if (editing != null) {
            val e = editing!!
            if (c.flat && restLeft > 0) {
                val gStyle = remember(c) { uk.woozy.fit.designsystem.glassStyle(c.dark, c.ink, c.muted) }
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                    uk.woozy.fit.designsystem.GlassRestPill(
                        leftMs = restLeft, totalMs = (restEnd - RestTimer.startMs(context)).coerceAtLeast(1),
                        backdrop = backdrop, style = gStyle, accent = c.primary, solid = true,
                        onAdd = { restEnd += 15_000; RestTimer.start(context, restEnd, "Rest", extend = true) },
                        onSkip = { restEnd = 0; RestTimer.cancel(context) },
                        onTapTime = { restSheet = true },
                    )
                }
            }
            NumberKeypad(onKey = ::key, stepLabel = if (e.weight) "${NumberEntry.format(weightStep)} kg" else "1 rep", showSteps = stepKeys)
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
        if (c.flat && restLeft > 0 && editing == null) {
            val gStyle = remember(c) { uk.woozy.fit.designsystem.glassStyle(c.dark, c.ink, c.muted) }
            uk.woozy.fit.designsystem.GlassRestPill(
                leftMs = restLeft, totalMs = (restEnd - RestTimer.startMs(context)).coerceAtLeast(1),
                backdrop = backdrop, style = gStyle, accent = c.primary,
                onAdd = { restEnd += 15_000; RestTimer.start(context, restEnd, "Rest", extend = true) },
                onSkip = { restEnd = 0; RestTimer.cancel(context) },
                onTapTime = { restSheet = true },
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars).padding(bottom = 16.dp),
            )
        }
        // Same pill as "Last refreshed": a few seconds, Undo only.
        var undoText by remember { mutableStateOf("Set deleted") }
        lastDeleted?.let { undoText = "Set ${it.setIndex} deleted" }
        val gStyleT = remember(c) { uk.woozy.fit.designsystem.glassStyle(c.dark, c.ink, c.muted) }
        uk.woozy.fit.designsystem.GlassToast(
            undoText, lastDeleted != null, backdrop, gStyleT,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                .padding(bottom = if (editing != null) 330.dp else if (restLeft > 0 && c.flat) 96.dp else 24.dp),
            action = "Undo",
            onAction = { lastDeleted?.let { gone -> scope.launch { db.sessions().restoreSet(gone) } }; lastDeleted = null },
        )
        var toastText by remember { mutableStateOf("") }
        toast?.let { toastText = it }
        uk.woozy.fit.designsystem.GlassToast(
            toastText, toast != null, backdrop, gStyleT,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 104.dp),
        )
        countdownSec?.takeIf { countdown > 0 }?.let { sec ->
            val firstName = sets.filter { it.section == sec }.minByOrNull { it.position }?.exerciseName.orEmpty()
            Box(
                Modifier.fillMaxSize().background(c.page).clickable(role = Role.Button, onClickLabel = "Start now") { countdown = 0 },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(32.dp)) {
                    HqText("GET READY", 13.sp, FontWeight.SemiBold, c.muted, letterSpacing = 2.sp)
                    HqText("$countdown", 120.sp, FontWeight.Bold, c.ink)
                    HqText(firstName, 20.sp, FontWeight.SemiBold, textAlign = TextAlign.Center)
                    HqText("Tap to start now", 13.sp, color = c.muted)
                }
            }
        }
        if (locked) {
            Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f))
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val white = androidx.compose.ui.graphics.Color.White
                    HqText(session?.let { clock((it.endedMs ?: now) - it.startedMs) } ?: "", 44.sp, FontWeight.Bold, white, letterSpacing = (-1).sp)
                    if (restLeft > 0) HqText("Rest ${clock(restLeft + 999)}", 18.sp, FontWeight.SemiBold, white.copy(alpha = 0.85f))
                    Spacer(Modifier.height(24.dp))
                    Box(
                        Modifier.clip(CircleShape).background(white.copy(alpha = 0.16f))
                            .combinedClickable(onClick = {}, onLongClick = { tick(view); locked = false }, onLongClickLabel = "Unlock")
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                    ) { HqText("Hold to unlock", 16.sp, FontWeight.SemiBold, white) }
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

    pendingDelete?.let { set ->
        SheetDialog(
            onDismissRequest = { pendingDelete = null },
            title = "Delete set ${set.setIndex}?",
            message = set.exerciseName,
            confirm = "Delete",
            onConfirm = {
                pendingDelete = null; lastDeleted = set
                scope.launch { db.sessions().deleteSet(set.id) }
            },
            dismiss = "Keep",
        )
    }
    settingsFor?.let { f ->
        ExerciseSettingsSheet(db, f.exerciseId, f.exerciseName, f.targetRepsMin, f.targetRepsMax) { settingsFor = null; prefsTick++ }
    }
    if (restSheet) {
        var pick by remember { mutableStateOf((((restLeft + 999) / 1000 / 5) * 5).toDouble().coerceAtLeast(30.0)) }
        ModalBottomSheet(onDismissRequest = { restSheet = false }, containerColor = c.card) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HqText("Rest timer", 15.sp, FontWeight.SemiBold, c.muted)
                HqText(clock(pick.toLong() * 1000), 48.sp, FontWeight.Bold, letterSpacing = (-1).sp)
                RulerPicker(pick, { pick = it }, 0.0, 600.0, 5.0, 6, { clock(it.toLong() * 1000) }, description = "Rest time")
                HqText("Drag the ruler to change the time left.", 13.sp, color = c.muted)
                HqPillButton("Set", onClick = {
                    if (pick <= 0.0) { restEnd = 0; RestTimer.cancel(context) }
                    else { restEnd = System.currentTimeMillis() + pick.toLong() * 1000; RestTimer.start(context, restEnd, "Rest", extend = true) }
                    restSheet = false
                }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { restEnd = 0; RestTimer.cancel(context); restSheet = false }) { HqText("Skip rest", 15.sp, FontWeight.SemiBold, c.muted) }
            }
        }
    }
    if (confirmLeave) {
        SheetDialog(
            onDismissRequest = { confirmLeave = false },
            title = "Leave the workout?",
            message = "It keeps running. Resume it from Today.",
            confirm = "Leave",
            onConfirm = { confirmLeave = false; onLeave() },
            dismiss = "Stay",
        )
    }
    if (confirmFinish) {
        SheetDialog(
            onDismissRequest = { confirmFinish = false; scope.launch { pager.animateScrollToPage((finishIndex - 1).coerceAtLeast(0)) } },
            title = "Finish workout?",
            message = "$notTicked sets are not ticked. They will not count.",
            confirm = "Finish",
            onConfirm = {
                confirmFinish = false
                scope.launch { RestTimer.cancel(context); Sessions.finish(db, sessionId, System.currentTimeMillis()); onFinished() }
            },
            dismiss = "Keep going",
        )
    }
    if (confirmDiscard) {
        SheetDialog(
            onDismissRequest = { confirmDiscard = false },
            title = "Discard this workout?",
            message = "Everything logged in it is deleted. The day stays on your calendar.",
            confirm = "Discard",
            onConfirm = {
                confirmDiscard = false
                scope.launch { RestTimer.cancel(context); Sessions.discard(db, sessionId); onDiscarded() }
            },
            danger = true,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCard(
    group: List<SetLogEntity>,
    previous: Map<Pair<Long, Int>, String>,
    prevVals: Map<Pair<Long, Int>, Pair<Double?, Int?>> = emptyMap(),
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
    plates: String? = null,
    onWarmup: (() -> Unit)? = null,
    nextLine: String? = null,
    onUseTarget: (() -> Unit)? = null,
    onSettings: () -> Unit = {},
) {
    val c = HqTheme.colors
    val first = group.first()
    HqCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "Show how to do it", onClick = onInfo), verticalAlignment = Alignment.CenterVertically) {
                    if (showThumb) ExerciseThumb(first.exerciseName, 44.dp, Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    HqText(first.exerciseName, 16.sp, FontWeight.SemiBold, maxLines = 2)
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
            }
            if (nextLine != null) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    HqText(nextLine, 13.sp, FontWeight.Medium, c.ink, Modifier.weight(1f), maxLines = 2)
                    if (onUseTarget != null) TextButton(onClick = onUseTarget) { HqText("Use", 14.sp, FontWeight.SemiBold, c.primary) }
                }
            }
            ExerciseIconRow(
                hasNote = first.note.isNotBlank(),
                onSettings = onSettings,
                onHistory = onHistory, onNote = onNote,
                onReplace = { onPick(PickMode.REPLACE) },
                onAdd = onPick, onInfo = onInfo, onMarkAll = onMarkAll,
                onAddSet = onAddSet, onRemoveSet = if (group.size > 1 && first.durationSec == null) onDeleteLast else null,
            )
            if (first.durationSec != null) {
                group.forEach { set -> TimedRow(set, onToggle) }
            } else {
                SetTableHeader()
                val currentId = group.firstOrNull { !it.done }?.id
                group.forEachIndexed { idx, set ->
                    androidx.compose.runtime.key(set.id) {
                        val e = editing?.takeIf { it.setId == set.id }
                        var setMenu by remember { mutableStateOf(false) }
                        val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                            if (v == SwipeToDismissBoxValue.EndToStart && group.size > 1) onDeleteSet(set)
                                false // the card springs back; a dialog asks before anything is deleted
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
                                    weightGhost = prevVals[set.exerciseId to set.setIndex]?.first?.let { NumberEntry.format(it) }.orEmpty(),
                                    repsGhost = (prevVals[set.exerciseId to set.setIndex]?.second ?: set.targetRepsMax ?: set.targetRepsMin)?.toString().orEmpty(),
                                    current = set.id == currentId,
                                    divider = idx > 0,
                                )
                                uk.woozy.fit.designsystem.GlassDropdownMenu(expanded = setMenu, onDismissRequest = { setMenu = false }) {
                                    listOf("normal" to "Normal set", "warmup" to "Warm-up set", "drop" to "Drop set", "failure" to "Failure set").forEach { (k, label) ->
                                        uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText(label, 15.sp) }, onClick = { setMenu = false; onSetType(set, k) })
                                    }
                                    listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { r ->
                                        uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("RPE ${r.toInt()}", 15.sp) }, onClick = { setMenu = false; onSetRpe(set, r) })
                                    }
                                    uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("Clear RPE", 15.sp) }, onClick = { setMenu = false; onSetRpe(set, null) })
                                }
                            }
                        }
                    }
                }
            }
            if (plates != null || onWarmup != null) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    HqText(plates.orEmpty(), 13.sp, color = c.muted, modifier = Modifier.weight(1f))
                    if (onWarmup != null) TextButton(onClick = onWarmup) { HqText("Add warm-up", 14.sp, FontWeight.SemiBold, c.primary) }
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
                .size(44.dp)
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

/** After finishing (Everfit style): how it felt on a slider first, then time, volume, records and the sets. */
@Composable
fun WorkoutSummaryScreen(db: HqFitDatabase, sessionId: Long, onDone: () -> Unit, onEdit: () -> Unit = {}) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val summary by produceState<SessionSummary?>(null, sessionId) { value = Sessions.summary(db, sessionId, System.currentTimeMillis()) }
    val session by produceState<uk.woozy.fit.data.db.WorkoutSessionEntity?>(null, sessionId) { value = db.sessions().get(sessionId) }
    var feeling by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(session) { if (feeling == null) session?.feeling?.let { feeling = it.toFloat() } }
    val sets by produceState(emptyList<SetLogEntity>(), sessionId) { value = db.sessions().sets(sessionId).filter { it.done } }
    BackHandler(onBack = onDone)
    val zone = java.time.ZoneId.systemDefault()
    var sleep by remember { mutableStateOf<Int?>(null) }
    var energy by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(session) { session?.let { if (sleep == null) sleep = it.sleep; if (energy == null) energy = it.energy } }
    fun saveRatings() { scope.launch { db.sessions().setSleepEnergy(sessionId, sleep, energy) } }
    var naming by remember { mutableStateOf(false) }
    var savedAs by remember { mutableStateOf<String?>(null) }
    // Headline extras: weeks in a row with a workout, and volume against the last time you did this workout.
    val extra by produceState<Pair<Int, String?>>(0 to null, sessionId, summary) {
        val sm = summary ?: return@produceState
        val me = db.sessions().get(sessionId) ?: return@produceState
        val done = db.sessions().doneSince(me.startedMs - 400L * 86_400_000)
        val monday = { ms: Long -> java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().with(java.time.DayOfWeek.MONDAY) }
        val weeks = done.map { monday(it.startedMs) }.toSet() + monday(me.startedMs)
        var w = monday(me.startedMs); var n = 0
        while (w in weeks) { n++; w = w.minusWeeks(1) }
        val prev = done.filter { it.id != sessionId && it.name == me.name && it.startedMs < me.startedMs }.maxByOrNull { it.startedMs }
        val line = prev?.let { p ->
            Sessions.summary(db, p.id, System.currentTimeMillis())?.volumeKg?.takeIf { it > 0 && sm.volumeKg > 0 }?.let { pv ->
                val pct = Math.round((sm.volumeKg - pv) / pv * 100).toInt()
                "Volume ${if (pct >= 0) "+" else ""}$pct% vs last ${me.name}"
            }
        }
        value = n to line
    }
    val started = session?.startedMs?.let { java.time.Instant.ofEpochMilli(it).atZone(zone) }

    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)) {
                HqText(
                    (started?.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM", java.util.Locale.UK)) ?: "Workout complete").uppercase(),
                    12.sp, FontWeight.SemiBold, c.onGradient.copy(alpha = 0.8f), letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                SpacedTitle(summary?.name ?: "", color = c.onGradient, size = 22.sp)
            }
        }
        val s = summary
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s != null) {
                val (head, sub) = when {
                    s.records.isNotEmpty() -> "New personal record" to "You beat your best on ${s.records.size} exercise${if (s.records.size == 1) "" else "s"}."
                    extra.first >= 2 -> "Streak alive" to "${extra.first} weeks in a row with a workout."
                    else -> "Another one in the bank" to "Logged and counted. Turning up is what moves the numbers."
                }
                HqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HqText("WORKOUT LOGGED", 12.sp, FontWeight.SemiBold, c.muted, letterSpacing = 1.sp)
                        HqText(head, 24.sp, FontWeight.Bold, letterSpacing = (-0.4).sp)
                        HqText(sub, 14.sp, color = c.muted)
                        extra.second?.let { HqText(it, 14.sp, FontWeight.SemiBold, c.primary) }
                    }
                }
                HqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HqText("How did you show up?", 17.sp, FontWeight.SemiBold)
                        HqText("Optional. Over time it shows whether sleep and energy change your lifts.", 12.sp, color = c.muted)
                        RatingBars("Sleep", sleep, c.primary) { sleep = it; saveRatings() }
                        RatingBars("Energy", energy, c.warn) { energy = it; saveRatings() }
                    }
                }
                HqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        HqText("How was the workout?", 17.sp, FontWeight.SemiBold)
                        val v = feeling ?: 3f
                        val words = listOf("Very easy", "Easy", "Just right", "Hard", "All out")
                        Spacer(Modifier.height(6.dp))
                        HqText(if (feeling == null) "Slide to rate" else words[(v.toInt() - 1).coerceIn(0, 4)], 14.sp, FontWeight.Medium, if (feeling == null) c.muted else c.primary)
                        androidx.compose.material3.Slider(
                            value = v,
                            onValueChange = { nv ->
                                if (feeling?.toInt() != nv.toInt()) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                feeling = nv
                            },
                            onValueChangeFinished = { feeling?.let { f -> scope.launch { db.sessions().setFeeling(sessionId, f.toInt()) } } },
                            valueRange = 1f..5f, steps = 3,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.track,
                                activeTickColor = c.onPrimary.copy(alpha = 0.6f), inactiveTickColor = c.muted.copy(alpha = 0.5f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            HqText("Easy", 12.sp, color = c.muted); HqText("All out", 12.sp, color = c.muted)
                        }
                    }
                }
                HqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HqText("${s.durationMs / 60_000}", 34.sp, FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            HqText("Training min", 15.sp, color = c.muted)
                        }
                        if (started != null) {
                            val end = session?.endedMs?.let { java.time.Instant.ofEpochMilli(it).atZone(zone) }
                            val hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                            Spacer(Modifier.height(6.dp))
                            HqText(started.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.UK)) + "  \u00b7  " + started.format(hm) + (end?.let { " - " + it.format(hm) } ?: ""), 14.sp, color = c.muted)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Sets", "${s.setsDone}/${s.setsTotal}")
                            Stat("Exercises", "${sets.map { it.position }.distinct().size}")
                            Stat("Records", "${s.records.size}")
                        }
                    }
                }
                if (s.volumeKg > 0) HqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        HqText("You lifted ${formatWhole(s.volumeKg)} kg", 18.sp, FontWeight.Bold, c.primary)
                        HqText(liftedLike(s.volumeKg), 14.sp, color = c.muted)
                    }
                }
                if (s.records.isNotEmpty()) {
                    SectionLabel("New records")
                    HqCard(Modifier.fillMaxWidth()) {
                        s.records.forEach { r ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                HqText(r.exerciseName, 16.sp, FontWeight.SemiBold, maxLines = 2)
                                HqText(
                                    r.records.joinToString(" \u00b7 ") { it.label } +
                                        "  (${NumberEntry.format(r.best.weightKg)} kg x ${r.best.reps})",
                                    13.sp, color = c.primary,
                                )
                            }
                        }
                    }
                }
                if (sets.isNotEmpty()) {
                    SectionLabel("Workout details")
                    HqCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            sets.groupBy { it.position }.toSortedMap().values.forEach { group ->
                                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    HqText(group.first().exerciseName, 15.sp, FontWeight.SemiBold)
                                    HqText(
                                        group.joinToString("  \u00b7  ") { st ->
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
            }
        }
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            HqPillButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth(), style = PillStyle.Filled)
            HqPillButton("Edit sets", onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), style = PillStyle.Outline)
            HqText(
                savedAs?.let { "Saved as \u201c$it\u201d. Schedule it in Programmes." } ?: "Save as a workout",
                14.sp, FontWeight.SemiBold, if (savedAs == null) c.primary else c.muted,
                Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).clip(CircleShape)
                    .clickable(enabled = savedAs == null, role = Role.Button) { naming = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    if (naming) {
        var text by remember { mutableStateOf(session?.name.orEmpty()) }
        SheetDialog(
            onDismissRequest = { naming = false },
            title = "Save as a workout",
            message = "The exercises, sets, reps and weights you did, ready to plan again.",
            confirm = "Save",
            onConfirm = {
                naming = false
                val name = text.trim().ifEmpty { "Saved workout" }
                scope.launch { if (Sessions.saveAsTemplate(db, sessionId, name) != null) savedAs = name }
            },
        ) { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
    }
}

/** Five tappable bars with a word (GymMane's sleep and energy rating). Tapping the current value clears it. */
@Composable
private fun RatingBars(label: String, value: Int?, color: androidx.compose.ui.graphics.Color, onChange: (Int?) -> Unit) {
    val c = HqTheme.colors
    val words = listOf("Awful", "Poor", "Okay", "Good", "Great")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            HqText(label, 14.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
            HqText(value?.let { words[(it - 1).coerceIn(0, 4)] } ?: "Optional", 13.sp, if (value != null) FontWeight.SemiBold else FontWeight.Normal, if (value != null) color else c.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { i ->
                Box(
                    Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = "$label ${words[i - 1]}") { onChange(if (value == i) null else i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (value != null && i <= value) color else c.track))
                }
            }
        }
    }
}

/** A light comparison for the volume line, like Everfit's huskies. */
private fun liftedLike(kg: Double): String {
    val things = listOf(5000.0 to "elephant", 1500.0 to "family car", 450.0 to "grand piano", 230.0 to "vending machine", 20.0 to "Siberian husky")
    val (w, name) = things.firstOrNull { kg >= it.first * 2 } ?: things.last()
    val n = kotlin.math.round(kg / w).toInt().coerceAtLeast(1)
    return "That's about $n ${if (n == 1) name else if (name.endsWith("y") && !name.endsWith("ey")) name.dropLast(1) + "ies" else name + "s"}."
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
                            HqText(e.name, 16.sp, FontWeight.Medium, maxLines = 2)
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
                    HqText(first.exerciseName, 16.sp, FontWeight.SemiBold, maxLines = 2)
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
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onNote: () -> Unit,
    onReplace: () -> Unit,
    onAdd: (PickMode) -> Unit,
    onInfo: () -> Unit,
    onMarkAll: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (() -> Unit)?,
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
            uk.woozy.fit.designsystem.GlassDropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("Add set", 15.sp) }, onClick = { addMenu = false; onAddSet() })
                listOf("Add exercise before" to PickMode.BEFORE, "Add exercise after" to PickMode.AFTER, "Add superset" to PickMode.SUPERSET).forEach { (label, mode) ->
                    uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText(label, 15.sp) }, onClick = { addMenu = false; onAdd(mode) })
                }
            }
        }
        if (onRemoveSet != null) Tool(Icons.Rounded.Remove, "Remove last set", onClick = onRemoveSet)
        Box {
            Tool(Icons.Rounded.MoreVert, "More options", onClick = { moreMenu = true })
            uk.woozy.fit.designsystem.GlassDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("How to do it", 15.sp) }, onClick = { moreMenu = false; onInfo() })
                uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("Settings for this exercise", 15.sp) }, onClick = { moreMenu = false; onSettings() })
                uk.woozy.fit.designsystem.GlassDropdownItem(text = { HqText("Mark all complete", 15.sp) }, onClick = { moreMenu = false; onMarkAll() })
            }
        }
    }
}
