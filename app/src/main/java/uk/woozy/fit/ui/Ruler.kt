package uk.woozy.fit.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.woozy.fit.designsystem.HqTheme
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A ruler you drag sideways to pick a number (GymMane's rest and bodyweight dial). The centre line is the value;
 * every [step] is a tick, every [majorEvery] ticks a taller tick with a [label]. Each step gives a light tick of
 * haptics. The caller shows the big number above it.
 */
@Composable
fun RulerPicker(
    value: Double,
    onChange: (Double) -> Unit,
    min: Double,
    max: Double,
    step: Double,
    majorEvery: Int,
    label: (Double) -> String,
    modifier: Modifier = Modifier,
    description: String = "Drag to change",
) {
    val c = HqTheme.colors
    val view = LocalView.current
    val tickPx = with(LocalDensity.current) { 11.dp.toPx() }
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var drift by remember { mutableFloatStateOf(0f) }
    fun snap(v: Double) = ((v / step).roundToLong() * step).coerceIn(min, max)
    val textPaint = remember(c) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.muted.toArgb(); textAlign = Paint.Align.CENTER }
    }
    val textSize = with(LocalDensity.current) { 11.dp.toPx() }
    Canvas(
        modifier.fillMaxWidth().height(72.dp)
            .semantics { contentDescription = description }
            .pointerInput(min, max, step) {
                detectHorizontalDragGestures(onDragEnd = { drift = 0f }, onDragCancel = { drift = 0f }) { ch, dx ->
                    ch.consume()
                    drift -= dx
                    val steps = (drift / tickPx).toInt()
                    if (steps != 0) {
                        drift -= steps * tickPx
                        val nv = snap(current + steps * step)
                        if (nv != current) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            change(nv)
                        }
                    }
                }
            },
    ) {
        val cx = size.width / 2f
        val base = size.height - 22.dp.toPx()
        val half = (size.width / 2f / tickPx).roundToInt() + 2
        val centreIndex = (value / step).roundToLong()
        textPaint.textSize = textSize
        for (k in -half..half) {
            val idx = centreIndex + k
            val v = idx * step
            if (v < min - 1e-9 || v > max + 1e-9) continue
            val x = cx + k * tickPx - drift
            val major = idx % majorEvery == 0L
            val fade = (1f - kotlin.math.abs(x - cx) / (size.width / 2f)).coerceIn(0.15f, 1f)
            val h = if (major) 26.dp.toPx() else 13.dp.toPx()
            drawLine(c.muted.copy(alpha = 0.55f * fade), Offset(x, base - h), Offset(x, base), 1.5.dp.toPx(), StrokeCap.Round)
            if (major) drawIntoCanvas { it.nativeCanvas.drawText(label(v), x, size.height - 4.dp.toPx(), textPaint.apply { alpha = (255 * fade).toInt() }) }
        }
        drawLine(c.primary, Offset(cx, base - 34.dp.toPx()), Offset(cx, base + 2.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
    }
}
