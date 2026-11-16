package uk.woozy.fit.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme

/** Page title row used by the four tabs. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier) {
    HqText(title, if (HqTheme.colors.flat) 36.sp else 28.sp, FontWeight.Bold, modifier = modifier, letterSpacing = if (HqTheme.colors.flat) (-1).sp else androidx.compose.ui.unit.TextUnit.Unspecified)
}

/**
 * Shrinking titles (GymMane style): the page title starts big under a round back button and, as the page scrolls,
 * folds into a small centred title in the top bar. One shared state at the root listens to every page's scrolling
 * (nested scroll), so any scrollable page under a [BackBar] gets it without its own wiring. It resets on each page.
 */
class TitleCollapse {
    var offset by androidx.compose.runtime.mutableFloatStateOf(0f)
    /** Height the big title can fold away; 0 while no collapsing [BackBar] is on screen. */
    var maxPx = 0f
    internal var users = 0
    fun reset() { offset = 0f }
    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (available.y >= 0f || maxPx <= 0f) return androidx.compose.ui.geometry.Offset.Zero
            val old = offset
            offset = (offset - available.y).coerceAtMost(maxPx)
            return androidx.compose.ui.geometry.Offset(0f, old - offset)
        }
        override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (available.y <= 0f || maxPx <= 0f) return androidx.compose.ui.geometry.Offset.Zero
            val old = offset
            offset = (offset - available.y).coerceAtLeast(0f)
            return androidx.compose.ui.geometry.Offset(0f, old - offset)
        }
    }
}

val LocalTitleCollapse = androidx.compose.runtime.staticCompositionLocalOf<TitleCollapse?> { null }

/**
 * A page's top: round back button, then the big title. With [collapse] (the default, for pages whose content
 * scrolls below the bar) the big title folds into a small centred one as you scroll. Pages that scroll the bar
 * itself away pass collapse = false.
 */
@Composable
fun BackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, collapse: Boolean = true) {
    val c = HqTheme.colors
    val state = LocalTitleCollapse.current?.takeIf { collapse }
    val bigPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    if (state != null) androidx.compose.runtime.DisposableEffect(state, bigPx) {
        state.users++; state.maxPx = bigPx
        onDispose { state.users--; if (state.users <= 0) { state.users = 0; state.maxPx = 0f; state.offset = 0f } }
    }
    val offset = state?.offset ?: 0f
    val frac = (offset / bigPx).coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.track)
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(22.dp))
            }
            HqText(
                title, 17.sp, FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp).alpha(((frac - 0.5f) * 2f).coerceIn(0f, 1f)),
            )
        }
        val h = with(androidx.compose.ui.platform.LocalDensity.current) { (bigPx - offset).coerceAtLeast(0f).toDp() }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().then(if (state != null) Modifier.height(h) else Modifier).clipToBounds(),
            contentAlignment = Alignment.BottomStart,
        ) {
            HqText(title, 30.sp, FontWeight.Bold, maxLines = 1, letterSpacing = (-0.6).sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp).alpha(1f - frac))
        }
    }
}

/** A row in a list card: icon, title, optional caption, and a chevron when it can be opened. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    caption: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = HqTheme.colors
    val base = Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.5f)
        .heightIn(min = 56.dp)
    val clickable = if (enabled && onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Row(
        modifier = clickable.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c.flat) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(22.dp)) }
        } else Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            HqText(title, 16.sp, FontWeight.Medium)
            if (caption != null) HqText(caption, 13.sp, color = c.muted)
        }
        if (enabled && onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(24.dp))
        }
    }
}

/** A card that says what will fill it and when. Keeps the skeleton screens honest. */
@Composable
fun EmptyCard(title: String, message: String, modifier: Modifier = Modifier, emoji: String = "") {
    // Zero-data state: a glyph on an inset disc, a 17sp title and one muted line, centred, never a bare grey sentence.
    val c = HqTheme.colors
    HqCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (emoji.isNotEmpty()) {
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(bottom = 6.dp).size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.track),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { HqText(emoji, 24.sp) }
            }
            HqText(title, 17.sp, FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            HqText(message, 15.sp, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
