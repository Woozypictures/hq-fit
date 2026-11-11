package uk.woozy.fit.data

import android.content.Context
import uk.woozy.fit.model.DEFAULT_SCHEME_KEY
import uk.woozy.fit.model.ThemeMode

/** Remembers the light / dark / system choice. The encrypted database arrives in milestone 2; this stays plain preferences. */
class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("display", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = ThemeMode.fromKey(prefs.getString("theme", null))
        set(value) {
            prefs.edit().putString("theme", value.key).apply()
        }

    /** Colour scheme key; see the design system's HqSchemes. */
    var scheme: String
        get() = prefs.getString("scheme", null) ?: DEFAULT_SCHEME_KEY
        set(value) {
            prefs.edit().putString("scheme", value).apply()
        }
}
