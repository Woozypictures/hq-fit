package uk.woozy.fit.ui

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.clickable

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
    frosted: Boolean,
    onFrosted: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDesign: () -> Unit,
    onScale: () -> Unit,
    onHealth: () -> Unit,
    onBackup: () -> Unit,
    onProfile: () -> Unit,
    onWorkoutSettings: () -> Unit,
    onHqConnection: () -> Unit,
    onTargets: () -> Unit,
) {
    val c = HqTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
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
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    HqText("Frosted glass", 16.sp, FontWeight.Medium)
                    HqText("Dark page with translucent cards, as in HQ", 12.sp, color = c.muted)
                }
                androidx.compose.material3.Switch(checked = frosted, onCheckedChange = onFrosted)
            }
            ListRow(Icons.Rounded.Palette, "Design components", "Every component, in this theme", onClick = onDesign)
        }

        SectionLabel("Body")
        HqCard(Modifier.fillMaxWidth()) {
            ListRow(Icons.Rounded.Flag, "Targets", "Energy, macros, steps, water, nutrients", onClick = onTargets)
            ListRow(Icons.Rounded.MonitorWeight, "Scale and weight", "Pair your scale, type a weight, weekly reminder", onClick = onScale)
            ListRow(Icons.Rounded.FavoriteBorder, "Health Connect", "Permissions, steps, sleep, sync", onClick = onHealth)
            ListRow(Icons.Rounded.Backup, "Backup and restore", "Encrypted backups, restore, CSV export", onClick = onBackup)
        }

        SectionLabel("More")
        HqCard(Modifier.fillMaxWidth()) {
            ListRow(Icons.Rounded.Person, "Profile", "Name and height", onClick = onProfile)
            ListRow(Icons.Rounded.FitnessCenter, "Workout", "Weight step, rest timer", onClick = onWorkoutSettings)
            ListRow(Icons.Rounded.Link, "HQ connection", "What HQ can read from this app", onClick = onHqConnection)
            ListRow(Icons.Rounded.SwapVert, "Import and export", "Cronometer import, CSV, backups", onClick = onBackup)
        }

        SectionLabel("Notifications")
        HqCard(Modifier.fillMaxWidth()) {
            val context = LocalContext.current
            var checkIn by remember { mutableStateOf(uk.woozy.fit.CheckInReminder.enabled(context)) }
            val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    HqText("Sunday check-in reminder", 16.sp, FontWeight.Medium)
                    HqText("Sunday 18:00, at most once a day", 12.sp, color = c.muted)
                }
                androidx.compose.material3.Switch(checked = checkIn, onCheckedChange = {
                    checkIn = it
                    uk.woozy.fit.CheckInReminder.setEnabled(context, it)
                    if (it && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            ListRow(Icons.Rounded.MonitorWeight, "Weigh-in reminder", "Set the day and time in Scale and weight", onClick = onScale)
        }

        SectionLabel("About")
        HqCard(Modifier.fillMaxWidth()) {
            AboutBlock("HQ Fit")
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HqText("Licence AGPL-3.0 · source at github.com/Woozypictures/hq-fit", 13.sp, color = c.muted)
                HqText("Your data stays on this phone.", 13.sp, color = c.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
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

/** Your social links, shared by every Woozypictures app. Each opens in its own app when installed. */
val SOCIALS = listOf(
    "https://www.instagram.com/woozypictures/",
    "https://www.youtube.com/@WoozyPictures",
    "https://x.com/WoozyPictures",
)

@Composable
fun AboutBlock(appName: String) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HqText(appName, 16.sp, FontWeight.SemiBold)
        HqText("Created by Woozypictures", 14.sp)
        HqText("App version: $version", 14.sp, color = c.muted)
        SOCIALS.forEach { url ->
            HqText(
                url, 14.sp, FontWeight.Medium, c.primary,
                modifier = Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(vertical = 6.dp),
            )
        }
    }
}
