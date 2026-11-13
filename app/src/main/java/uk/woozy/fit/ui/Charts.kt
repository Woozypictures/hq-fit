package uk.woozy.fit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.model.DayValue
import androidx.compose.foundation.layout.Arrangement

/** A line over time. [goal] draws a dashed line; the vertical range always includes it. */
@Composable
fun LineChart(points: List<DayValue>, goal: Double?, unit: String, modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    if (points.isEmpty()) return
    val lo = minOf(points.minOf { it.value }, goal ?: Double.MAX_VALUE) - 0.5
    val hi = maxOf(points.maxOf { it.value }, goal ?: -Double.MAX_VALUE) + 0.5
    val x0 = points.minOf { it.day }
    val x1 = maxOf(points.maxOf { it.day }, x0 + 1.0)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HqText("%.1f %s".format(java.util.Locale.UK, hi, unit), 11.sp, color = c.muted)
            if (goal != null) HqText("goal %.0f %s".format(java.util.Locale.UK, goal, unit), 11.sp, color = c.muted)
        }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            fun px(d: Double) = (((d - x0) / (x1 - x0)) * (w - 12f) + 6f).toFloat()
            fun py(v: Double) = (h - ((v - lo) / (hi - lo)) * (h - 12f) - 6f).toFloat()
            if (c.flat) listOf(0.25f, 0.5f, 0.75f).forEach { f -> drawLine(c.hairline, Offset(0f, h * f), Offset(w, h * f), strokeWidth = 1.5f) }
            if (goal != null) {
                drawLine(c.muted, Offset(0f, py(goal)), Offset(w, py(goal)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            val sorted = points.sortedBy { it.day }
            if (c.flat && sorted.size > 1) {
                val area = androidx.compose.ui.graphics.Path().apply {
                    moveTo(px(sorted.first().day), h)
                    sorted.forEach { lineTo(px(it.day), py(it.value)) }
                    lineTo(px(sorted.last().day), h); close()
                }
                drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.22f), c.primary.copy(alpha = 0f))))
            }
            for (i in 0 until sorted.size - 1) {
                drawLine(c.primary, Offset(px(sorted[i].day), py(sorted[i].value)), Offset(px(sorted[i + 1].day), py(sorted[i + 1].value)), strokeWidth = if (c.flat) 4f else 5f, cap = StrokeCap.Round)
            }
            if (c.flat) {
                sorted.dropLast(1).forEach { drawCircle(c.primary, radius = 4f, center = Offset(px(it.day), py(it.value))) }
                sorted.last().let { drawCircle(c.card, radius = 12f, center = Offset(px(it.day), py(it.value))); drawCircle(c.primary, radius = 8f, center = Offset(px(it.day), py(it.value))) }
            } else sorted.forEach { drawCircle(c.primary, radius = 7f, center = Offset(px(it.day), py(it.value))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HqText("%.1f %s".format(java.util.Locale.UK, lo, unit), 11.sp, color = c.muted)
        }
    }
}

/** Bars with a label each. A bar of zero is drawn as a faint stub so an empty day still shows. */
@Composable
fun BarChart(values: List<Double>, labels: List<String>, target: Double? = null, modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    if (values.isEmpty()) return
    val top = maxOf(values.max(), target ?: 0.0, 1.0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / values.size
            val barW = slot * 0.62f
            values.forEachIndexed { i, v ->
                val bh = if (v <= 0.0) 4f else ((v / top) * (size.height - 4f)).toFloat()
                if (c.flat) drawRoundRect(c.track, Offset(i * slot + (slot - barW) / 2f, 0f), Size(barW, size.height), androidx.compose.ui.geometry.CornerRadius(14f, 14f))
                drawRoundRect(
                    color = if (v <= 0.0) c.muted.copy(alpha = 0.3f) else c.primary,
                    topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - bh),
                    size = Size(barW, bh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (c.flat) 14f else 6f, if (c.flat) 14f else 6f),
                )
            }
            if (target != null) {
                val y = size.height - ((target / top) * (size.height - 4f)).toFloat()
                drawLine(c.muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { HqText(it, 10.sp, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

/**
 * Calories per day split into protein, carbs and fat, like Cronometer's Calories Consumed chart. Each bar is the
 * day's logged calories; the three colours share it in proportion to the calories each macro supplied.
 */
@Composable
fun StackedMacroChart(days: List<MacroDay>, labels: List<String>, target: Double? = null, modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    if (days.isEmpty()) return
    val top = maxOf(days.maxOf { it.kcal }, target ?: 0.0, 1.0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.62f
            days.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2f
                if (d.kcal <= 0.0) {
                    drawRoundRect(c.muted.copy(alpha = 0.3f), Offset(x, size.height - 4f), Size(barW, 4f), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                    return@forEachIndexed
                }
                val h = ((d.kcal / top) * (size.height - 4f)).toFloat()
                val parts = listOf(d.proteinKcal to c.protein, d.carbsKcal to c.carbs, d.fatKcal to c.fat)
                val sum = parts.sumOf { it.first }.takeIf { it > 0 }
                var y = size.height
                if (sum == null) {
                    drawRect(c.primary, Offset(x, y - h), Size(barW, h))
                } else parts.forEach { (v, col) ->
                    val ph = (h * (v / sum)).toFloat()
                    if (ph > 0f) { drawRect(col, Offset(x, y - ph), Size(barW, ph)); y -= ph }
                }
            }
            if (target != null) {
                val ty = size.height - ((target / top) * (size.height - 4f)).toFloat()
                drawLine(c.muted, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { HqText(it, 10.sp, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Protein" to c.protein, "Carbs" to c.carbs, "Fat" to c.fat).forEach { (n, col) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Canvas(Modifier.height(10.dp).width(10.dp)) { drawCircle(col) }
                    HqText(n, 12.sp, color = c.muted)
                }
            }
        }
    }
}

data class MacroDay(val kcal: Double, val proteinKcal: Double, val carbsKcal: Double, val fatKcal: Double)
