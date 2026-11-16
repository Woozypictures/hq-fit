package uk.woozy.fit.ui

import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Notifications
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

/** Settings, GymMane style: search at the top, then Your app / You and targets / Data and help; each row opens its own page. */
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
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    androidx.activity.compose.BackHandler(open.isNotEmpty() || query.isNotEmpty()) { if (open.isNotEmpty()) open = "" else query = "" }
    val titles = mapOf("general" to "Appearance", "notifications" to "Notifications", "advanced" to "About and design", "home" to "Home")

    // Every row Settings can show, with extra words so search finds the setting inside each page.
    data class Entry(val group: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val words: String, val go: () -> Unit)
    val entries = listOf(
        Entry("Your app", Icons.Rounded.Palette, "Appearance", "Light or dark, colour scheme", "theme dark light system colour color scheme frosted glass look", { open = "general" }),
        Entry("Your app", Icons.Rounded.FitnessCenter, "Workout", "Rest, weight step, countdown, bar", "rest timer weight step countdown get ready barbell bar plates keypad warm-up steps", onWorkoutSettings),
        Entry("Your app", androidx.compose.material.icons.Icons.Rounded.Notifications, "Notifications", "Sunday check-in, weigh-in reminder", "reminder check-in sunday weigh-in alert", { open = "notifications" }),
        Entry("Home and extras", Icons.Rounded.SwapVert, "Home", "Which cards show on Today, and their order", "home today cards order rearrange hide show tiles more preview", { open = "home" }),
        Entry("You and targets", Icons.Rounded.Person, "Profile", "Name and height", "name height age you", onProfile),
        Entry("You and targets", Icons.Rounded.Flag, "Targets", "Energy, macros, steps, water, nutrients", "calories kcal energy macros protein carbs fat fibre steps water nutrients goals", onTargets),
        Entry("You and targets", Icons.Rounded.MonitorWeight, "Scale and weight", "Pair your scale, type a weight, reminder", "scale pair bluetooth weight weigh-in body fat kamtron", onScale),
        Entry("Data and help", Icons.Rounded.FavoriteBorder, "Health Connect", "Permissions, steps, sleep, sync", "health connect samsung watch steps sleep sync permissions", onHealth),
        Entry("Data and help", Icons.Rounded.Backup, "Backup, restore and import", "Encrypted backups, CSV export, Cronometer import", "backup restore export csv import cronometer everfit drive", onBackup),
        Entry("Data and help", Icons.Rounded.Link, "HQ connection", "What HQ can read from this app", "hq connection widget desktop share", onHqConnection),
        Entry("Data and help", Icons.Rounded.Palette, "About and design", "Version, socials, design components", "about version socials instagram youtube design components gallery", { open = "advanced" }),
    )

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            BackBar(titles[open] ?: "Settings", { if (open.isNotEmpty()) open = "" else onBack() })
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (open.isEmpty()) {
                androidx.compose.material3.OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { HqText("Search settings", 15.sp, color = c.muted) },
                    leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, contentDescription = null, tint = c.muted) },
                    trailingIcon = {
                        if (query.isNotEmpty()) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = "Clear search", tint = c.muted,
                            modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button) { query = "" }.padding(8.dp))
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                val q = query.trim().lowercase()
                if (q.isNotEmpty()) {
                    val hits = entries.filter { e -> q.split(' ').filter { it.isNotBlank() }.all { w -> "${e.title} ${e.sub} ${e.words}".lowercase().contains(w) } }
                    if (hits.isEmpty()) HqText("Nothing matches \u201c$query\u201d.", 14.sp, color = c.muted, modifier = Modifier.padding(8.dp))
                    else HqCard(Modifier.fillMaxWidth()) { Column { hits.forEach { e -> ListRow(e.icon, e.title, "${e.group} \u00b7 ${e.sub}", onClick = { query = ""; e.go() }) } } }
                } else {
                    entries.groupBy { it.group }.forEach { (group, rows) ->
                        HqText(group.uppercase(), 12.sp, FontWeight.SemiBold, c.muted, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                        HqCard(Modifier.fillMaxWidth()) { Column { rows.forEach { e -> ListRow(e.icon, e.title, e.sub, onClick = e.go) } } }
                    }
                    HqCard(Modifier.fillMaxWidth()) { AboutBlock("Woozy Fit", "hq-fit") }
                }
            }
            if (open == "general") HqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HqText("Theme", 13.sp, FontWeight.Medium, c.muted)
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
            }
            if (open == "notifications") HqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val context = LocalContext.current
                    var checkIn by remember { mutableStateOf(uk.woozy.fit.CheckInReminder.enabled(context)) }
                    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            HqText("Sunday check-in reminder", 16.sp, FontWeight.Medium)
                            HqText("Sunday 18:00, at most once a day", 12.sp, color = c.muted)
                        }
                        uk.woozy.fit.designsystem.HapticSwitch(checked = checkIn, onCheckedChange = {
                            checkIn = it
                            uk.woozy.fit.CheckInReminder.setEnabled(context, it)
                            if (it && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        })
                    }
                    HqCardlessRows { ListRow(Icons.Rounded.MonitorWeight, "Weigh-in reminder", "Set the day and time in Scale and weight", onClick = onScale) }
                }
            }
            if (open == "home") HomeCardsPage()
            if (open == "advanced") {
                HqCard(Modifier.fillMaxWidth()) { AboutBlock("Woozy Fit", "hq-fit") }
                HqCard(Modifier.fillMaxWidth()) { HqCardlessRows { ListRow(Icons.Rounded.Palette, "Design components", "Every component, in this theme", onClick = onDesign) } }
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
        HqText(
            "Licence AGPL-3.0 \u00b7 source at github.com/Woozypictures/$repo", 13.sp, color = c.muted,
            modifier = Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Woozypictures/$repo")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 4.dp),
        )
        HqText("Your data stays with you.", 13.sp, color = c.muted)
    }
}

/**
 * Settings > Home (GymMane style): a live mini preview of Today on the left, and the cards to show, hide and move
 * on the right (or below on a phone). Changes save straight away and show next time Today opens.
 */
@Composable
private fun HomeCardsPage() {
    val c = HqTheme.colors
    val context = LocalContext.current
    val prefs = rememberCardPrefs("today", TODAY_CARDS)
    var tiles by remember { mutableStateOf(showTodayTiles(context)) }
    val shown = prefs.order.filter { it !in prefs.hidden }
    val heights = mapOf("workout" to 46, "energy" to 34, "nutrients" to 22, "water" to 18, "steps" to 18, "tasks" to 26, "checkin" to 24)
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Mini phone with the cards as blocks, in order.
        Column(
            Modifier.width(118.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(c.page).border(1.dp, c.hairline, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.width(52.dp).height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(c.ink.copy(alpha = 0.7f)))
            shown.forEach { k ->
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().height((heights[k] ?: 20).dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                        .background(if (k == "workout") c.primary.copy(alpha = 0.55f) else c.card),
                )
            }
            if (tiles) androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(18.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(c.primary.copy(alpha = 0.25f))) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HqText("Home cards", 13.sp, FontWeight.SemiBold, c.muted)
            prefs.order.forEachIndexed { i, k ->
                val on = k !in prefs.hidden
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    HqText(TODAY_CARD_NAMES[k] ?: k, 14.sp, FontWeight.Medium, if (on) c.ink else c.muted, Modifier.weight(1f), maxLines = 2)
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp, "Move ${TODAY_CARD_NAMES[k]} up", tint = if (i > 0) c.ink else c.track,
                        modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(enabled = i > 0, role = androidx.compose.ui.semantics.Role.Button) { prefs.saveOrder(prefs.order.toMutableList().apply { add(i - 1, removeAt(i)) }) }.padding(4.dp),
                    )
                    uk.woozy.fit.designsystem.HapticSwitch(checked = on, onCheckedChange = { prefs.saveHidden(if (it) prefs.hidden - k else prefs.hidden + k) })
                }
            }
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                HqText("More tiles at the bottom", 14.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                uk.woozy.fit.designsystem.HapticSwitch(checked = tiles, onCheckedChange = { tiles = it; setShowTodayTiles(context, it) })
            }
        }
    }
    HqText("You can also long-press a card on Today to drag it.", 12.sp, color = c.muted)
}
