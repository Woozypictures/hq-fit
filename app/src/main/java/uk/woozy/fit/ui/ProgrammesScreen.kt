package uk.woozy.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.ScheduledWorkoutEntity
import uk.woozy.fit.data.db.WorkoutTemplateEntity
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.model.DayRule
import uk.woozy.fit.model.Scheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Pick the weekdays for each workout, how many weeks to plan, and edit any workout's exercises. */
@Composable
fun ProgrammesScreen(db: HqFitDatabase, onBack: () -> Unit, onEdit: (Long) -> Unit, onNewWorkout: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val templates by produceState<List<WorkoutTemplateEntity>>(emptyList()) { value = db.templates().all() }
    val days = remember { mutableStateMapOf<Long, Set<DayOfWeek>>() }
    val changed = remember { mutableStateMapOf<Long, Boolean>() }
    var weeks by remember { mutableStateOf(10) }
    var message by remember { mutableStateOf<String?>(null) }

    // Current pattern: the weekdays each workout is already planned on over the next eight weeks.
    produceState(Unit, templates) {
        val upcoming = db.schedule().between(today.toString(), today.plusWeeks(8).toString()).filter { it.status == "planned" }
        templates.forEach { t ->
            if (days[t.id] == null) {
                days[t.id] = upcoming.filter { it.templateId == t.id }.map { LocalDate.parse(it.date).dayOfWeek }.toSet()
            }
        }
        value = Unit
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Programmes", onBack) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HqText("Choose the days each workout is on. Applying replaces its upcoming planned days; finished days stay as they are.", 13.sp, color = c.muted)
            templates.forEach { t ->
                HqCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HqText(t.name, 17.sp, FontWeight.SemiBold)
                        if (t.focus.isNotBlank()) HqText(t.focus, 13.sp, color = c.muted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEach { d ->
                                val on = days[t.id].orEmpty().contains(d)
                                Box(
                                    Modifier.weight(1f).height(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (on) c.primary.copy(alpha = 0.30f) else Color.Transparent)
                                        .border(1.dp, if (on) c.primary else c.muted.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            val now = days[t.id].orEmpty()
                                            days[t.id] = if (on) now - d else now + d
                                            changed[t.id] = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { HqText(d.getDisplayName(TextStyle.NARROW, Locale.UK), 13.sp, if (on) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                        HqPillButton("Edit exercises", onClick = { onEdit(t.id) }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
                    }
                }
            }
            SectionLabel("Weeks to plan")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 8, 10, 12, 26).forEach { w ->
                    FilterChip(selected = weeks == w, onClick = { weeks = w }, label = { HqText("$w", 13.sp) })
                }
            }
            ListRow(Icons.Rounded.FitnessCenter, "New workout", "Build your own, then pick its days here", onClick = onNewWorkout)
            message?.let { HqText(it, 13.sp, color = c.muted) }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(16.dp)) {
            HqPillButton(
                "Apply from today",
                onClick = {
                    scope.launch {
                        val touched = templates.filter { changed[it.id] == true }
                        if (touched.isEmpty()) { message = "Nothing changed yet."; return@launch }
                        db.schedule().deletePlanned(today.toString(), touched.map { it.id })
                        var added = 0
                        touched.forEach { t ->
                            val rules = days[t.id].orEmpty().map { DayRule(it, t.name) }
                            val rows = Scheduler.generate(today, weeks, rules).map {
                                ScheduledWorkoutEntity(programmeId = null, templateId = t.id, templateName = t.name, date = it.date.toString(), originalDate = it.date.toString())
                            }
                            db.schedule().insertAll(rows)
                            added += rows.size
                        }
                        changed.clear()
                        message = "Planned $added workouts over $weeks weeks."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
