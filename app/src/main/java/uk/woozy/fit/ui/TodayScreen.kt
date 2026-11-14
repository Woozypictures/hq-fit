package uk.woozy.fit.ui

import uk.woozy.fit.designsystem.LocalGlassInset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.GradientCard
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqProgressBar
import uk.woozy.fit.designsystem.HqRing
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SpacedTitle
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.Targets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One tick box in the Sunday check-in. */
data class CheckInRow(val key: String, val label: String, val done: Boolean)

/**
 * Today: workout, energy and macros, water, steps and the Sunday check-in. Long-press a card to rearrange or hide
 * cards (as in HQ). A banner shows only when something needs fixing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    onStartWorkout: () -> Unit,
    workoutTitle: String?,
    workoutCaption: String,
    buttonLabel: String,
    eaten: Double,
    waterMl: Int,
    onAddWater: () -> Unit,
    onUndoWater: () -> Unit = {},
    steps: Long? = null,
    burnedKcal: Double? = null,
    checkIn: List<CheckInRow>? = null,
    onToggleCheckIn: (String, Boolean) -> Unit = { _, _ -> },
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    name: String = "Mahesh",
    targets: Targets = Targets(),
    proteinG: Double = 0.0,
    netCarbsG: Double = 0.0,
    fatG: Double = 0.0,
    problem: String? = null,
    onProblem: () -> Unit = {},
    onSteps: () -> Unit = {},
    onDiary: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    /** Changes when water was just added from the + menu: scroll to the Water card and flash it. */
    waterFocus: Long = 0L,
    onWaterShown: () -> Unit = {},
    /** Today's nutrient totals by key, and your set targets (value, kind), for the Nutrients card. */
    dayTotals: Map<String, Double> = emptyMap(),
    nutrientTargets: Map<String, Pair<Double, String>> = emptyMap(),
    onNutrients: () -> Unit = {},
    /** Planned workouts from the last week that were not done, shown as a banner like Everfit's. */
    missedCount: Int = 0,
    onMissed: () -> Unit = {},
    weighedToday: Boolean = false,
    onScale: () -> Unit = {},
) {
    val c = HqTheme.colors
    val dateLine = remember(today) {
        "TODAY, " + today.format(DateTimeFormatter.ofPattern("MMM d", Locale.UK)).uppercase(Locale.UK)
    }
    val defaultOrder = listOf("workout", "energy", "nutrients", "water", "steps", "tasks", "checkin")
    val names = mapOf("workout" to "Today's workout", "energy" to "Energy and macros", "nutrients" to "Highlighted nutrients", "water" to "Water", "steps" to "Steps", "tasks" to "Tasks", "checkin" to "Sunday check-in")
    val cards = rememberCardPrefs("today", defaultOrder)
    var editing by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(editing) { editing = false }

    val scroll = rememberScrollState()
    var colTop by remember { mutableStateOf(0f) }
    var waterTop by remember { mutableStateOf(-1f) }
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(waterFocus) {
        if (waterFocus > 0) {
            kotlinx.coroutines.delay(250)
            if (waterTop >= 0f) scroll.animateScrollTo((scroll.value + waterTop - colTop - 120f).toInt().coerceAtLeast(0))
            flash.animateTo(1f, androidx.compose.animation.core.tween(220))
            flash.animateTo(0f, androidx.compose.animation.core.tween(900))
            onWaterShown()
        }
    }
    uk.woozy.fit.designsystem.SmoothRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, container = c.card, accent = c.primary, modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { colTop = it.positionInWindow().y }
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + LocalGlassInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                // Same header as HQ: weekday, big date, greeting.
                val hour = java.time.LocalTime.now().hour
                val greet = if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
                // Three lines at most, same as HQ: date, greeting, weather.
                HqText(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)), 13.sp, FontWeight.Medium, c.muted)
                HqText("$greet, ${name.trim().substringBefore(' ')}.", if (c.flat) 28.sp else 24.sp, FontWeight.Bold, maxLines = 2, letterSpacing = if (c.flat) (-0.6).sp else androidx.compose.ui.unit.TextUnit.Unspecified)
                val weather = rememberWeatherLine(refreshing)
                val ctx = androidx.compose.ui.platform.LocalContext.current
                if (weather != null) HqText(weather, 12.sp, color = c.muted, maxLines = 1, modifier = Modifier.clickable { openWeather(ctx) }.padding(top = 2.dp))
            }
            AvatarCircle(
                initial = name, size = 44.dp,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Settings", onClick = onSettings),
            )
        }

        if (problem != null) {
            Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(c.primary.copy(alpha = 0.10f)).clickable(role = Role.Button, onClick = onProblem)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                HqText(problem, 13.sp, FontWeight.SemiBold)
                HqText("TAP TO FIX", 11.sp, color = c.muted, letterSpacing = 0.03.em)
            }
        }

        if (missedCount > 0) {
            Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Color(0xFFE5484D).copy(alpha = 0.14f)).clickable(role = Role.Button, onClick = onMissed)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                HqText("\uD83D\uDC49 You missed $missedCount workout${if (missedCount == 1) "" else "s"}", 14.sp, FontWeight.SemiBold)
                HqText("TAP TO CATCH UP OR MOVE ${if (missedCount == 1) "IT" else "THEM"}", 11.sp, color = c.muted, letterSpacing = 0.03.em)
            }
        }

        CardColumn(cards, names, available = { it != "checkin" || checkIn != null }, editing = editing, onEditing = { editing = it }) { key ->
                    when (key) {
                        "workout" -> GradientCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                HqText(if (workoutTitle == null) "REST DAY" else "TODAY'S WORKOUT", 12.sp, FontWeight.SemiBold, c.onGradient, letterSpacing = 0.04.em)
                                Spacer(Modifier.height(6.dp))
                                if (workoutTitle == null) HqText("Hoo-ray, it's your rest day \uD83C\uDF34", 18.sp, FontWeight.SemiBold, c.onGradient)
                                else SpacedTitle("\uD83C\uDFCB\uFE0F $workoutTitle", color = c.onGradient, size = 20.sp)
                                Spacer(Modifier.height(4.dp))
                                HqText(workoutCaption, 13.sp, color = c.onGradient.copy(alpha = 0.8f))
                                if (workoutTitle != null) {
                                    Spacer(Modifier.height(16.dp))
                                    HqPillButton(buttonLabel, onClick = onStartWorkout, style = PillStyle.OnGradient)
                                }
                            }
                        }
                        "energy" -> HqCard(Modifier.fillMaxWidth(), onClick = onDiary) {
                            val target = targets.energyKcal.toDouble().coerceAtLeast(1.0)
                            if (c.flat) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    SectionLabel("Calories")
                                    uk.woozy.fit.designsystem.CalorieHero(eaten, burnedKcal, target)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        uk.woozy.fit.designsystem.MacroColumn("Protein", proteinG, targets.proteinG.toDouble(), c.protein, Modifier.weight(1f))
                                        uk.woozy.fit.designsystem.MacroColumn("Net carbs", netCarbsG, targets.netCarbsG.toDouble(), c.carbs, Modifier.weight(1f))
                                        uk.woozy.fit.designsystem.MacroColumn("Fat", fatG, targets.fatG.toDouble(), c.fat, Modifier.weight(1f))
                                    }
                                }
                            } else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionLabel("Energy")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    EnergyRing("Consumed", formatWhole(eaten), (eaten / target).toFloat().coerceIn(0f, 1f), c.primary)
                                    // Like HQ: the burned ring is full once there is a reading, and empty without one.
                                    EnergyRing("Burned", burnedKcal?.takeIf { it > 0 }?.let { formatWhole(it) } ?: "-", if ((burnedKcal ?: 0.0) > 0) 1f else 0f, c.protein)
                                    val left = target - eaten
                                    EnergyRing(if (left < 0) "Over" else "Remaining", formatWhole(kotlin.math.abs(left)), (left / target).toFloat().coerceIn(0f, 1f), c.fat)
                                }
                                uk.woozy.fit.designsystem.MacroBar("Protein", proteinG, targets.proteinG.toDouble(), "g", c.protein)
                                uk.woozy.fit.designsystem.MacroBar("Net carbs", netCarbsG, targets.netCarbsG.toDouble(), "g", c.carbs)
                                uk.woozy.fit.designsystem.MacroBar("Fat", fatG, targets.fatG.toDouble(), "g", c.fat)
                            }
                        }
                        "nutrients" -> HqCard(Modifier.fillMaxWidth(), onClick = onNutrients) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionLabel("Highlighted nutrients")
                                uk.woozy.fit.model.Micros.highlighted.chunked(2).forEach { pair ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        pair.forEach { m ->
                                            val t = nutrientTargets[m.key]
                                            val (share, over) = uk.woozy.fit.model.Micros.status(m, dayTotals[m.key] ?: 0.0, t?.first, t?.second == "max")
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(Modifier.fillMaxWidth()) {
                                                    HqText(m.label, 13.sp, modifier = Modifier.weight(1f))
                                                    HqText("${formatWhole(share * 100)}%", 13.sp, FontWeight.Medium, if (over) androidx.compose.ui.graphics.Color(0xFFE5484D) else c.muted)
                                                }
                                                uk.woozy.fit.designsystem.HqProgressBar(share.toFloat().coerceIn(0f, 1f), if (over) androidx.compose.ui.graphics.Color(0xFFE5484D) else c.primary)
                                            }
                                        }
                                    }
                                }
                                HqText("Red means over the safe upper limit. Tap for every nutrient.", 11.sp, color = c.muted)
                            }
                        }
                        "water" -> HqCard(
                            Modifier.fillMaxWidth()
                                .onGloballyPositioned { waterTop = it.positionInWindow().y }
                                .border(2.dp, c.primary.copy(alpha = flash.value), androidx.compose.foundation.shape.RoundedCornerShape(if (c.flat) 22.dp else 18.dp)),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionLabel("Water \uD83D\uDCA7")
                                Row(
                                    Modifier.fillMaxWidth().semantics { contentDescription = "Water: $waterMl of ${targets.waterMl} millilitres" },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    repeat(targets.waterGlasses) { i ->
                                        val filled = waterMl >= (i + 1) * targets.waterGlassMl
                                        Icon(
                                            Icons.Rounded.WaterDrop, contentDescription = null, tint = if (filled) c.carbs else c.track,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    HqText("${formatWhole(waterMl.toDouble())} / ${formatWhole(targets.waterMl.toDouble())} ml", 14.sp, color = c.muted, modifier = Modifier.weight(1f))
                                    HqPillButton("Undo", onClick = onUndoWater, style = PillStyle.Outline)
                                    Spacer(Modifier.width(8.dp))
                                    HqPillButton("+ ${targets.waterGlassMl} ml", onClick = onAddWater)
                                }
                            }
                        }
                        "steps" -> HqCard(Modifier.fillMaxWidth(), onClick = onSteps) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionLabel("Steps \uD83D\uDC5F")
                                HqText("${steps?.let { formatWhole(it.toDouble()) } ?: "-"} / ${formatWhole(targets.steps.toDouble())} steps", 18.sp, FontWeight.SemiBold)
                                Segments(((steps ?: 0L).toFloat() / targets.steps.coerceAtLeast(1)).coerceIn(0f, 1f), c.primary)
                            }
                        }
                        "tasks" -> HqCard(Modifier.fillMaxWidth()) {
                            val rows = listOf(
                                Triple("Log your meals", eaten > 0, onDiary),
                                Triple("Drink your water", waterMl >= targets.waterMl, {}),
                                Triple("Weigh in", weighedToday, onScale),
                            )
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    SectionLabel("Tasks")
                                    Spacer(Modifier.weight(1f))
                                    HqText("${rows.count { it.second }} of ${rows.size} done", 13.sp, color = c.muted)
                                }
                                rows.forEach { (label, done, go) ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = go).padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        androidx.compose.material3.Checkbox(checked = done, onCheckedChange = null)
                                        HqText(label, 16.sp, color = if (done) c.muted else c.ink)
                                    }
                                }
                            }
                        }
                        "checkin" -> if (checkIn != null) HqCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SectionLabel("Sunday check-in")
                                checkIn.forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggleCheckIn(row.key, !row.done) }.padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        androidx.compose.material3.Checkbox(checked = row.done, onCheckedChange = null)
                                        HqText(row.label, 16.sp)
                                    }
                                }
                            }
                        }
                    }
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun EnergyRing(label: String, value: String, progress: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HqRing(progress = progress, color = color, diameter = 92.dp, stroke = 10.dp) {
            HqText(value, 16.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        HqText(label, 12.sp, color = HqTheme.colors.muted)
    }
}
