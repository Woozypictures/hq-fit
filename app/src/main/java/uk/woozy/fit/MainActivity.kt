package uk.woozy.fit

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import uk.woozy.fit.designsystem.HqFitTheme
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.model.ThemeMode
import uk.woozy.fit.ui.HqFitRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as HqFitApplication).container.themeSettings
        setContent {
            var mode by remember { mutableStateOf(settings.mode) }
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            HqFitTheme(darkTheme = dark) {
                SystemBarIcons(dark)
                Box(Modifier.fillMaxSize().background(HqTheme.colors.page)) {
                    HqFitRoot(themeMode = mode, onThemeMode = {
                        mode = it
                        settings.mode = it
                    })
                }
            }
        }
    }
}

/** Dark icons on the light page, light icons on the dark page, whatever the phone's own setting is. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}
