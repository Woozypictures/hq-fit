package uk.woozy.fit.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small preview of one scheme (page, header, card, accent) in the appearance now in use. */
@Composable
fun SchemeSwatch(
    scheme: HqScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = HqTheme.colors
    val preview = HqSchemes.colors(scheme.key, wantDark = c.dark)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .width(88.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 64.dp)
                .clip(shape)
                .background(preview.page)
                .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.hairline, shape)
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.linearGradient(listOf(preview.gradientStart, preview.gradientEnd))),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(preview.card),
                    )
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(preview.primary))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        HqText(
            scheme.label, 11.sp,
            if (selected) FontWeight.SemiBold else FontWeight.Normal,
            if (selected) c.primary else c.muted,
            textAlign = TextAlign.Center, maxLines = 1,
        )
    }
}

/** Every scheme in one scrolling row. */
@Composable
fun SchemePicker(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HqSchemes.all.forEach { scheme ->
            SchemeSwatch(scheme, selected = scheme.key == selectedKey, onClick = { onSelect(scheme.key) })
        }
    }
}
