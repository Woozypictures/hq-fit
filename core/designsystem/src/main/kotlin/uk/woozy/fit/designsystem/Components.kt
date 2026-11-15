package uk.woozy.fit.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Whole number with thousands separators, UK style: 2,297. */
fun formatWhole(value: Double): String = String.format(Locale.UK, "%,.0f", value)

/** One decimal place: 87.4. */
fun formatOne(value: Double): String = String.format(Locale.UK, "%,.1f", value)

// ---------------------------------------------------------------- cards

/** White card, 16 dp corners. */
@Composable
fun HqCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = HqTheme.colors
    val shape = RoundedCornerShape(if (c.flat) 22.dp else 18.dp)
    val frosted = LocalFrosted.current
    val base = if (c.flat && !frosted) {
        modifier.clip(shape).background(c.card)
    } else if (frosted) {
        // Frosted glass scheme: cards are solid and crisp (glass is kept for navigation only, as Apple advises).
        modifier.clip(shape).background(Color(0xFF3A3A3A)).border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    } else {
        modifier.clip(shape).background(c.card).border(1.dp, c.hairline, shape)
    }
    Column(modifier = if (onClick != null) base.clickable(onClick = onClick) else base, content = content)
}

/** Everfit's blue gradient card, used for today's workout. */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = HqTheme.colors
    val base = modifier
        .clip(RoundedCornerShape(if (c.flat) 22.dp else 18.dp))
        .background(Brush.linearGradient(listOf(c.gradientStart, c.gradientEnd)))
        // A little texture, as on Everfit's top card: soft overlapping waves and circles.
        .drawBehind {
            val w = size.width
            val h = size.height
            val wave = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.45f, 0f)
                cubicTo(w * 0.62f, h * 0.30f, w * 0.80f, h * 0.02f, w, h * 0.34f)
                lineTo(w, 0f)
                close()
            }
            drawPath(wave, Color.White.copy(alpha = 0.07f))
            val wave2 = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * 0.78f)
                cubicTo(w * 0.22f, h * 0.60f, w * 0.48f, h * 1.00f, w * 0.78f, h * 0.76f)
                cubicTo(w * 0.90f, h * 0.68f, w * 0.96f, h * 0.70f, w, h * 0.66f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(wave2, Color.Black.copy(alpha = 0.07f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = h * 0.55f, center = androidx.compose.ui.geometry.Offset(w * 0.92f, h * 0.18f))
            drawCircle(Color.White.copy(alpha = 0.04f), radius = h * 0.32f, center = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.95f))
        }
    Column(modifier = if (onClick != null) base.clickable(onClick = onClick) else base, content = content)
}

/** Full-width blue header with rounded bottom corners, for the workout screens. The caller adds status-bar padding. */
@Composable
fun GradientHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = HqTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brush.linearGradient(listOf(c.gradientStart, c.gradientEnd))),
        content = content,
    )
}

// ---------------------------------------------------------------- text helpers

/** Letter-spaced capitals, as Everfit's workout titles ("F U L L B O D Y  A"). */
@Composable
fun SpacedTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HqTheme.colors.ink,
    size: androidx.compose.ui.unit.TextUnit = 22.sp,
) {
    HqText(text.uppercase(), size, FontWeight.SemiBold, color, modifier, letterSpacing = 0.06.em)
}

/** Small caps label above a block. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    if (HqTheme.colors.flat) HqText(text, 14.sp, FontWeight.Medium, HqTheme.colors.muted, modifier)
    else HqText(text.uppercase(), 12.sp, FontWeight.SemiBold, HqTheme.colors.muted, modifier, letterSpacing = 0.04.em)
}

// ---------------------------------------------------------------- buttons

enum class PillStyle { Filled, Outline, OnGradient }

/** Pill button: 48 dp tall, fully rounded. */
@Composable
fun HqPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Filled,
    enabled: Boolean = true,
) {
    val c = HqTheme.colors
    val background: Color
    val foreground: Color
    when (style) {
        PillStyle.Filled -> { background = c.primary; foreground = c.onPrimary }
        PillStyle.Outline -> { background = Color.Transparent; foreground = c.primary }
        PillStyle.OnGradient -> { background = c.onGradient; foreground = c.gradientStart }
    }
    val outline = if (style == PillStyle.Outline) Modifier.border(1.5.dp, c.primary, CircleShape) else Modifier
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(background)
            .then(outline)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        HqText(text, 15.sp, FontWeight.SemiBold, foreground, maxLines = 1)
    }
}

/** Two or three options in a row; the chosen one is filled blue. Used for the theme switch. */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = HqTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(c.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (on) c.primary else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                HqText(label, 14.sp, FontWeight.SemiBold, if (on) c.onPrimary else c.muted)
            }
        }
    }
}

// ---------------------------------------------------------------- rings and bars

/** Progress ring with room for text in the middle. */
@Composable
fun HqRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 88.dp,
    stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = HqTheme.colors.track
    val shown = springValue(progress.coerceIn(0f, 1f))
    Box(
        modifier = modifier.size(diameter).semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = s / 2f
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(width = s),
            )
            val sweep = 360f * shown
            if (sweep > 0f) {
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = arc, style = Stroke(width = s, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** A value that springs from 0 to [target] when it first shows and to each new target after (instant in previews and with Remove animations on). */
@Composable
fun springValue(target: Float): Float {
    val still = androidx.compose.ui.platform.LocalInspectionMode.current || rememberReduceMotion()
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (still) target else 0f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (still) a.snapTo(target) else a.animateTo(target, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 120f))
    }
    return a.value
}

/** A number that counts up to [target] when it first shows and eases to each new value (instant in previews and with Remove animations on). */
@Composable
fun countUp(target: Double): Double {
    val still = androidx.compose.ui.platform.LocalInspectionMode.current || rememberReduceMotion()
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (still) target.toFloat() else 0f) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (still) a.snapTo(target.toFloat()) else a.animateTo(target.toFloat(), androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return a.value.toDouble()
}

/** Thin horizontal bar, 0 to 1. */
@Composable
fun HqProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val track = HqTheme.colors.track
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(springValue(fraction.coerceIn(0f, 1f)))
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Label, "value / target unit", and a bar. Cronometer's macro rows. */
@Composable
fun MacroBar(
    label: String,
    value: Double,
    target: Double,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HqText(label, 14.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
            val over = target > 0 && value > target
            HqText(if (over) "+${formatWhole(value - target)} $unit over" else if (MacroMode.remaining && target > 0) "${formatWhole(target - value)} $unit left" else "${formatWhole(value)} / ${formatWhole(target)} $unit", 13.sp, FontWeight.Normal, if (over) HqTheme.colors.warn else HqTheme.colors.muted)
        }
        Spacer(Modifier.height(6.dp))
        HqProgressBar(fraction = if (target > 0) (value / target).toFloat() else 0f, color = if (target > 0 && value > target) HqTheme.colors.warn else color)
    }
}

// ---------------------------------------------------------------- the set table

/** Column headings above the set rows. */
@Composable
fun SetTableHeader(weightUnit: String = "KG", modifier: Modifier = Modifier) {
    val muted = HqTheme.colors.muted
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val flat = HqTheme.colors.flat
        fun h(t: String) = if (flat) t else t.uppercase()
        val sp = if (flat) androidx.compose.ui.unit.TextUnit.Unspecified else 0.1.em
        HqText(h("Set"), 12.sp, FontWeight.Medium, muted, Modifier.width(36.dp), letterSpacing = sp)
        HqText(h("Previous"), 12.sp, FontWeight.Medium, muted, Modifier.weight(1f), letterSpacing = sp)
        HqText(if (flat) weightUnit.lowercase() else weightUnit, 12.sp, FontWeight.Medium, muted, Modifier.width(72.dp), TextAlign.Center, letterSpacing = sp)
        HqText(h("Reps"), 12.sp, FontWeight.Medium, muted, Modifier.width(72.dp), TextAlign.Center, letterSpacing = sp)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun EditableCell(text: String, color: Color, active: Boolean, onClick: (() -> Unit)?, ghost: String = "") {
    val c = HqTheme.colors
    Box(
        modifier = Modifier
            .width(72.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) c.track else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Ghost text: last time's figure in grey until you type or tick; a tick accepts it.
        if (text.isEmpty() && ghost.isNotEmpty()) HqText(ghost, 17.sp, FontWeight.SemiBold, c.muted.copy(alpha = 0.55f), textAlign = TextAlign.Center)
        else HqText(text.ifEmpty { "-" }, 17.sp, FontWeight.SemiBold, color, textAlign = TextAlign.Center)
    }
}

/** One set: number, last session's result, weight, reps and the tick. A ticked set turns blue and the tick fills dark, as in Everfit. */
@Composable
fun SetRow(
    number: String,
    previous: String,
    weight: String,
    reps: String,
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onWeightClick: (() -> Unit)? = null,
    onRepsClick: (() -> Unit)? = null,
    weightActive: Boolean = false,
    repsActive: Boolean = false,
    weightGhost: String = "",
    repsGhost: String = "",
    current: Boolean = false,
    divider: Boolean = false,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val c = HqTheme.colors
    val value = if (done) c.primary else c.ink
    // Done rows wash in the accent; the next set to do sits on the inset tone so the eye lands on it.
    val rowBg by androidx.compose.animation.animateColorAsState(
        when {
            c.flat && done -> c.primary.copy(alpha = 0.10f)
            c.flat && current -> c.track.copy(alpha = 0.55f)
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        androidx.compose.animation.core.tween(220), label = "row",
    )
    val tick = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    val wasDone = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(done) }
    androidx.compose.runtime.LaunchedEffect(done) {
        if (done && !wasDone.value) { tick.snapTo(0.8f); tick.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 520f)) }
        wasDone.value = done
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(rowBg)
            .then(if (divider) Modifier.drawBehind { drawLine(c.ink.copy(alpha = if (c.dark) 0.12f else 0.08f), androidx.compose.ui.geometry.Offset(16.dp.toPx(), 0f), androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), 0f), 1f) } else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HqText(number, 15.sp, FontWeight.SemiBold, if (current && !done) c.primary else c.muted, Modifier.width(36.dp))
        HqText(previous, 13.sp, FontWeight.Normal, c.muted, Modifier.weight(1f))
        EditableCell(weight, value, weightActive, onWeightClick, if (done) "" else weightGhost)
        EditableCell(reps, value, repsActive, onRepsClick, if (done) "" else repsGhost)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = tick.value; scaleY = tick.value }
                .clip(CircleShape)
                .background(if (done) (if (c.flat) c.primary else c.ink) else c.track)
                .toggleable(value = done, role = Role.Checkbox, onValueChange = {
                    haptic.performHapticFeedback(if (it) androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress else androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onToggle()
                }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = if (done) "Set done" else "Mark set done",
                tint = if (done) (if (c.flat) c.onPrimary else c.page) else c.muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- number keypad

sealed interface KeypadKey {
    data class Digit(val digit: Char) : KeypadKey
    data object Dot : KeypadKey
    data object Backspace : KeypadKey
    data object Minus : KeypadKey
    data object Plus : KeypadKey
    data object Done : KeypadKey
}

/** Our own bottom keypad with stepper buttons, so the system keyboard never covers the set table. */
@Composable
fun NumberKeypad(
    onKey: (KeypadKey) -> Unit,
    stepLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = HqTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.card)
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9')).forEach { digits ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digits.forEach { d ->
                    KeyText(d.toString(), Modifier.weight(1f)) { onKey(KeypadKey.Digit(d)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyText(".", Modifier.weight(1f)) { onKey(KeypadKey.Dot) }
            KeyText("0", Modifier.weight(1f)) { onKey(KeypadKey.Digit('0')) }
            KeyIcon(Icons.AutoMirrored.Rounded.Backspace, "Delete", Modifier.weight(1f)) { onKey(KeypadKey.Backspace) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyIcon(Icons.Rounded.Remove, "Minus $stepLabel", Modifier.weight(1f)) { onKey(KeypadKey.Minus) }
            KeyIcon(Icons.Rounded.Add, "Plus $stepLabel", Modifier.weight(1f)) { onKey(KeypadKey.Plus) }
            HqPillButton("Done", onClick = { onKey(KeypadKey.Done) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyText(label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = HqTheme.colors
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.page)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        HqText(label, 22.sp, FontWeight.Medium)
    }
}

@Composable
private fun KeyIcon(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    val c = HqTheme.colors
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.page)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = c.ink, modifier = Modifier.size(24.dp))
    }
}

// ---------------------------------------------------------------- bottom bar

data class BarItem(val label: String, val icon: ImageVector)

/**
 * Four tabs with a raised + between the second and third (Today, Train, +, Diary, Progress).
 * [items] must hold exactly four entries.
 */
@Composable
fun HqBottomBar(
    items: List<BarItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onCentre: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.size == 4) { "HqBottomBar takes four tabs" }
    val c = HqTheme.colors
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp).background(c.card), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            // On a tablet the tabs stay grouped in the middle rather than spreading across the whole width.
            Row(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().navigationBarsPadding().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { i, item ->
                    if (i == 2) Spacer(Modifier.weight(1f))
                    BarTab(item, i == selected, Modifier.weight(1f)) { onSelect(i) }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(56.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(c.primary)
                .clickable(role = Role.Button, onClickLabel = "Quick add", onClick = onCentre),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Quick add", tint = c.onPrimary, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun BarTab(item: BarItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = HqTheme.colors
    val tint = if (selected) c.primary else c.muted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        HqText(item.label, 11.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium, tint)
    }
}


/**
 * Cronometer's Consumed / Remaining switch, shared by every calorie and macro readout in the app and remembered
 * between launches. Remaining is the default (what Woozy showed before).
 */
object MacroMode {
    var remaining by androidx.compose.runtime.mutableStateOf(true)
        private set
    private var loaded = false
    fun load(ctx: android.content.Context) {
        if (loaded) return
        loaded = true
        remaining = ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getBoolean("macro_remaining", true)
    }
    fun set(ctx: android.content.Context, value: Boolean) {
        remaining = value; loaded = true
        ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putBoolean("macro_remaining", value).apply()
    }
}

/** Two-segment control: Consumed | Remaining, with a thumb that springs across. */
@Composable
fun ConsumedRemainingToggle(modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { MacroMode.load(ctx) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val on = MacroMode.remaining
    val pos by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, WoozyTokens.Motion.pill, label = "seg")
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.height(36.dp).clip(CircleShape).background(c.track).padding(3.dp),
    ) {
        val half = maxWidth / 2
        Box(
            Modifier.offset(x = half * pos).width(half).fillMaxHeight().clip(CircleShape).background(c.card)
                .then(if (c.dark) Modifier else Modifier.border(0.5.dp, c.ink.copy(alpha = 0.06f), CircleShape)),
        )
        Row(Modifier.fillMaxSize()) {
            listOf("Consumed" to false, "Remaining" to true).forEach { (label, v) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(role = Role.Tab, interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            if (on != v) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); MacroMode.set(ctx, v) }
                        },
                    contentAlignment = Alignment.Center,
                ) { HqText(label, 13.sp, if (on == v) FontWeight.SemiBold else FontWeight.Normal, if (on == v) c.ink else c.muted) }
            }
        }
    }
}

/** Woozy hero for calories: the number left is the headline, then a bar, then eaten / burned / target. */
@Composable
fun CalorieHero(eaten: Double, burned: Double?, target: Double) {
    val c = HqTheme.colors
    val left = target - eaten
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Follows the Consumed / Remaining switch; tapping the big number flips it too.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) { MacroMode.load(ctx) }
        val eatenView = !MacroMode.remaining
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Switch between eaten and left") { MacroMode.set(ctx, eatenView) },
            verticalAlignment = Alignment.Bottom,
        ) {
            HqText(formatWhole(countUp(if (eatenView) eaten else kotlin.math.abs(left))), 56.sp, FontWeight.Bold, letterSpacing = (-2).sp)
            HqText(if (eatenView) "  kcal eaten" else if (left < 0) "  kcal over" else "  kcal left", 16.sp, FontWeight.Medium, if (!eatenView && left < 0) c.warn else c.muted, Modifier.padding(bottom = 10.dp))
        }
        HqProgressBar(fraction = if (target > 0) (eaten / target).toFloat() else 0f, color = if (left < 0) c.warn else c.primary, height = 10.dp)
        Row(Modifier.fillMaxWidth()) {
            HeroStat("Eaten", formatWhole(countUp(eaten)), Modifier.weight(1f))
            HeroStat("Active", burned?.takeIf { it > 0 }?.let { formatWhole(it) } ?: "-", Modifier.weight(1f))
            HeroStat("Target", formatWhole(target), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        HqText(label, 12.sp, FontWeight.Medium, HqTheme.colors.muted)
        HqText(value, 18.sp, FontWeight.SemiBold)
    }
}

/** One macro as a compact column: name, value over target, thin bar. Three sit side by side. */
@Composable
fun MacroColumn(label: String, value: Double, target: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HqText(label, 12.sp, FontWeight.Medium, HqTheme.colors.muted)
        val over = target > 0 && value > target
        val text = when {
            over -> "+${formatWhole(value - target)} g over"
            MacroMode.remaining && target > 0 -> "${formatWhole(target - value)} g left"
            else -> "${formatWhole(value)} / ${formatWhole(target)} g"
        }
        HqText(text, 14.sp, FontWeight.SemiBold, if (over) HqTheme.colors.warn else HqTheme.colors.ink)
        HqProgressBar(fraction = if (target > 0) (value / target).toFloat() else 0f, color = if (over) HqTheme.colors.warn else color, height = 4.dp)
    }
}

/**
 * Live sync status pill (solid content surface, not glass): a dot, the source and how fresh it is, ticking every
 * minute. Green when fresh, amber past two hours, accent and pulsing while a sync runs, red on a failure. Tap syncs.
 */
@Composable
fun SyncStatusPill(
    lastMs: Long,
    syncing: Boolean,
    watchLastMs: Long?,
    failed: Boolean = false,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = HqTheme.colors
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) }
    }
    fun ago(ms: Long): String {
        val m = ((now - ms) / 60_000).toInt()
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 24 * 60 -> java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            else -> "${m / (24 * 60)} d ago"
        }
    }
    val stale = lastMs <= 0 || now - lastMs > 2 * 60 * 60_000L
    val dot = when { syncing -> c.primary; failed -> Color(0xFFE5484D); stale -> c.warn; else -> c.ok }
    // Pulse only while a sync runs (an idle infinite transition would keep Home redrawing every frame).
    val dotAlpha = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(syncing) {
        if (syncing) while (true) { dotAlpha.animateTo(0.25f, androidx.compose.animation.core.tween(700)); dotAlpha.animateTo(1f, androidx.compose.animation.core.tween(700)) }
        else dotAlpha.snapTo(1f)
    }
    val text = buildString {
        append(if (syncing) "Syncing Health Connect" else if (failed) "Health Connect sync failed" else if (lastMs > 0) "Health Connect · ${ago(lastMs)}" else "Health Connect · not synced")
        if (!syncing && watchLastMs != null) append("  ·  Watch ${ago(watchLastMs)}")
    }
    Row(
        modifier.clip(CircleShape).background(c.track.copy(alpha = if (c.dark) 0.8f else 0.7f))
            .clickable(role = Role.Button, onClickLabel = "Sync now", enabled = !syncing, onClick = onSync)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = dotAlpha.value }.clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        HqText(text, 12.sp, FontWeight.Medium, if (stale && !syncing) c.warn else c.muted, maxLines = 1)
    }
}
