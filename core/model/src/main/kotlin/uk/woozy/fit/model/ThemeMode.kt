package uk.woozy.fit.model

/** Light is the default, as in Everfit. System follows the phone. */
enum class ThemeMode(val key: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

/** Key of the default colour scheme (the Woozy look). The schemes themselves live in the design system. */
const val DEFAULT_SCHEME_KEY = "woozy"

/** The original Everfit-style look, still selectable. */
const val EVERFIT_SCHEME_KEY = "everfit"
