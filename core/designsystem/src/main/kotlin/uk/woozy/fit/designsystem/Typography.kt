package uk.woozy.fit.designsystem

import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Google Sans (SIL Open Font License 1.1), bundled so it renders the same on every phone. Licence: licenses/GoogleSans-OFL.txt. */
val GoogleSans = FontFamily(
    Font(R.font.googlesans_regular, FontWeight.Normal),
    Font(R.font.googlesans_medium, FontWeight.Medium),
    Font(R.font.googlesans_semibold, FontWeight.SemiBold),
    Font(R.font.googlesans_bold, FontWeight.Bold),
)

private fun TextStyle.gs(): TextStyle = copy(fontFamily = GoogleSans, fontFeatureSettings = "tnum")

internal val HqTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.gs(), displayMedium = t.displayMedium.gs(), displaySmall = t.displaySmall.gs(),
        headlineLarge = t.headlineLarge.gs(), headlineMedium = t.headlineMedium.gs(), headlineSmall = t.headlineSmall.gs(),
        titleLarge = t.titleLarge.gs(), titleMedium = t.titleMedium.gs(), titleSmall = t.titleSmall.gs(),
        bodyLarge = t.bodyLarge.gs(), bodyMedium = t.bodyMedium.gs(), bodySmall = t.bodySmall.gs(),
        labelLarge = t.labelLarge.gs(), labelMedium = t.labelMedium.gs(), labelSmall = t.labelSmall.gs(),
    )
}


/**
 * One type scale for both Woozy apps (Apple-style steps): 11 micro, 13 caption, 15 body, 17 headline, 20, 24, 28, 34,
 * then display sizes as given. Every size passed in snaps to the nearest step, so both apps share the same rhythm.
 */
internal fun snapType(size: TextUnit): TextUnit {
    if (!size.isSp) return size
    val v = size.value
    val s = when {
        v <= 11.5f -> 11f; v <= 13.9f -> 13f; v <= 15.9f -> 15f; v <= 18.5f -> 17f
        v <= 21.9f -> 20f; v <= 25.9f -> 24f; v <= 30.9f -> 28f; v <= 36.9f -> 34f; else -> v
    }
    return s.sp
}

/** Three weights per screen, Apple-style: Regular, Semibold, Bold. Medium and lighter fold into Regular. */
internal fun snapWeight(w: FontWeight): FontWeight = when {
    w.weight >= 650 -> FontWeight.Bold
    w.weight >= 550 -> FontWeight.SemiBold
    else -> FontWeight.Normal
}

/** The one text composable for HQ Fit: Google Sans with tabular figures, so numbers line up. */
@Composable
fun HqText(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = HqTheme.colors.ink,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = snapType(size),
        fontWeight = snapWeight(weight),
        fontFamily = GoogleSans,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFeatureSettings = "tnum"),
    )
}
