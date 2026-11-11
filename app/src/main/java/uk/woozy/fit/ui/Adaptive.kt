package uk.woozy.fit.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import java.time.LocalDate

/** Today's date that moves on at midnight and when the app comes back, instead of the day the screen first opened. */
@Composable
fun rememberToday(): LocalDate {
    val d by produceState(LocalDate.now()) {
        while (true) {
            val now = LocalDate.now()
            if (now != value) value = now
            delay(30_000)
        }
    }
    return d
}

/** True on a tablet or an unfolded phone: wide enough for two columns of cards. */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 840

/** Widest a page of reading text or forms should get. Tab pages with cards may go wider, in two columns. */
val FORM_MAX: Dp = 760.dp
val CARDS_MAX: Dp = 1280.dp

/** Centres [content] and caps its width, so pages do not stretch edge to edge on a tablet. */
@Composable
fun Centred(max: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max).fillMaxWidth()) { content() }
    }
}

/** HQ's 30-square progress bar: lit squares for the fraction done, faint ones for the rest. */
@Composable
fun Segments(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp, count: Int = 30) {
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (count - 1)) / count
        val lit = (f * count).toInt().let { if (f > 0f && it == 0) 1 else it }
        for (i in 0 until count) {
            drawRoundRect(
                color = if (i < lit) color else color.copy(alpha = 0.18f),
                topLeft = Offset(i * (w + gap), 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/** A long press anywhere on a card (even over its buttons) starts rearranging, as in HQ. */
private fun Modifier.onLongPress(onLong: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val r = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result = true
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.all { !it.pressed }) break
                if (e.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) { result = false; break }
            }
            result
        }
        if (r == null) {
            onLong()
            // Swallow the rest of this press, so lifting the finger does not also tap the button under it.
            do {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    }
}

/** Per-page card order and hidden cards, kept in preferences. */
class CardPrefs(private val context: android.content.Context, private val page: String, private val defaults: List<String>) {
    private val p get() = context.getSharedPreferences("cards", android.content.Context.MODE_PRIVATE)
    var order by mutableStateOf(load("order_$page") { it in defaults }.let { saved -> saved + defaults.filter { it !in saved } })
        private set
    var hidden by mutableStateOf(load("hidden_$page") { it in defaults }.toSet())
        private set

    private fun load(key: String, keep: (String) -> Boolean): List<String> =
        // Today's order used to live in its own file; read it once so a saved order is not lost.
        (p.getString(key, null) ?: if (key == "order_today") context.getSharedPreferences("today", 0).getString("order", null) else null)
            ?.split(',')?.filter { it.isNotBlank() && keep(it) }?.distinct().orEmpty()

    fun setOrder(o: List<String>) { order = o; p.edit().putString("order_$page", o.joinToString(",")).apply() }
    fun setHidden(h: Set<String>) { hidden = h; p.edit().putString("hidden_$page", h.joinToString(",")).apply() }
}

@Composable
fun rememberCardPrefs(page: String, defaults: List<String>): CardPrefs {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(page) { CardPrefs(context.applicationContext, page, defaults) }
}

/**
 * HQ's rearranging, for HQ Fit pages. Long-press any card to start: cards wiggle, a handle in the top corner drags a
 * card up or down, and each card can be hidden (hidden cards are listed at the bottom to show again). Tap Done to
 * finish. On a wide screen the cards sit in two columns, except while rearranging, when they line up in one.
 * [available] leaves out cards that have nothing to show right now (they keep their place in the order).
 */
@Composable
fun CardColumn(
    prefs: CardPrefs,
    names: Map<String, String>,
    available: (String) -> Boolean = { true },
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    card: @Composable (String) -> Unit,
) {
    val c = HqTheme.colors
    val vis = prefs.order.filter { it !in prefs.hidden && available(it) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(0f) }
    val gap = with(LocalDensity.current) { 16.dp.toPx() }

    @Composable
    fun one(key: String) {
        val isDragging = dragging == key
        val wiggle = rememberInfiniteTransition(label = "wiggle")
        val phase = (key.hashCode() and 3) * 40
        val angle by wiggle.animateFloat(-0.9f, 0.9f, infiniteRepeatable(tween(130 + phase, easing = LinearEasing), RepeatMode.Reverse), label = "a")
        Box(
            Modifier.onSizeChanged { heights[key] = it.height }
                .graphicsLayer {
                    if (editing && !isDragging) rotationZ = angle
                    if (isDragging) { translationY = offset; scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 24f }
                }
                .zIndex(if (isDragging) 1f else 0f)
                .then(if (!editing) Modifier.onLongPress { onEditing(true) } else Modifier),
        ) {
            card(key)
            // While rearranging, taps on the card itself do nothing.
            if (editing) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = {}) })
            if (editing) Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.card.copy(alpha = 0.92f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { prefs.setHidden(prefs.hidden + key) }) },
                    contentAlignment = Alignment.Center,
                ) { Icon(androidx.compose.material.icons.Icons.Rounded.VisibilityOff, contentDescription = "Hide ${names[key] ?: key}", tint = c.ink, modifier = Modifier.size(20.dp)) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.card.copy(alpha = 0.92f)).pointerInput(key) {
                        detectDragGestures(
                            onDragStart = { dragging = key; offset = 0f },
                            onDragEnd = { dragging = null; offset = 0f },
                            onDragCancel = { dragging = null; offset = 0f },
                        ) { change, drag ->
                            change.consume()
                            offset += drag.y
                            val now = prefs.order.filter { it !in prefs.hidden && available(it) }
                            val i = now.indexOf(key)
                            val other = when {
                                offset > 0 && i < now.lastIndex -> now[i + 1]
                                offset < 0 && i > 0 -> now[i - 1]
                                else -> null
                            }
                            if (other != null) {
                                val oh = (heights[other] ?: 0) + gap
                                if (kotlin.math.abs(offset) > oh / 2) {
                                    val o = prefs.order.toMutableList()
                                    val a = o.indexOf(key); val b = o.indexOf(other)
                                    o.removeAt(a); o.add(b, key)
                                    prefs.setOrder(o)
                                    offset += if (offset > 0) -oh else oh
                                }
                            }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.DragIndicator, contentDescription = "Drag to move", tint = c.ink, modifier = Modifier.size(22.dp)) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (editing) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HqText("Drag the handle to move a card. Tap the eye to hide one.", 12.sp, color = c.muted, modifier = Modifier.weight(1f))
                HqPillButton("Done", onClick = { onEditing(false) })
            }
        }
        if (isWide() && !editing) {
            // Two columns, filled left, right, left... so the order still reads top to bottom.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(0, 1).forEach { col ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        vis.filterIndexed { i, _ -> i % 2 == col }.forEach { k -> androidx.compose.runtime.key(k) { one(k) } }
                    }
                }
            }
        } else {
            vis.forEach { k -> androidx.compose.runtime.key(k) { one(k) } }
        }
        val hiddenNow = prefs.order.filter { it in prefs.hidden }
        if (editing && hiddenNow.isNotEmpty()) {
            HqText("Hidden cards", 13.sp, color = c.muted)
            hiddenNow.forEach { k ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HqText(names[k] ?: k, 15.sp, modifier = Modifier.weight(1f))
                    HqPillButton("Show", style = PillStyle.Outline, onClick = { prefs.setHidden(prefs.hidden - k) })
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGestures(onTap: () -> Unit) =
    androidx.compose.foundation.gestures.detectTapGestures(onTap = { onTap() })
