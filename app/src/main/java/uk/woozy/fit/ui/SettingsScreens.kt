package uk.woozy.fit.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.ComponentGallery
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SchemePicker
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.model.ThemeMode

/** Settings skeleton, grouped as in the spec. The theme switch works now; the rest light up with their milestones. */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    scheme: String,
    onScheme: (String) -> Unit,
    onBack: () -> Unit,
    onDesign: () -> Unit,
    onScale: () -> Unit,
    onHealth: () -> Unit,
) {
    val c = HqTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackBar("Settings", onBack)

        SectionLabel("Display")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HqText("Appearance", 16.sp, FontWeight.Medium)
                val modes = ThemeMode.entries
                SegmentedChoice(
                    options = listOf("Light", "Dark", "System"),
                    selectedIndex = modes.indexOf(themeMode),
                    onSelect = { onThemeMode(modes[it]) },
                )
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HqText("Colour scheme", 16.sp, FontWeight.Medium)
                SchemePicker(selectedKey = scheme, onSelect = onScheme)
            }
            ListRow(Icons.Rounded.Palette, "Design components", "Every component, in this theme", onClick = onDesign)
        }

        SectionLabel("Body")
        HqCard(Modifier.fillMaxWidth()) {
            ListRow(Icons.Rounded.MonitorWeight, "Scale and weight", "Pair your scale, type a weight, weekly reminder", onClick = onScale)
            ListRow(Icons.Rounded.FavoriteBorder, "Health Connect", "Permissions, steps, sleep, sync", onClick = onHealth)
        }

        SectionLabel("Coming")
        HqCard(Modifier.fillMaxWidth()) {
            ListRow(Icons.Rounded.Person, "Profile", "Units and height · milestone 5", enabled = false)
            ListRow(Icons.Rounded.Flag, "Targets", "Energy, macros, nutrients · milestone 5", enabled = false)
            ListRow(Icons.Rounded.FitnessCenter, "Workout", "Rest time, weight steps, RPE · milestone 4", enabled = false)
            ListRow(Icons.Rounded.Link, "HQ connection", "Share a summary with HQ · milestone 8", enabled = false)
            ListRow(Icons.Rounded.Backup, "Backup and restore", "Encrypted nightly backups · milestone 9", enabled = false)
            ListRow(Icons.Rounded.SwapVert, "Import and export", "Cronometer, Everfit, CSV · milestone 9", enabled = false)
        }

        SectionLabel("About")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HqText("HQ Fit ${versionName()}", 16.sp, FontWeight.Medium)
                HqText("Licence AGPL-3.0 · source at github.com/Woozypictures/hq-fit", 13.sp, color = c.muted)
                HqText("Your data stays on this phone.", 13.sp, color = c.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@SuppressLint("QueryPermissionsNeeded")
@Suppress("DEPRECATION")
@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }
}

/** The component gallery, scrollable. Same page CI screenshots. */
@Composable
fun DesignScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Design components", onBack) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ComponentGallery() }
    }
}
