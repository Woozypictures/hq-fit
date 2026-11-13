package uk.woozy.fit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

/** Back arrow and title, for Settings and the design gallery. */
@Composable
fun BackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = HqTheme.colors.ink,
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        HqText(title, 22.sp, FontWeight.Bold)
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
        Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
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
fun EmptyCard(title: String, message: String, modifier: Modifier = Modifier) {
    HqCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HqText(title, 16.sp, FontWeight.SemiBold)
            HqText(message, 14.sp, color = HqTheme.colors.muted)
        }
    }
}
