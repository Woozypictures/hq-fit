package uk.woozy.fit.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    val shape = RoundedCornerShape(if (c.flat) 22.dp else 16.dp)
    val frosted = LocalFrosted.current
    val base = if (c.flat && !frosted) {
        modifier.clip(shape).background(c.card)
    } else if (frosted) {
        // Frosted glass as in HQ: fill 5% to 22% white, rim 23% to 38% white.
        modifier.clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.22f))))
            .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.23f))), shape)
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
        .clip(RoundedCornerShape(if (c.flat) 22.dp else 16.dp))
        .background(Brush.linearGradient(listOf(c.gradientStart, c.gradientEnd)))
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
    HqText(text.uppercase(), size, FontWeight.SemiBold, color, modifier, letterSpacing = 0.25.em)
}

/** Small caps label above a block. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    HqText(text.uppercase(), 12.sp, FontWeight.SemiBold, HqTheme.colors.muted, modifier, letterSpacing = 0.1.em)
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
        HqText(text, 15.sp, FontWeight.SemiBold, foreground)
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
            val sweep = 360f * progress.coerceIn(0f, 1f)
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
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
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
            HqText("${formatWhole(value)} / ${formatWhole(target)} $unit", 13.sp, FontWeight.Normal, HqTheme.colors.muted)
        }
        Spacer(Modifier.height(6.dp))
        HqProgressBar(fraction = if (target > 0) (value / target).toFloat() else 0f, color = color)
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
        HqText("SET", 11.sp, FontWeight.SemiBold, muted, Modifier.width(36.dp), letterSpacing = 0.1.em)
        HqText("PREVIOUS", 11.sp, FontWeight.SemiBold, muted, Modifier.weight(1f), letterSpacing = 0.1.em)
        HqText(weightUnit, 11.sp, FontWeight.SemiBold, muted, Modifier.width(72.dp), TextAlign.Center, letterSpacing = 0.1.em)
        HqText("REPS", 11.sp, FontWeight.SemiBold, muted, Modifier.width(72.dp), TextAlign.Center, letterSpacing = 0.1.em)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun EditableCell(text: String, color: Color, active: Boolean, onClick: (() -> Unit)?) {
    val c = HqTheme.colors
    Box(
        modifier = Modifier
            .width(72.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) c.track else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        HqText(text.ifEmpty { "-" }, 17.sp, FontWeight.SemiBold, color, textAlign = TextAlign.Center)
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
) {
    val c = HqTheme.colors
    val value = if (done) c.primary else c.ink
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HqText(number, 15.sp, FontWeight.SemiBold, c.muted, Modifier.width(36.dp))
        HqText(previous, 13.sp, FontWeight.Normal, c.muted, Modifier.weight(1f))
        EditableCell(weight, value, weightActive, onWeightClick)
        EditableCell(reps, value, repsActive, onRepsClick)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (done) c.ink else c.track)
                .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = if (done) "Set done" else "Mark set done",
                tint = if (done) c.page else c.muted,
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
