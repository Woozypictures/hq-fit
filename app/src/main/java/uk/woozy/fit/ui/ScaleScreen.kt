package uk.woozy.fit.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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

        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = ui.liveKg?.toDouble() ?: weights.firstOrNull()?.kg
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(180.dp).border(3.dp, c.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HqText(shown?.let { formatOne(it) } ?: "--", 44.sp, FontWeight.Bold)
                            HqText("kg", 14.sp, color = c.muted)
                        }
                    }
                }
                HqText(if (ui.liveKg != null) (if (ui.stable) "Settled and saved" else "Settling, stay still") else ui.status, 14.sp, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                val first = weights.lastOrNull()
                val heightCm = ProfilePrefs.heightCm(context)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(
                        "Change" to if (shown != null && first != null && weights.size > 1) (if (shown - first.kg >= 0) "+" else "") + formatOne(shown - first.kg) + " kg" else "-",
                        "BMI" to if (shown != null && heightCm != null) formatOne(shown / ((heightCm / 100.0) * (heightCm / 100.0))) else "-",
                        "Weigh-ins" to weights.size.toString(),
                    ).forEach { (label, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HqText(value, 16.sp, FontWeight.SemiBold)
                            HqText(label, 12.sp, color = c.muted)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (savedAddress != null) {
                        HqPillButton("Connect ${savedName ?: "scale"}", onClick = { withPermission { session.connect(savedAddress!!) } })
                    }
                    HqPillButton(if (ui.scanning) "Scanning" else "Find scale", style = PillStyle.Outline, enabled = !ui.scanning, onClick = { withPermission { session.scan() } })
                }
                if (ui.connected) HqPillButton("Disconnect", style = PillStyle.Outline, onClick = { session.disconnect() })
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
                    Switch(checked = reminder.enabled, onCheckedChange = { saveReminder(reminder.copy(enabled = it)) })
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
            weights.take(8).forEach { w ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        HqText("${formatOne(w.kg)} kg", 15.sp, FontWeight.Medium)
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
