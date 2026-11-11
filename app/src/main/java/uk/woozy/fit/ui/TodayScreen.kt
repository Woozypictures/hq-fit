package uk.woozy.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
 * Today. The workout card reads the calendar; energy, water and steps stay honest empty values until
 * milestones 5 and 7.
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
    steps: Long? = null,
    burnedKcal: Double? = null,
    checkIn: List<CheckInRow>? = null,
    onToggleCheckIn: (String, Boolean) -> Unit = { _, _ -> },
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    name: String = "Mahesh",
    targets: Targets = Targets(),
) {
    val c = HqTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateLine = remember {
        "TODAY, " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.UK)).uppercase(Locale.UK)
    }
    val prefs = remember { context.getSharedPreferences("today", android.content.Context.MODE_PRIVATE) }
    val defaultOrder = listOf("workout", "energy", "water", "steps", "checkin")
    var order by remember {
        mutableStateOf(
            (prefs.getString("order", null)?.split(',')?.filter { it in defaultOrder }.orEmpty().let { saved -> saved + defaultOrder.filter { it !in saved } }),
        )
    }
    var reordering by remember { mutableStateOf(false) }
    fun move(key: String, delta: Int) {
        val i = order.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in order.indices) return
        order = order.toMutableList().also { it[i] = it[j].also { _ -> it[j] = key } }
        prefs.edit().putString("order", order.joinToString(",")).apply()
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HqText(dateLine, 12.sp, FontWeight.SemiBold, c.primary, letterSpacing = 0.08.em)
                HqText("Hello, $name!", 28.sp, FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c.primary)
                    .clickable(role = Role.Button, onClickLabel = "Settings", onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                HqText(name.take(1).uppercase(), 18.sp, FontWeight.Bold, c.onPrimary)
            }
        }

        order.forEach { key ->
            val visible = key != "checkin" || checkIn != null
            if (visible) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (reordering) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            androidx.compose.material3.TextButton(onClick = { move(key, -1) }) { HqText("Up", 13.sp, color = c.primary) }
                            androidx.compose.material3.TextButton(onClick = { move(key, 1) }) { HqText("Down", 13.sp, color = c.primary) }
                        }
                    }
                    when (key) {
                        "workout" -> GradientCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                HqText("TODAY'S WORKOUT", 12.sp, FontWeight.SemiBold, c.onGradient, letterSpacing = 0.1.em)
                                Spacer(Modifier.height(6.dp))
                                SpacedTitle(workoutTitle ?: "Rest day", color = c.onGradient, size = 20.sp)
                                Spacer(Modifier.height(4.dp))
                                HqText(workoutCaption, 13.sp, color = c.onGradient.copy(alpha = 0.8f))
                                if (workoutTitle != null) {
                                    Spacer(Modifier.height(16.dp))
                                    HqPillButton(buttonLabel, onClick = onStartWorkout, style = PillStyle.OnGradient)
                                }
                            }
                        }
                        "energy" -> HqCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionLabel("Energy")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    EnergyRing("Consumed", formatWhole(eaten), (eaten / targets.energyKcal).toFloat().coerceIn(0f, 1f), c.primary)
                                    EnergyRing("Burned", burnedKcal?.let { formatWhole(it) } ?: "0", 0f, c.protein)
                                    val left = targets.energyKcal - eaten
                                    EnergyRing(if (left < 0) "Over" else "Remaining", formatWhole(kotlin.math.abs(left)), (left / targets.energyKcal).toFloat().coerceIn(0f, 1f), c.fat)
                                }
                            }
                        }
                        "water" -> HqCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionLabel("Water")
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
                                    HqPillButton("+ ${targets.waterGlassMl} ml", onClick = onAddWater)
                                }
                            }
                        }
                        "steps" -> HqCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionLabel("Steps")
                                HqText("${formatWhole((steps ?: 0L).toDouble())} / ${formatWhole(targets.steps.toDouble())} steps", 18.sp, FontWeight.SemiBold)
                                HqProgressBar(fraction = ((steps ?: 0L).toFloat() / targets.steps).coerceIn(0f, 1f), color = c.primary)
                                if (steps == null) HqText("Allow Health Connect in Settings to see steps.", 12.sp, color = c.muted)
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
            }
        }
        androidx.compose.material3.TextButton(onClick = { reordering = !reordering }) {
            HqText(if (reordering) "Done reordering" else "Reorder cards", 13.sp, color = c.muted)
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
