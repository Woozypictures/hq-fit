package uk.woozy.fit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.Coach
import uk.woozy.fit.data.db.ExercisePrefEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.model.NumberEntry
import uk.woozy.fit.model.Progression

/**
 * "Settings for this exercise": how it progresses, its rep range and weight step, its own rest, deload,
 * assisted machines, automatic warm-ups and bar weight. Saved as you change them; the next target updates live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSettingsSheet(
    db: HqFitDatabase,
    exerciseId: Long,
    exerciseName: String,
    programmeRepsMin: Int?,
    programmeRepsMax: Int?,
    onDismiss: () -> Unit,
) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val globalStep = remember { WorkoutPrefs.weightStep(context) }
    val loaded by produceState<ExercisePrefEntity?>(null, exerciseId) { value = db.exercisePrefs().get(exerciseId) ?: ExercisePrefEntity(exerciseId) }
    var pref by remember(loaded) { mutableStateOf(loaded) }
    val history by produceState(emptyList<List<uk.woozy.fit.model.SetPoint>>(), exerciseId) { value = Coach.recent(db, exerciseId) }

    fun save(p: ExercisePrefEntity) { pref = p; scope.launch { db.exercisePrefs().upsert(p) } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        val p = pref ?: return@ModalBottomSheet
        val s = Coach.settings(p, programmeRepsMin, programmeRepsMax, globalStep)
        val next = Progression.next(s, history)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HqText("Settings for this exercise", 20.sp, FontWeight.SemiBold)
            HqText(exerciseName, 14.sp, color = c.muted)
            Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(vertical = 4.dp)) {
                HqText("NEXT TIME", 12.sp, FontWeight.SemiBold, c.muted, letterSpacing = 1.sp)
                HqText(next?.let { Progression.label(it) } ?: if (s.rule == Progression.Rule.MANUAL) "No suggestions (manual)" else "Log this exercise once to get a target", 22.sp, FontWeight.Bold)
                next?.let { HqText(it.why, 13.sp, color = c.primary) }
            }

            SectionLabel("Progression")
            val rules = listOf("auto" to "Auto", "double" to "Range", "linear" to "Linear", "manual" to "Manual")
            SegmentedChoice(rules.map { it.second }, rules.indexOfFirst { it.first == p.rule }.coerceAtLeast(0), onSelect = { save(p.copy(rule = rules[it].first)) })
            HqText(
                when (s.rule) {
                    Progression.Rule.DOUBLE -> "Range: every set at ${s.repsMax} reps adds ${NumberEntry.format(s.stepKg)} kg and drops back to ${s.repsMin}."
                    Progression.Rule.LINEAR -> "Linear: every set at ${s.repsMax} reps adds ${NumberEntry.format(s.stepKg)} kg."
                    Progression.Rule.MANUAL -> "Manual: you choose; no targets are suggested."
                } + if (p.rule == "auto") " (Auto picks this from the programme.)" else "",
                12.sp, color = c.muted,
            )

            SectionLabel("Rep range")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stepper("Lowest", s.repsMin) { v -> save(p.copy(repsMin = v.coerceIn(1, 50), repsMax = maxOf(v.coerceIn(1, 50), p.repsMax ?: s.repsMax))) }
                Stepper("Highest", s.repsMax) { v -> save(p.copy(repsMax = v.coerceIn(s.repsMin, 50), repsMin = p.repsMin ?: s.repsMin)) }
            }
            if (p.repsMin != null || p.repsMax != null) {
                HqText("Use the programme's", 13.sp, FontWeight.SemiBold, c.primary, Modifier.clickable(role = Role.Button) { save(p.copy(repsMin = null, repsMax = null)) }.padding(vertical = 4.dp))
            }

            SectionLabel("Weight step")
            val steps = listOf<Double?>(null, 1.0, 1.25, 2.0, 2.5, 5.0)
            SegmentedChoice(steps.map { it?.let { v -> NumberEntry.format(v) } ?: "App" }, steps.indexOf(p.stepKg).coerceAtLeast(0), onSelect = { save(p.copy(stepKg = steps[it])) })

            SectionLabel("Rest after each set")
            val rests = listOf<Int?>(null, 60, 90, 120, 180, 240)
            SegmentedChoice(rests.map { it?.let { r -> if (r % 60 == 0) "${r / 60}m" else "${r / 60}:${"%02d".format(r % 60)}" } ?: "Plan" }, rests.indexOf(p.restSec).coerceAtLeast(0), onSelect = { save(p.copy(restSec = rests[it])) })
            HqText("Plan keeps the rest in the workout plan. Applies from the next workout.", 12.sp, color = c.muted)

            SectionLabel("Go lighter after")
            val deloads = listOf(0, 2, 3, 4)
            SegmentedChoice(deloads.map { if (it == 0) "Never" else "$it misses" }, deloads.indexOf(p.deloadAfter).coerceAtLeast(0), onSelect = { save(p.copy(deloadAfter = deloads[it])) })

            SwitchRow("Assisted machine", "More help is lighter, so progress means less weight.", p.assisted) { save(p.copy(assisted = it)) }
            SwitchRow("Start with warm-up sets", "Adds three ramp-up sets when a workout starts.", p.warmups) { save(p.copy(warmups = it)) }

            SectionLabel("Bar weight (barbell lifts)")
            val bars = listOf<Double?>(null, 10.0, 15.0, 20.0)
            SegmentedChoice(bars.map { it?.let { b -> "${NumberEntry.format(b)} kg" } ?: "App" }, bars.indexOf(p.barKg).coerceAtLeast(0), onSelect = { save(p.copy(barKg = bars[it])) })
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    val c = HqTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HqText(label, 12.sp, color = c.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Remove, "Fewer", tint = c.ink, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onChange(value - 1) }.padding(8.dp))
            HqText("$value", 22.sp, FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
            Icon(Icons.Rounded.Add, "More", tint = c.ink, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onChange(value + 1) }.padding(8.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = HqTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HqText(title, 16.sp, FontWeight.Medium)
            HqText(sub, 12.sp, color = c.muted)
        }
        uk.woozy.fit.designsystem.HapticSwitch(checked = checked, onCheckedChange = onChange)
    }
}
