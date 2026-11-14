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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import uk.woozy.fit.designsystem.HqSchemes
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SchemePicker
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.model.ThemeMode

/** Same structure as Woozy HQ's Settings: a list of grouped pages (Display, You and targets, ...), each opening on its own, then About. */
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
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    androidx.activity.compose.BackHandler(open.isNotEmpty()) { open = "" }
    val titles = mapOf("general" to "Display", "profile" to "You and targets", "connections" to "Data and connections", "notifications" to "Notifications", "advanced" to "Advanced")

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { if (open.isNotEmpty()) open = "" else onBack() },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(8.dp))
            HqText(titles[open] ?: "Settings", 26.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            @Composable fun Section(key: String, content: @Composable () -> Unit) {
                if (open.isEmpty()) {
                    HqCard(Modifier.fillMaxWidth(), onClick = { open = key }) {
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            HqText(titles.getValue(key), 16.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            HqText("\u203A", 22.sp, color = c.muted)
                        }
                    }
                } else if (open == key) {
                    HqCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
                }
            }

            Section("general") {
                HqText("Appearance", 13.sp, FontWeight.Medium, c.muted)
                val modes = ThemeMode.entries
                SegmentedChoice(
                    options = listOf("Light", "Dark", "System"),
                    selectedIndex = modes.indexOf(themeMode),
                    onSelect = { onThemeMode(modes[it]) },
                )
                HqText("Colour scheme", 13.sp, FontWeight.Medium, c.muted)
                SchemePicker(
                    selectedKey = if (frosted) HqSchemes.FROSTED_KEY else scheme,
                    onSelect = { k -> if (k == HqSchemes.FROSTED_KEY) onFrosted(true) else { onFrosted(false); onScheme(k) } },
                )
            }
            Section("profile") {
                HqCardlessRows {
                    ListRow(Icons.Rounded.Person, "Profile", "Name and height", onClick = onProfile)
                    ListRow(Icons.Rounded.Flag, "Targets", "Energy, macros, steps, water, nutrients", onClick = onTargets)
                    ListRow(Icons.Rounded.FitnessCenter, "Workout", "Weight step, rest timer", onClick = onWorkoutSettings)
                }
            }
            Section("connections") {
                HqCardlessRows {
                    ListRow(Icons.Rounded.MonitorWeight, "Scale and weight", "Pair your scale, type a weight, weekly reminder", onClick = onScale)
                    ListRow(Icons.Rounded.FavoriteBorder, "Health Connect", "Permissions, steps, sleep, sync", onClick = onHealth)
                    ListRow(Icons.Rounded.Backup, "Backup, restore and import", "Encrypted backups, restore, CSV export, Cronometer import", onClick = onBackup)
                    ListRow(Icons.Rounded.Link, "HQ connection", "What HQ can read from this app", onClick = onHqConnection)
                }
            }
            Section("notifications") {
                val context = LocalContext.current
                var checkIn by remember { mutableStateOf(uk.woozy.fit.CheckInReminder.enabled(context)) }
                val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        HqText("Sunday check-in reminder", 16.sp, FontWeight.Medium)
                        HqText("Sunday 18:00, at most once a day", 12.sp, color = c.muted)
                    }
                    androidx.compose.material3.Switch(checked = checkIn, onCheckedChange = {
                        checkIn = it
                        uk.woozy.fit.CheckInReminder.setEnabled(context, it)
                        if (it && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
                HqCardlessRows { ListRow(Icons.Rounded.MonitorWeight, "Weigh-in reminder", "Set the day and time in Scale and weight", onClick = onScale) }
            }
            Section("advanced") {
                val context = LocalContext.current
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
                HqText("Version $version", 14.sp, FontWeight.Medium)
                HqCardlessRows { ListRow(Icons.Rounded.Palette, "Design components", "Every component, in this theme", onClick = onDesign) }
            }
            if (open.isEmpty()) {
                HqCard(Modifier.fillMaxWidth()) { AboutBlock("Woozy Fit", "hq-fit") }
            }
        }
    }
}

/** Rows that already carry their own padding, shown inside a settings page card. */
@Composable
private fun HqCardlessRows(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 0.dp)) { content() }
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

/** About, identical in HQ and Woozy Fit: name, creator, version, socials, licence line. */
@Composable
fun AboutBlock(appName: String, repo: String) {
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
        HqText("Licence AGPL-3.0 \u00b7 source at github.com/Woozypictures/$repo", 13.sp, color = c.muted)
        HqText("Your data stays with you.", 13.sp, color = c.muted)
    }
}
