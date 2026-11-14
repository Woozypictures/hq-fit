package uk.woozy.fit.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import uk.woozy.fit.WeighInReminder
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.WeightEntryEntity
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.designsystem.formatOne
import uk.woozy.fit.scale.ScaleSession
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.Date
import java.util.Locale

private fun scalePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun hasAll(c: Context, perms: Array<String>) = perms.all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }

/** Weight: pair the scale, type a weight in, set the weekly reminder, and see the packet log if pairing misbehaves. */
@Composable
fun ScaleScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("scale", Context.MODE_PRIVATE) }
    var savedAddress by remember { mutableStateOf(prefs.getString("addr", null)) }
    var savedName by remember { mutableStateOf(prefs.getString("name", null)) }

    val session = remember {
        ScaleSession(context.applicationContext) { kg, epochMs, stored ->
            scope.launch {
                if (db.weights().countBetween(epochMs - 60_000, epochMs + 60_000) == 0) {
                    db.weights().insert(WeightEntryEntity(epochMs = epochMs, kg = Math.round(kg * 10.0) / 10.0, source = if (stored) "import" else "scale"))
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { session.stopScan(); session.disconnect() } }
    val ui by session.ui.collectAsState()
    val weights by db.weights().observeAll().collectAsState(initial = emptyList())

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) pendingAction?.invoke()
        pendingAction = null
    }
    fun withPermission(action: () -> Unit) {
        val perms = scalePermissions()
        if (hasAll(context, perms)) action() else { pendingAction = action; permLauncher.launch(perms) }
    }

    var manual by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf(WeighInReminder.get(context)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun saveReminder(r: WeighInReminder.Setting) {
        reminder = r
        WeighInReminder.set(context, r)
        if (r.enabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackBar("Scale and weight", onBack)

        // Feelfit-style live readout: the number springs as the scale settles, the ring fills toward the goal, a status
        // pill says where the connection is, and the stats sit on inset tiles (14dp inside the 22dp card, 8dp in).
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val shown = ui.liveKg?.toDouble() ?: weights.firstOrNull()?.kg
                val first = weights.lastOrNull()
                val heightCm = ProfilePrefs.heightCm(context)
                val start = first?.kg ?: shown
                val toGoal = if (shown != null && start != null) ((start - shown) / (start - GOAL_KG).let { if (it == 0.0) 1.0 else it }).toFloat().coerceIn(0f, 1f) else 0f
                val live = ui.liveKg != null
                val breathe = androidx.compose.animation.core.rememberInfiniteTransition(label = "scale")
                val glow by breathe.animateFloat(0.25f, 0.7f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val halo = c.primary.copy(alpha = if (live && !ui.stable) glow else 0.45f)
                    androidx.compose.foundation.Canvas(Modifier.size(196.dp)) {
                        val r = size.minDimension / 2 - 2.dp.toPx()
                        for (k in 0 until 36) {
                            val ang = Math.toRadians(k * 10.0 - 90)
                            drawCircle(halo, 1.8.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x + r * kotlin.math.cos(ang).toFloat(), center.y + r * kotlin.math.sin(ang).toFloat()))
                        }
                    }
                    uk.woozy.fit.designsystem.HqRing(progress = toGoal, color = if (live && ui.stable) c.ok else c.primary, diameter = 168.dp, stroke = 8.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val kg by androidx.compose.animation.core.animateFloatAsState((shown ?: 0.0).toFloat(), androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 180f), label = "kg")
                            HqText(if (shown != null) formatOne(kg.toDouble()) else "--", 48.sp, FontWeight.Bold, letterSpacing = (-1.5).sp)
                            HqText("kg", 13.sp, color = c.muted)
                        }
                    }
                }
                // Connection status pill
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val dot = when { live && ui.stable -> c.ok; live || ui.scanning -> c.primary; ui.connected -> c.ok; else -> c.muted }
                    Row(
                        Modifier.clip(CircleShape).background(c.track).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot.copy(alpha = if (live && !ui.stable || ui.scanning) glow + 0.3f else 1f)))
                        Spacer(Modifier.size(8.dp))
                        HqText(
                            if (live) (if (ui.stable) "Settled and saved" else "Settling, stay still") else if (ui.connected) "Connected to ${savedName ?: "scale"}" else ui.status,
                            12.sp, FontWeight.Medium, c.muted, maxLines = 1,
                        )
                    }
                }
                val tiles = listOf(
                    "Change" to if (shown != null && first != null && weights.size > 1) (if (shown - first.kg >= 0) "+" else "") + formatOne(shown - first.kg) else "-",
                    "To goal" to if (shown != null) formatOne(kotlin.math.abs(shown - GOAL_KG)) else "-",
                    "BMI" to if (shown != null && heightCm != null) formatOne(shown / ((heightCm / 100.0) * (heightCm / 100.0))) else "-",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.forEach { (label, value) ->
                        Column(
                            Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(uk.woozy.fit.designsystem.WoozyTokens.Radius.tile))
                                .background(c.track.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            HqText(label, 12.sp, FontWeight.Medium, c.muted)
                            Row(verticalAlignment = Alignment.Bottom) {
                                HqText(value, 20.sp, FontWeight.Bold, letterSpacing = (-0.3).sp)
                                if (label != "BMI" && value != "-") HqText(" kg", 12.sp, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (savedAddress != null && !ui.connected) {
                        HqPillButton("Connect ${savedName ?: "scale"}", onClick = { withPermission { session.connect(savedAddress!!) } })
                    }
                    if (ui.connected) HqPillButton("Disconnect", style = PillStyle.Outline, onClick = { session.disconnect() })
                    else HqPillButton(if (ui.scanning) "Scanning" else "Find scale", style = PillStyle.Outline, enabled = !ui.scanning, onClick = { withPermission { session.scan() } })
                }
            }
        }

        if (ui.devices.isNotEmpty()) {
            SectionLabel("Nearby devices")
            HqCard(Modifier.fillMaxWidth()) {
                ui.devices.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            prefs.edit().putString("addr", d.address).putString("name", d.name).apply()
                            savedAddress = d.address; savedName = d.name
                            withPermission { session.connect(d.address) }
                        }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        HqText(d.name, 15.sp, FontWeight.Medium)
                        HqText("${d.rssi} dBm", 13.sp, color = c.muted)
                    }
                }
            }
        }

        SectionLabel("Type a weight")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Weight (kg)", manual) { manual = it }
                HqPillButton("Save weight", enabled = (manual.toDoubleOrNull() ?: 0.0) in 20.0..400.0, onClick = {
                    val kg = manual.toDouble()
                    scope.launch { db.weights().insert(WeightEntryEntity(epochMs = System.currentTimeMillis(), kg = kg, source = "manual")) }
                    manual = ""
                })
            }
        }

        SectionLabel("Weekly weigh-in reminder")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    HqText("Remind me", 16.sp, FontWeight.Medium)
                    uk.woozy.fit.designsystem.HapticSwitch(checked = reminder.enabled, onCheckedChange = { saveReminder(reminder.copy(enabled = it)) })
                }
                val days = DayOfWeek.entries
                SegmentedChoice(
                    options = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                    selectedIndex = days.indexOf(reminder.day),
                    onSelect = { saveReminder(reminder.copy(day = days[it])) },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { saveReminder(reminder.copy(hour = (reminder.hour + 23) % 24)) }) { HqText("-", 18.sp) }
                    HqText("%02d:00".format(reminder.hour), 16.sp, FontWeight.Medium)
                    TextButton(onClick = { saveReminder(reminder.copy(hour = (reminder.hour + 1) % 24)) }) { HqText("+", 18.sp) }
                }
            }
        }

        SectionLabel("Recent weights")
        HqCard(Modifier.fillMaxWidth()) {
            if (weights.isEmpty()) {
                Column(Modifier.padding(16.dp)) { HqText("Nothing yet.", 14.sp, color = c.muted) }
            }
            weights.take(8).forEachIndexed { i, w ->
                val prev = weights.getOrNull(i + 1)
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.ink.copy(alpha = if (c.dark) 0.12f else 0.08f)))
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            HqText("${formatOne(w.kg)} kg", 15.sp, FontWeight.SemiBold)
                            if (prev != null) {
                                val d = w.kg - prev.kg
                                HqText("  " + (if (d > 0) "+" else "") + formatOne(d), 13.sp, FontWeight.Medium, if (d <= 0) c.ok else c.warn)
                            }
                        }
                        HqText(SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(Date(w.epochMs)) + " · " + w.source, 12.sp, color = c.muted)
                    }
                    TextButton(onClick = { scope.launch { db.weights().delete(w.id); runCatching { uk.woozy.fit.health.HealthGateway(context.applicationContext).let { g -> if (g.available) g.deleteWeights(listOf(w.id)) } } } }) { HqText("Delete", 13.sp, color = c.muted) }
                }
            }
        }

        SectionLabel("Packet log")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HqText("If the scale does not pair, send this log to be fixed. Weight only; body composition is not read.", 12.sp, color = c.muted)
                Spacer(Modifier.height(4.dp))
                if (ui.log.isEmpty()) HqText("Empty.", 12.sp, color = c.muted)
                ui.log.takeLast(60).forEach { HqText(it, 11.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}


/**
 * Feelfit-style weigh-in from Home: while Home is open and a scale is paired, wait for it in the background
 * (Bluetooth auto-connect costs nothing until the scale wakes). Step on, and the reading is saved and announced.
 */
@Composable
fun HomeScaleListener(db: HqFitDatabase, onWeighed: (Double) -> Unit) {
    val context = LocalContext.current
    val addr = remember { context.getSharedPreferences("scale", Context.MODE_PRIVATE).getString("addr", null) } ?: return
    if (!hasAll(context, scalePermissions())) return
    val scope = rememberCoroutineScope()
    val session = remember {
        ScaleSession(context.applicationContext) { kg, epochMs, stored ->
            scope.launch {
                if (db.weights().countBetween(epochMs - 60_000, epochMs + 60_000) == 0) {
                    val v = Math.round(kg * 10.0) / 10.0
                    db.weights().insert(WeightEntryEntity(epochMs = epochMs, kg = v, source = if (stored) "import" else "scale"))
                    if (!stored) onWeighed(v)
                }
            }
        }
    }
    val ui by session.ui.collectAsState()
    // (Re)arm whenever the scale is not connected: after a reading the scale sleeps and drops the link.
    LaunchedEffect(ui.connected) { if (!ui.connected) runCatching { session.connect(addr, auto = true) } }
    DisposableEffect(Unit) { onDispose { session.disconnect() } }
}
