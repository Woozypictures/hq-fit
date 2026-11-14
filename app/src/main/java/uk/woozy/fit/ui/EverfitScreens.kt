package uk.woozy.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.ScheduledWorkoutEntity
import uk.woozy.fit.data.db.WorkoutSessionEntity
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// Everfit's training look: grey done circles, red ring for a missed day, blue date for today, cards beside each day.

private val Missed = Color(0xFFE5484D)

@Composable
private fun doneGrey(): Color = if (HqTheme.colors.dark) Color(0xFF6A6A6E) else Color(0xFF3A3A3C)

/** Light tinted card used for planned, missed and history entries. */
@Composable
private fun softCard(): Color = HqTheme.colors.primary.copy(alpha = if (HqTheme.colors.dark) 0.10f else 0.05f).compositeOver(HqTheme.colors.card)

private fun hasEmoji(s: String) = s.any { Character.isSurrogate(it) }

/** Workout names as Everfit shows them: capitals, wide spacing, an emoji either side when the name has none. */
private fun cardName(name: String): String = if (hasEmoji(name)) name else "🏋️$name🏋️"

@Composable
private fun CardTitle(name: String, color: Color) {
    HqText(cardName(name).uppercase(Locale.UK), 15.sp, FontWeight.Bold, color, letterSpacing = 0.18.em, maxLines = 1)
}

private fun durationText(ms: Long): String? {
    val m = ms / 60_000
    if (m <= 0) return null
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private fun sessionDate(s: WorkoutSessionEntity): LocalDate = Instant.ofEpochMilli(s.startedMs).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Everfit's week strip. Without [selected]: a grey tick on done days, a red ring on missed days, today in blue.
 * With [selected] (History): the selected day in a dark circle, a blue arrow over today, future days greyed.
 */
@Composable
fun EverfitWeek(
    monday: LocalDate,
    today: LocalDate,
    byDate: Map<String, List<ScheduledWorkoutEntity>> = emptyMap(),
    doneDates: Set<String> = emptySet(),
    selected: LocalDate? = null,
    onDay: (LocalDate) -> Unit = {},
) {
    val c = HqTheme.colors
    val grey = doneGrey()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { i ->
            val day = monday.plusDays(i.toLong())
            val isToday = day == today
            val planned = byDate[day.toString()].orEmpty()
            val isDone = planned.any { it.status == "done" } || day.toString() in doneDates
            val isMissed = !isDone && planned.any { it.status == "planned" } && day < today
            val isSel = selected != null && day == selected
            Column(
                Modifier.width(44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onDay(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selected != null) HqText(if (isToday) "▼" else " ", 10.sp, color = c.primary)
                HqText(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.UK), 12.sp,
                    if (isToday) FontWeight.Bold else FontWeight.Medium, if (isToday) c.primary else c.muted,
                )
                Box(
                    Modifier.size(38.dp).clip(CircleShape).then(
                        when {
                            isSel -> Modifier.background(grey)
                            selected == null && isDone -> Modifier.background(grey)
                            selected == null && isMissed -> Modifier.border(1.5.dp, Missed, CircleShape)
                            else -> Modifier
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == null && isDone) {
                        Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        HqText(
                            if (isSel) "%02d".format(day.dayOfMonth) else day.dayOfMonth.toString(), 16.sp,
                            if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                            when {
                                isSel -> Color.White
                                isToday -> c.primary
                                selected != null && day > today -> c.muted.copy(alpha = 0.6f)
                                else -> c.ink
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Everfit's one-line pep talk under the week strip. */
fun weekMessage(done: Int, planned: Int): String = when {
    done == 0 -> "No workouts yet this week, you've got this! 💪"
    planned in 1..done -> "All $done workouts done this week, great work! 🎉"
    else -> "You completed $done workout${if (done == 1) "" else "s"}, keep going! 💪"
}

@Composable
private fun TopBar(title: String?, onBack: () -> Unit, centred: Boolean, actions: @Composable () -> Unit = {}) {
    val c = HqTheme.colors
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
        Box(
            Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(30.dp)) }
        if (title != null && centred) HqText(title, 18.sp, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = HqTheme.colors.primary, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun DayLabel(day: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    val col = if (day == today) c.primary else c.muted
    Column(modifier.width(56.dp)) {
        HqText(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).uppercase(Locale.UK), 12.sp, FontWeight.SemiBold, col)
        HqText("%02d".format(day.dayOfMonth), 16.sp, FontWeight.Normal, col)
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(HqTheme.colors.hairline))

/** Completed card: blue gradient, name, "Completed · 1h 18m", a small dark tick on the right. */
@Composable
private fun DoneCard(name: String, duration: String?, onClick: (() -> Unit)?) {
    val c = HqTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(c.gradientStart, c.gradientEnd)))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(name, c.onGradient)
            HqText("Completed" + (duration?.let { " • $it" } ?: ""), 13.sp, color = c.onGradient.copy(alpha = 0.9f))
        }
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF2B2B2E)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

/** Planned or missed card: soft tint, name, "Missed · 14 exercises". */
@Composable
private fun PlanCard(w: ScheduledWorkoutEntity, today: LocalDate, exercises: Int?, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    val c = HqTheme.colors
    val missed = LocalDate.parse(w.date) < today
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(softCard())
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trailing == null) HqText("⠇", 18.sp, color = c.muted, modifier = Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(w.templateName, c.ink)
            if (trailing == null) Row {
                if (missed) HqText("Missed", 13.sp, color = Missed)
                val rest = listOfNotNull(
                    if (!missed && w.date != w.originalDate) "Moved from " + LocalDate.parse(w.originalDate).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) else null,
                    exercises?.takeIf { it > 0 }?.let { "$it exercises" },
                )
                if (rest.isNotEmpty()) HqText((if (missed) " • " else "") + rest.joinToString(" • "), 13.sp, color = c.muted)
            }
        }
        trailing?.invoke()
    }
}

/**
 * Everfit's Training page: the week strip, then each day with its card beside the date.
 * Top right: Rearrange (drag workouts to other days) and History.
 */
@Composable
fun TrainingScreen(
    calendar: List<ScheduledWorkoutEntity>,
    sessions: List<WorkoutSessionEntity>,
    exerciseCounts: Map<Long, Int>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onRearrange: () -> Unit,
    onHistory: () -> Unit,
) {
    val c = HqTheme.colors
    val today = rememberToday()
    val monday = remember(today) { today.with(DayOfWeek.MONDAY) }
    val byDate = calendar.groupBy { it.date }
    val byDay = sessions.groupBy { sessionDate(it).toString() }
    val doneDates = byDay.keys

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(null, onBack, centred = false) {
            TopIcon(Icons.Rounded.EventRepeat, "Rearrange", onRearrange)
            TopIcon(Icons.Rounded.History, "History", onHistory)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            HqText("Training", 30.sp, FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            HqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                    EverfitWeek(monday, today, byDate, doneDates, onDay = { d -> byDate[d.toString()]?.firstOrNull()?.let { onOpen(it.id) } })
                }
            }
            Spacer(Modifier.height(16.dp))
            (0..20).forEach { i ->
                val day = monday.plusDays(i.toLong())
                val entries = byDate[day.toString()].orEmpty()
                val daySessions = byDay[day.toString()].orEmpty()
                Hairline()
                Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    DayLabel(day, today)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val used = HashSet<Long>()
                        entries.forEach { w ->
                            val s = daySessions.firstOrNull { it.scheduledId == w.id } ?: daySessions.firstOrNull { it.id !in used && it.templateId == w.templateId }
                            if (w.status == "done" || s != null) {
                                s?.let { used += it.id }
                                DoneCard(w.templateName, s?.let { x -> x.endedMs?.let { durationText(it - x.startedMs) } }) { if (s != null) onOpenSession(s.id) else onOpen(w.id) }
                            } else {
                                PlanCard(w, today, exerciseCounts[w.templateId]) { onOpen(w.id) }
                            }
                        }
                        daySessions.filter { it.id !in used && entries.none { e -> e.status == "done" } }.forEach { s ->
                            DoneCard(s.name, s.endedMs?.let { durationText(it - s.startedMs) }) { onOpenSession(s.id) }
                        }
                    }
                }
            }
            Hairline()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Everfit's Rearrange page: every coming day, drag a workout by its handle onto another day. Dropping on a busy day swaps them. */
@Composable
fun RearrangeScreen(
    calendar: List<ScheduledWorkoutEntity>,
    onBack: () -> Unit,
    onMove: suspend (Long, String) -> Unit,
) {
    val c = HqTheme.colors
    val today = rememberToday()
    val days = remember(today) { (0..27).map { today.plusDays(it.toLong()) } }
    val planned = calendar.filter { it.status == "planned" && LocalDate.parse(it.date) >= today }
    val byDate = planned.groupBy { it.date }
    val rowH = 92.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("Rearrange", onBack, centred = true)
        Hairline()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), enabled = dragId == null)) {
            days.forEachIndexed { idx, day ->
                val here = byDate[day.toString()].orEmpty()
                val holdsDrag = here.any { it.id == dragId }
                Column(Modifier.zIndex(if (holdsDrag) 1f else 0f)) {
                    Row(
                        Modifier.fillMaxWidth().height(rowH).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DayLabel(day, today)
                        Box(Modifier.weight(1f)) {
                            here.firstOrNull()?.let { w ->
                                val dragging = dragId == w.id
                                Box(Modifier.graphicsLayer {
                                    if (dragging) { translationY = dragY; scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 18f }
                                }) {
                                    PlanCard(w, today, null, trailing = {
                                        Box(
                                            Modifier.size(40.dp).pointerInput(w.id, idx, byDate) {
                                                detectDragGestures(
                                                    onDragStart = { dragId = w.id; dragY = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                                    onDragCancel = { dragId = null; dragY = 0f },
                                                    onDragEnd = {
                                                        val target = (idx + (dragY / rowPx).roundToInt()).coerceIn(0, days.lastIndex)
                                                        val id = w.id
                                                        dragId = null; dragY = 0f
                                                        if (target != idx) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val to = days[target].toString()
                                                            val other = byDate[to]?.firstOrNull()
                                                            scope.launch {
                                                                onMove(id, to)
                                                                if (other != null) onMove(other.id, day.toString())
                                                            }
                                                        }
                                                    },
                                                ) { change, amount -> change.consume(); dragY += amount.y }
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to another day", tint = c.primary, modifier = Modifier.size(24.dp)) }
                                    }, onClick = null)
                                }
                            }
                            if (here.size > 1) HqText("+${here.size - 1} more", 11.sp, color = c.muted, modifier = Modifier.align(Alignment.BottomEnd))
                        }
                    }
                    Hairline()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Everfit's History: a week strip to jump about, then a timeline of every day back from today with each workout's card. */
@Composable
fun HistoryScreen(db: HqFitDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val c = HqTheme.colors
    val today = rememberToday()
    val sessions by produceState(emptyList<WorkoutSessionEntity>(), db) { value = db.sessions().doneSince(0L).sortedByDescending { it.startedMs } }
    val counts by produceState(emptyMap<Long, Int>(), sessions) {
        value = sessions.associate { s -> s.id to db.sessions().sets(s.id).map { it.exerciseId }.distinct().size }
    }
    val byDay = sessions.groupBy { sessionDate(it) }
    val earliest = (sessions.minOfOrNull { sessionDate(it) } ?: today).let { if (it > today.minusDays(28)) today.minusDays(28) else it }
    val days = remember(today, earliest) { generateSequence(today) { it.minusDays(1) }.takeWhile { it >= earliest }.toList() }
    val weeks = (ChronoUnit.WEEKS.between(earliest.with(DayOfWeek.MONDAY), today.with(DayOfWeek.MONDAY)).toInt() + 1).coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = weeks - 1) { weeks }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(today) }
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.UK)
    val labelFmt = DateTimeFormatter.ofPattern("MMM d", Locale.UK)
    val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("History", onBack, centred = true)
        HqCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            HorizontalPager(state = pager, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) { page ->
                val monday = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1 - page).toLong())
                EverfitWeek(monday, today, selected = selected, onDay = { d ->
                    if (d <= today) {
                        selected = d
                        val i = days.indexOf(d)
                        if (i >= 0) scope.launch { list.animateScrollToItem(i) }
                    }
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = list, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            itemsIndexed(days, key = { _, d -> d.toString() }) { i, d ->
                val here = byDay[d].orEmpty()
                val has = here.isNotEmpty()
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(
                        Modifier.width(24.dp).fillMaxHeight().drawBehind {
                            val x = size.width / 2
                            val dotY = 22.dp.toPx()
                            if (i > 0) drawLine(c.muted.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, dotY), 1.5f, pathEffect = dashed)
                            if (i < days.lastIndex) drawLine(c.muted.copy(alpha = 0.4f), Offset(x, dotY), Offset(x, size.height), 1.5f, pathEffect = dashed)
                            drawCircle(if (has) c.primary else c.muted.copy(alpha = 0.45f), radius = 4.5.dp.toPx(), center = Offset(x, dotY))
                        },
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp, top = 14.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HqText(
                            if (d == today) "TODAY" else d.format(labelFmt).uppercase(Locale.UK), 12.sp, FontWeight.Bold,
                            if (has) c.primary else c.muted,
                        )
                        here.forEach { s ->
                            val imported = s.scheduledId == uk.woozy.fit.data.imports.EverfitImport.MARKER
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(softCard())
                                    .clickable(role = Role.Button) { onOpen(s.id) }.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CardTitle(s.name, c.ink)
                                Hairline()
                                if (!imported && s.endedMs != null) {
                                    val start = Instant.ofEpochMilli(s.startedMs).atZone(ZoneId.systemDefault())
                                    val end = Instant.ofEpochMilli(s.endedMs!!).atZone(ZoneId.systemDefault())
                                    val mins = (s.endedMs!! - s.startedMs) / 60_000
                                    val len = if (mins >= 60) "${mins / 60}:${"%02d".format(mins % 60)} hr" else "$mins min"
                                    HistoryLine(Icons.Rounded.Schedule, "$len • ${start.format(timeFmt).uppercase(Locale.UK)} - ${end.format(timeFmt).uppercase(Locale.UK)}")
                                }
                                HistoryLine(Icons.Rounded.FitnessCenter, "${counts[s.id] ?: 0} exercises tracked")
                                HistoryLine(Icons.Rounded.StarBorder, "Rating: " + (s.feeling?.let { "$it/5" } ?: "N/A"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLine(icon: ImageVector, text: String) {
    val c = HqTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        HqText(text, 14.sp, color = c.ink)
    }
}
