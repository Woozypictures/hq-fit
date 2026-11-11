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
        fontSize = size,
        fontWeight = weight,
        fontFamily = GoogleSans,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFeatureSettings = "tnum"),
    )
}
