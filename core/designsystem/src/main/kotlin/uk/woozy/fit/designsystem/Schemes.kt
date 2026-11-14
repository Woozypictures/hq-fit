package uk.woozy.fit.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import uk.woozy.fit.model.DEFAULT_SCHEME_KEY
import uk.woozy.fit.model.EVERFIT_SCHEME_KEY

/** The default scheme: Woozy, the shared HQ and Fit look (near-black or warm off-white, one green accent). */
const val DEFAULT_SCHEME = DEFAULT_SCHEME_KEY

class HqScheme(val key: String, val label: String)

/** One of HQ's colour schemes, as HQ defines it. [accent] is HQ's third tab colour, used as the blue. */
private class RawScheme(
    val key: String, val label: String, val dark: Boolean,
    val bg: Long, val panel: Long, val fg: Long, val muted: Long, val hair: Long,
    val ok: Long, val warn: Long, val accent: Long, val onAccent: Long,
)

// Ported from HQ's Theme.kt (Natural plus 16 more). Only the colours; font and layout are HQ Fit's own.
private val hqSchemes = listOf(
    RawScheme("hq", "Natural (HQ)", false, 0xFFFBF8F2, 0xFFFFFFFF, 0xFF1D1D1F, 0xFF625E57, 0x1A1D1D1F, 0xFF3E7A4E, 0xFFB3401A, 0xFF2F5F8A, 0xFFFFFFFF),
    RawScheme("colourful", "Colourful", false, 0xFFFFF7EC, 0xFFFFFFFF, 0xFF1D1D1F, 0xFF625E57, 0x1F000000, 0xFF1F8A57, 0xFFC92A2A, 0xFF1C7ED6, 0xFFFFFFFF),
    RawScheme("standard", "Standard", false, 0xFFF5F5F7, 0xFFFFFFFF, 0xFF1C1D22, 0xFF5E616B, 0x1F000000, 0xFF2E7D4F, 0xFFB42318, 0xFF4A6A8A, 0xFFFFFFFF),
    RawScheme("calm", "Calm", false, 0xFFF5F4F8, 0xFFFFFFFF, 0xFF2A2933, 0xFF6B6978, 0x1F000000, 0xFF4F7A6A, 0xFF9A4E4E, 0xFF5F7790, 0xFFFFFFFF),
    RawScheme("seventies", "Seventies", false, 0xFFF2E6CC, 0xFFFBF2DF, 0xFF3A2718, 0xFF6E5642, 0x1F000000, 0xFF5E6E2E, 0xFFB4431F, 0xFF2F6B66, 0xFFFFFFFF),
    RawScheme("japandi", "Japandi", false, 0xFFEEE7DA, 0xFFF7F2E8, 0xFF2A2521, 0xFF6E655B, 0x1F000000, 0xFF6F7F5F, 0xFFA8392F, 0xFF5E7280, 0xFFFFFFFF),
    RawScheme("riviera", "Riviera", false, 0xFFF3EDE1, 0xFFFBF8F1, 0xFF14233F, 0xFF5A6273, 0x1F000000, 0xFF1F4A3A, 0xFF8E2A2A, 0xFF24476E, 0xFFFFFFFF),
    RawScheme("editorial", "Editorial", false, 0xFFF3EFE6, 0xFFFBF9F4, 0xFF111111, 0xFF57534C, 0x1F000000, 0xFF2F6B45, 0xFFC8102E, 0xFF111111, 0xFFFFFFFF),
    RawScheme("notebook", "Notebook", false, 0xFFF6F0E1, 0xFFFFFDF6, 0xFF2D3142, 0xFF5F6274, 0x1F000000, 0xFF2E8A62, 0xFFC2452E, 0xFF2E7FB0, 0xFFFFFFFF),
    RawScheme("riso", "Riso", false, 0xFFF3EEE2, 0xFFFFFBF2, 0xFF1E1B3A, 0xFF55506E, 0x1F000000, 0xFF00837F, 0xFFD61F7A, 0xFF2D6FD6, 0xFFFFFFFF),
    RawScheme("solarpunk", "Solarpunk", false, 0xFFEAF6E4, 0xFFFFFFFF, 0xFF1F3A24, 0xFF557159, 0x1F000000, 0xFF2F8A3E, 0xFFC2502E, 0xFF2E86C1, 0xFFFFFFFF),
    RawScheme("bauhaus", "Bauhaus", false, 0xFFF1EDE2, 0xFFFBF9F3, 0xFF111111, 0xFF4A4740, 0x1F000000, 0xFF1F4FC1, 0xFFD42A20, 0xFF1F4FC1, 0xFFFFFFFF),
    RawScheme("chrome", "Chrome", false, 0xFFE9E4FB, 0xB3FFFFFF, 0xFF262046, 0xFF5E5884, 0x1F000000, 0xFF2E9C7A, 0xFFC2366B, 0xFF3A86E8, 0xFFFFFFFF),
    RawScheme("digital", "Digital", true, 0xFF0F1320, 0xFF171C2E, 0xFFEEF1F8, 0xFFA3ACC2, 0x1FFFFFFF, 0xFF5BE0A3, 0xFFFF8A65, 0xFF6FB2FF, 0xFF0F1320),
    RawScheme("noir", "Noir", true, 0xFF0C0B0A, 0xFF151412, 0xFFF2ECDF, 0xFFA69E8F, 0x1FFFFFFF, 0xFFA9BE8F, 0xFFE0927A, 0xFFA9BBC8, 0xFF0C0B0A),
    RawScheme("athletic", "Athletic", true, 0xFF141414, 0xFF1E1E1E, 0xFFF2F2EE, 0xFFA6A6A0, 0x1FFFFFFF, 0xFFC9DB6A, 0xFFF07A5A, 0xFF9FC9E0, 0xFF141414),
    RawScheme("arcade", "Arcade", true, 0xFF120A26, 0xFF1C1238, 0xFFF1ECFF, 0xFFB4A8D8, 0x1FFFFFFF, 0xFFA8FF3E, 0xFFFF5C8A, 0xFF3EE6FF, 0xFF120A26),
)

/**
 * Everfit's look plus the 17 HQ colour schemes. Appearance (light, dark, system) turns any light
 * scheme into a dark one the way HQ does; the four schemes that are dark by nature stay dark.
 */
object HqSchemes {
    const val FROSTED_KEY = "frosted"
    // Same order as HQ: Woozy, Everfit, Natural, Frosted glass, then the rest.
    val all: List<HqScheme> = listOf(HqScheme(DEFAULT_SCHEME, "Woozy"), HqScheme(EVERFIT_SCHEME_KEY, "Everfit")) +
        hqSchemes.take(1).map { HqScheme(it.key, it.label) } + HqScheme(FROSTED_KEY, "Frosted glass") + hqSchemes.drop(1).map { HqScheme(it.key, it.label) }

    fun colors(key: String, wantDark: Boolean): HqColors {
        if (key == DEFAULT_SCHEME) return if (wantDark) WoozyDark else WoozyLight
        if (key == FROSTED_KEY) return DarkHqColors.let {
            HqColors(dark = true, page = Color(0xFF2E2E2E), card = Color(0xFF3A3A3A), primary = Color.White, onPrimary = Color(0xFF2E2E2E),
                gradientStart = Color(0xFF3A3A3A), gradientEnd = Color(0xFF2E2E2E), onGradient = Color.White,
                ink = Color(0xFFF2F2F2), muted = Color(0xFFB4B4B4), hairline = Color(0x3DFFFFFF), track = Color(0x33FFFFFF),
                protein = it.protein, carbs = it.carbs, fat = it.fat, ok = it.ok, warn = it.warn)
        }
        if (key == EVERFIT_SCHEME_KEY) return if (wantDark) DarkHqColors else LightHqColors
        val raw = hqSchemes.firstOrNull { it.key == key } ?: return colors(DEFAULT_SCHEME, wantDark)
        return if (!raw.dark && wantDark) darkOf(raw) else build(raw)
    }

    private fun build(r: RawScheme): HqColors = assemble(
        dark = r.dark, bg = Color(r.bg), panel = Color(r.panel), fg = Color(r.fg), muted = Color(r.muted),
        hair = Color(r.hair), ok = Color(r.ok), warn = Color(r.warn), accent = Color(r.accent), onAccent = Color(r.onAccent),
    )

    /** Dark version of a light scheme: same accents lightened, dark surfaces. */
    private fun darkOf(r: RawScheme): HqColors {
        val bg = lerp(Color(0xFF0F1013), Color(r.bg), 0.06f)
        return assemble(
            dark = true, bg = bg, panel = lerp(bg, Color.White, 0.08f), fg = Color(0xFFEDEDED), muted = Color(0xFFA9A9AF),
            hair = Color(0x1FFFFFFF), ok = lerp(Color(r.ok), Color.White, 0.35f), warn = lerp(Color(r.warn), Color.White, 0.3f),
            accent = lerp(Color(r.accent), Color.White, 0.4f), onAccent = bg,
        )
    }

    private fun assemble(
        dark: Boolean, bg: Color, panel: Color, fg: Color, muted: Color, hair: Color,
        ok: Color, warn: Color, accent: Color, onAccent: Color,
    ): HqColors {
        val macros = if (dark) DarkHqColors else LightHqColors
        return HqColors(
            dark = dark,
            page = bg,
            card = panel.compositeOver(bg),
            primary = accent,
            onPrimary = onAccent,
            gradientStart = accent,
            gradientEnd = lerp(accent, Color.Black, if (dark) 0.35f else 0.25f),
            onGradient = if (accent.luminance() > 0.5f) Color(0xFF101114) else Color.White,
            ink = fg,
            muted = muted,
            hairline = hair,
            track = lerp(bg, fg, 0.10f),
            protein = macros.protein,
            carbs = macros.carbs,
            fat = macros.fat,
            ok = ok,
            warn = warn,
        )
    }
}

/** Woozy light: warm off-white page, white surfaces, deep green accent. Same values as HQ's Woozy theme. */
val WoozyLight = HqColors(
    dark = false, page = Color(0xFFF4F1EA), card = Color(0xFFFFFFFF), primary = Color(0xFF2D5A34), onPrimary = Color(0xFFFFFFFF),
    gradientStart = Color(0xFF2D5A34), gradientEnd = Color(0xFF1F4526), onGradient = Color(0xFFFFFFFF),
    ink = Color(0xFF141414), muted = Color(0xFF6B675F), hairline = Color(0x1A141414), track = Color(0xFFE6E2D8),
    protein = Color(0xFF4C8A4A), carbs = Color(0xFFB08A3E), fat = Color(0xFFA8645A), ok = Color(0xFF2D6A3A), warn = Color(0xFFB3401A), flat = true,
)

/** Woozy dark: near-black, electric lime accent. */
val WoozyDark = HqColors(
    dark = true, page = Color(0xFF0E0F0E), card = Color(0xFF181A18), primary = Color(0xFFC8F25A), onPrimary = Color(0xFF10140A),
    gradientStart = Color(0xFF2E3D14), gradientEnd = Color(0xFF1B260B), onGradient = Color(0xFFFFFFFF),
    ink = Color(0xFFF2F1EC), muted = Color(0xFF9A9A92), hairline = Color(0x1FFFFFFF), track = Color(0xFF282B28),
    protein = Color(0xFF9BD670), carbs = Color(0xFFD9B36A), fat = Color(0xFFCF9484), ok = Color(0xFF8FD18F), warn = Color(0xFFFF8A7A), flat = true,
)
