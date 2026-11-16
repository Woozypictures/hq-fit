package uk.woozy.fit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.woozy.fit.data.Muscles
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SegmentedChoice

/** Secondary muscles from the bundled catalogue, read once. */
object MuscleCatalog {
    @Volatile private var map: Map<String, List<String>>? = null
    fun get(context: android.content.Context): Map<String, List<String>> =
        map ?: synchronized(this) { map ?: runCatching { Muscles.secondaries(context.assets.open("exercises.tsv").bufferedReader()) }.getOrDefault(emptyMap()).also { map = it } }
}

private class MuscleData(val week: Muscles.Load, val month: Muscles.Load)

/**
 * Progress card: front and back body coloured by training. Week and 30 days colour by working sets against the
 * 10-a-week guide; Recovery colours what was worked in the last 72 hours (darkest = just trained).
 */
@Composable
fun MuscleCard(db: HqFitDatabase) {
    val c = HqTheme.colors
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val data by produceState<MuscleData?>(null, db) {
        value = withContext(Dispatchers.IO) {
            val sec = MuscleCatalog.get(context)
            val now = System.currentTimeMillis()
            MuscleData(Muscles.load(db, now - 7 * 86_400_000L, sec), Muscles.load(db, now - 30 * 86_400_000L, sec))
        }
    }
    HqCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedChoice(options = listOf("Week", "30 days", "Recovery"), selectedIndex = tab, onSelect = { tab = it })
            val d = data
            if (d == null) { Box(Modifier.fillMaxWidth().height(220.dp)); return@Column }
            val now = System.currentTimeMillis()
            val load = if (tab == 1) d.month else d.week
            val full = if (tab == 1) Muscles.WEEK_FULL * 30 / 7 else Muscles.WEEK_FULL
            val values: Map<String, Float> = if (tab == 2) {
                d.week.lastHit.mapValues { (_, at) -> (1f - (now - at) / (72f * 3_600_000f)).coerceIn(0f, 1f) }
            } else load.sets.mapValues { (_, v) -> (v / full).toFloat().coerceIn(0f, 1f) }
            val hot = if (tab == 2) c.warn else c.primary
            BodyMap(values, hot, Modifier.fillMaxWidth().aspectRatio(2.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                HqText("Front", 12.sp, color = c.muted); HqText("Back", 12.sp, color = c.muted)
            }
            if (tab == 2) {
                val recovering = d.week.lastHit.filter { now - it.value < 48 * 3_600_000L }.keys.sortedByDescending { d.week.lastHit[it] }.map { Muscles.NAMES[it] ?: it }
                HqText(if (recovering.isEmpty()) "Everything is rested." else "Recovering: " + recovering.joinToString(", ").lowercase().replaceFirstChar { it.uppercase() }, 14.sp, FontWeight.SemiBold)
                HqText("Worked in the last 48 hours. The colour fades over three days.", 12.sp, color = c.muted)
            } else {
                if (load.workouts == 0) {
                    HqText(if (tab == 0) "No workouts in the last 7 days." else "No workouts in the last 30 days.", 14.sp, color = c.muted)
                } else {
                    val behind = Muscles.behind(load, if (tab == 1) 30.0 / 7 else 1.0).map { Muscles.NAMES[it] ?: it }
                    if (behind.isNotEmpty()) HqText("Falling behind: " + behind.joinToString(", ").lowercase(), 14.sp, FontWeight.SemiBold, c.warn)
                    else HqText("Every main muscle is getting work.", 14.sp, FontWeight.SemiBold, c.ok)
                    val rows = load.sets.filter { it.value > 0 }.toList().sortedByDescending { it.second }
                    val max = maxOf(full, rows.maxOfOrNull { it.second } ?: 0.0)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { (k, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HqText(Muscles.NAMES[k] ?: k, 13.sp, modifier = Modifier.width(92.dp))
                                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.track)) {
                                    Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c.primary))
                                }
                                HqText(if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.UK, "%.1f", v), 13.sp, color = c.muted, modifier = Modifier.padding(start = 10.dp).width(34.dp))
                            }
                        }
                    }
                    HqText("Working sets. A helping muscle counts half a set. Warm-ups are left out.", 12.sp, color = c.muted)
                }
            }
        }
    }
}

/** Front and back figures side by side, each region filled from [base] towards [hot] by its value (0 to 1). */
@Composable
fun BodyMap(values: Map<String, Float>, hot: Color, modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    val base = c.track
    val skin = lerp(c.track, c.muted, 0.18f)
    Canvas(modifier) {
        // Unit space: two 100 x 200 figures with a 20-unit gap, scaled to fit.
        val u = minOf(size.width / 220f, size.height / 200f)
        val ox = (size.width - 220f * u) / 2f
        fun col(region: String): Color { val v = values[region] ?: 0f; return if (v <= 0f) base else lerp(base, hot, 0.25f + 0.75f * v) }
        for (side in 0..1) {
            val dx = ox + side * 120f * u
            fun P(x: Float, y: Float) = Offset(dx + x * u, y * u)
            fun oval(region: String?, cx: Float, cy: Float, rx: Float, ry: Float) =
                drawOval(if (region == null) skin else col(region), P(cx - rx, cy - ry), Size(2 * rx * u, 2 * ry * u))
            fun box(region: String?, l: Float, t: Float, r: Float, b: Float, rad: Float) =
                drawRoundRect(if (region == null) skin else col(region), P(l, t), Size((r - l) * u, (b - t) * u), CornerRadius(rad * u))
            fun poly(region: String, vararg pts: Float) {
                val p = Path().apply { moveTo(dx + pts[0] * u, pts[1] * u); var i = 2; while (i < pts.size) { lineTo(dx + pts[i] * u, pts[i + 1] * u); i += 2 }; close() }
                drawPath(p, col(region))
            }
            // Head, neck, hands, feet: plain.
            oval(null, 50f, 14f, 10f, 12f)
            box(null, 45f, 24f, 55f, 33f, 3f)
            oval(null, 18f, 106f, 4f, 6f); oval(null, 82f, 106f, 4f, 6f)
            oval(null, 42f, 194f, 6f, 4f); oval(null, 58f, 194f, 6f, 4f)
            box(null, 37f, 94f, 63f, 104f, 5f) // pelvis
            if (side == 0) {
                poly("traps", 45f, 30f, 36f, 36f, 45f, 36f); poly("traps", 55f, 30f, 64f, 36f, 55f, 36f)
                oval("shoulders", 29f, 42f, 9f, 8f); oval("shoulders", 71f, 42f, 9f, 8f)
                box("chest", 36f, 36f, 49.3f, 56f, 6f); box("chest", 50.7f, 36f, 64f, 56f, 6f)
                oval("biceps", 24f, 62f, 5.5f, 11f); oval("biceps", 76f, 62f, 5.5f, 11f)
                oval("forearms", 20f, 87f, 4.8f, 13f); oval("forearms", 80f, 87f, 4.8f, 13f)
                box("obliques", 35f, 59f, 40.5f, 92f, 4f); box("obliques", 59.5f, 59f, 65f, 92f, 4f)
                box("abs", 41.5f, 58f, 58.5f, 93f, 5f)
                oval("adductors", 47.6f, 114f, 2.6f, 12f); oval("adductors", 52.4f, 114f, 2.6f, 12f)
                oval("quads", 41.5f, 126f, 7.5f, 22f); oval("quads", 58.5f, 126f, 7.5f, 22f)
                oval(null, 42f, 151f, 4.5f, 3.5f); oval(null, 58f, 151f, 4.5f, 3.5f) // knees
                oval("calves", 42f, 172f, 5.5f, 16f); oval("calves", 58f, 172f, 5.5f, 16f)
            } else {
                box("upperback", 37f, 42f, 63f, 62f, 6f)
                poly("traps", 50f, 27f, 35f, 38f, 50f, 60f, 65f, 38f)
                oval("shoulders", 29f, 42f, 9f, 8f); oval("shoulders", 71f, 42f, 9f, 8f)
                poly("lats", 36f, 50f, 46f, 62f, 45f, 84f, 38f, 78f, 34f, 62f); poly("lats", 64f, 50f, 54f, 62f, 55f, 84f, 62f, 78f, 66f, 62f)
                box("lowerback", 44f, 70f, 56f, 93f, 4f)
                oval("triceps", 24f, 61f, 5.5f, 11f); oval("triceps", 76f, 61f, 5.5f, 11f)
                oval("forearms", 20f, 87f, 4.8f, 13f); oval("forearms", 80f, 87f, 4.8f, 13f)
                oval("glutes", 43f, 104f, 8f, 9f); oval("glutes", 57f, 104f, 8f, 9f)
                oval("hamstrings", 42f, 133f, 7f, 19f); oval("hamstrings", 58f, 133f, 7f, 19f)
                oval("calves", 42f, 170f, 6.5f, 14f); oval("calves", 58f, 170f, 6.5f, 14f)
            }
        }
    }
}
