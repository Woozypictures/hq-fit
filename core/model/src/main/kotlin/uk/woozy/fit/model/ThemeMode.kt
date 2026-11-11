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
