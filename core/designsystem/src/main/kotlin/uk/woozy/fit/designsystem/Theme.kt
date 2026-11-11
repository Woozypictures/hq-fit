package uk.woozy.fit.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour roles for HQ Fit. Light is Everfit's look (pale page, white cards, electric blue);
 * dark is derived from it the way HQ derives its dark schemes. Macro colours follow Cronometer.
 */
@Immutable
class HqColors(
    val dark: Boolean,
    val page: Color,
    val card: Color,
    val primary: Color,
    val onPrimary: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    /** Text and pill colour that reads on the blue gradient. White on most schemes, near-black on the light-accent dark ones. */
    val onGradient: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val track: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val ok: Color,
    val warn: Color,
)

val LightHqColors = HqColors(
    dark = false,
    page = Color(0xFFF6F7FC),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF2355FB),
    onPrimary = Color(0xFFFFFFFF),
    gradientStart = Color(0xFF2355FB),
    gradientEnd = Color(0xFF0048E2),
    onGradient = Color(0xFFFFFFFF),
    ink = Color(0xFF1C1D22),
    muted = Color(0xFF6B6F80),
    hairline = Color(0x1A1C1D22),
    track = Color(0xFFE8EBF5),
    protein = Color(0xFF3BA55D),
    carbs = Color(0xFF1FA8A0),
    fat = Color(0xFF8E5BD9),
    ok = Color(0xFF2E7D4F),
    warn = Color(0xFFB42318),
)

val DarkHqColors = HqColors(
    dark = true,
    page = Color(0xFF14151B),
    card = Color(0xFF1F2129),
    primary = Color(0xFF6E8CFF),
    onPrimary = Color(0xFF0B1020),
    gradientStart = Color(0xFF2A4FD8),
    gradientEnd = Color(0xFF0B3BB8),
    onGradient = Color(0xFFFFFFFF),
    ink = Color(0xFFF1F2F6),
    muted = Color(0xFFA0A4B5),
    hairline = Color(0x24FFFFFF),
    track = Color(0xFF2C2F3A),
    protein = Color(0xFF5CC27E),
    carbs = Color(0xFF3CC4BB),
    fat = Color(0xFFA985EA),
    ok = Color(0xFF6CC48D),
    warn = Color(0xFFFF8A7A),
)

val LocalHqColors = staticCompositionLocalOf { LightHqColors }

/** True for the frosted glass style: dark page, translucent gradient cards. */
val LocalFrosted = staticCompositionLocalOf { false }

object HqTheme {
    val colors: HqColors
        @Composable @ReadOnlyComposable get() = LocalHqColors.current
}

/**
 * [darkTheme] is what the appearance setting asks for. Schemes that are dark by nature (Digital, Noir,
 * Athletic, Arcade) stay dark either way, as in HQ; read [HqColors.dark] for what was actually applied.
 */
@Composable
fun HqFitTheme(darkTheme: Boolean, scheme: String = DEFAULT_SCHEME, frosted: Boolean = false, content: @Composable () -> Unit) {
    val picked = HqSchemes.colors(scheme, darkTheme || frosted)
    val c = if (!frosted) picked else HqColors(
        dark = true, page = Color(0xFF2E2E2E), card = Color(0xFF3A3A3A), primary = picked.primary, onPrimary = picked.onPrimary,
        gradientStart = picked.gradientStart, gradientEnd = picked.gradientEnd, onGradient = picked.onGradient,
        ink = Color(0xFFF2F2F2), muted = Color(0xFFB4B4B4), hairline = Color(0x3DFFFFFF), track = Color(0x33FFFFFF),
        protein = picked.protein, carbs = picked.carbs, fat = picked.fat, ok = picked.ok, warn = picked.warn,
    )
    val materialScheme = if (c.dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            background = c.page, onBackground = c.ink,
            surface = c.card, onSurface = c.ink,
            surfaceVariant = c.track, onSurfaceVariant = c.muted,
            outline = c.hairline, outlineVariant = c.hairline,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            background = c.page, onBackground = c.ink,
            surface = c.card, onSurface = c.ink,
            surfaceVariant = c.track, onSurfaceVariant = c.muted,
            outline = c.hairline, outlineVariant = c.hairline,
        )
    }
    CompositionLocalProvider(LocalHqColors provides c, LocalFrosted provides frosted) {
        MaterialTheme(colorScheme = materialScheme, typography = HqTypography, content = content)
    }
}
