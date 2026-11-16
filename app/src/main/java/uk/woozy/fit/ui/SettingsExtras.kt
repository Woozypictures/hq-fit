package uk.woozy.fit.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.share.HqSummary
import uk.woozy.fit.data.share.HqSummaryBuilder
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.model.NumberEntry

/** Name and height. The name greets you on Today; height gives BMI on the Progress screen's weight card. */
object ProfilePrefs {
    private fun p(c: Context) = c.getSharedPreferences("profile", Context.MODE_PRIVATE)
    fun name(c: Context): String = p(c).getString("name", null)?.takeIf { it.isNotBlank() } ?: "Mahesh"
    fun setName(c: Context, v: String) = p(c).edit().putString("name", v.trim()).apply()
    fun heightCm(c: Context): Double? = p(c).getFloat("height", 0f).toDouble().takeIf { it > 0 }
    fun setHeightCm(c: Context, v: Double?) = p(c).edit().putFloat("height", (v ?: 0.0).toFloat()).apply()
}

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar(title, onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = HqTheme.colors
    var name by remember { mutableStateOf(ProfilePrefs.name(context)) }
    var height by remember { mutableStateOf(ProfilePrefs.heightCm(context)?.let { NumberEntry.format(it) }.orEmpty()) }
    SettingsPage("Profile", onBack) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            AvatarCircle(name, 72.dp)
            HqText("Your picture comes from HQ, which takes it from the icon of your Notion DASHBOARD page. Change it there.", 13.sp, color = c.muted, modifier = Modifier.weight(1f))
        }
        androidx.compose.material3.OutlinedTextField(
            value = name, onValueChange = { name = it; ProfilePrefs.setName(context, it) },
            label = { HqText("Name", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        NumberField("Height (cm)", height) { height = it; ProfilePrefs.setHeightCm(context, it.toDoubleOrNull()) }
    }
}

@Composable
fun WorkoutSettingsScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = HqTheme.colors
    var step by remember { mutableStateOf(WorkoutPrefs.weightStep(context)) }
    var auto by remember { mutableStateOf(WorkoutPrefs.autoRest(context)) }
    var stepKeys by remember { mutableStateOf(WorkoutPrefs.stepKeys(context)) }
    var countdown by remember { mutableStateOf(WorkoutPrefs.countdown(context)) }
    var bar by remember { mutableStateOf(WorkoutPrefs.barKg(context)) }
    var order by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(db) { order = uk.woozy.fit.data.db.Rotation.on(db) }
    @Composable fun Group(label: String) = HqText(label.uppercase(), 12.sp, FontWeight.SemiBold, c.muted, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    @Composable fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                HqText(title, 16.sp, FontWeight.Medium)
                HqText(sub, 12.sp, color = c.muted)
            }
            uk.woozy.fit.designsystem.HapticSwitch(checked = on, onCheckedChange = set)
        }
    }
    SettingsPage("Workout", onBack) {
        // GymMane-style split: what happens between sets, then what happens while you train.
        Group("Your plan")
        HqCard(Modifier.fillMaxWidth()) {
            Column {
                Toggle("Keep my order", "Today shows the next workout in your plan's order on any day you train. A missed one comes first and the rest move on.", order) { order = it; scope.launch { uk.woozy.fit.data.db.Rotation.set(db, it) } }
            }
        }
        Group("Rest")
        HqCard(Modifier.fillMaxWidth()) {
            Column {
                Toggle("Start rest timer after each set", "Uses the rest time in the workout, or the exercise's own. Off means ticking a set starts nothing.", auto) { auto = it; WorkoutPrefs.setAutoRest(context, it) }
            }
        }
        HqText("Tap the rest time during a workout to change it on the dial. Each exercise can have its own rest in Settings for this exercise.", 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        Group("While you train")
        HqCard(Modifier.fillMaxWidth()) {
            Column {
                Toggle("Countdown before starting", "3, 2, 1 before the first set of a workout. Tap it to start straight away.", countdown) { countdown = it; WorkoutPrefs.setCountdown(context, it) }
                Toggle("Show - and + on the keypad", "Step the weight or reps up and down. Off keeps the keypad to numbers and Done.", stepKeys) { stepKeys = it; WorkoutPrefs.setStepKeys(context, it) }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HqText("Weight step", 16.sp, FontWeight.Medium)
                    HqText("What - and + add, and the step for next targets unless an exercise has its own.", 12.sp, color = c.muted)
                    SegmentedChoice(
                        WorkoutPrefs.steps.map { NumberEntry.format(it) }, WorkoutPrefs.steps.indexOf(step).coerceAtLeast(0),
                        onSelect = { step = WorkoutPrefs.steps[it]; WorkoutPrefs.setWeightStep(context, step) },
                    )
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HqText("Barbell weight", 16.sp, FontWeight.Medium)
                    HqText("For plates per side and the lightest warm-up set on barbell lifts.", 12.sp, color = c.muted)
                    val bars = listOf(10.0, 15.0, 20.0)
                    SegmentedChoice(
                        bars.map { "${NumberEntry.format(it)} kg" }, bars.indexOf(bar).coerceAtLeast(0),
                        onSelect = { bar = bars[it]; WorkoutPrefs.setBarKg(context, bar) },
                    )
                }
            }
        }
    }
}

/** What HQ will read from this app right now, so you can check the link without opening HQ. */
@Composable
fun HqConnectionScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val c = HqTheme.colors
    val s by produceState<HqSummary?>(null) { value = HqSummaryBuilder.build(db, System.currentTimeMillis()) }
    SettingsPage("HQ connection", onBack) {
        HqText(
            "HQ reads a read-only summary from this app: content://uk.woozy.fit.summary/today. Only an app signed with the same key can read it, and HQ still has to be updated to use it. This is what it would see now.",
            14.sp, color = c.muted,
        )
        val x = s
        if (x == null) uk.woozy.fit.designsystem.SkeletonBlock(c.track, lines = 6) else HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable fun line(k: String, v: String) = Row { HqText(k, 14.sp, color = c.muted, modifier = Modifier.weight(1f)); HqText(v, 14.sp, FontWeight.Medium) }
                line("Date", x.date)
                line("Energy", "${NumberEntry.format(x.kcal)} / ${x.kcalTarget?.let { NumberEntry.format(it) } ?: "-"} kcal")
                line("Protein", "${NumberEntry.format(x.proteinG)} / ${x.proteinTarget?.let { NumberEntry.format(it) } ?: "-"} g")
                line("Net carbs", "${NumberEntry.format(x.netCarbsG)} / ${x.netCarbsTarget?.let { NumberEntry.format(it) } ?: "-"} g")
                line("Fat", "${NumberEntry.format(x.fatG)} / ${x.fatTarget?.let { NumberEntry.format(it) } ?: "-"} g")
                line("Water", "${x.waterMl} ml")
                line("Weight", x.weightKg?.let { "${NumberEntry.format(it)} kg" } ?: "-")
                line("Workout today", (x.workoutToday ?: "None") + if (x.workoutTodayDone) " (done)" else "")
                line("This week", "${x.workoutsDoneThisWeek} of ${x.workoutsPlannedThisWeek} workouts")
                line("Check-in ${x.checkInDate}", listOf("mood" to x.checkInMood, "metrics" to x.checkInMetrics, "photo" to x.checkInPhoto).joinToString(", ") { (n, v) -> n + if (v) " yes" else " no" })
            }
        }
    }
}
