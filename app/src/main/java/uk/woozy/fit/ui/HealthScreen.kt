package uk.woozy.fit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.health.HealthGateway
import uk.woozy.fit.health.HealthSnapshot
import uk.woozy.fit.health.HealthSync
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Health Connect: permissions, what it says about today, and the sync that sends weights and workouts out. */
@Composable
fun HealthScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hc = remember { HealthGateway(context.applicationContext) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var snap by remember { mutableStateOf<HealthSnapshot?>(null) }
    var status by remember { mutableStateOf(HealthSync.lastStatus(context)) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refresh() {
        if (!hc.available) return
        granted = runCatching { hc.granted() }.getOrDefault(emptySet())
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        snap = runCatching { hc.snapshot(start, Instant.now()) }.getOrNull()
    }
    LaunchedEffect(Unit) { refresh() }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { refresh() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackBar("Health Connect", onBack)

        if (!hc.available) {
            HqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HqText("Health Connect is not ready", 16.sp, FontWeight.Medium)
                    HqText("Install or update Health Connect from the Play Store (on Android 14 it is built in), then come back.", 13.sp, color = c.muted)
                }
            }
            return@Column
        }

        val missing = HealthGateway.allPermissions - granted
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText(if (missing.isEmpty()) "Access granted" else "${granted.size} of ${HealthGateway.allPermissions.size} permissions granted", 16.sp, FontWeight.Medium)
                HqText("Read: steps, sleep, resting heart rate, active calories, weight. Write: weight, workouts, and (later) food and water.", 13.sp, color = c.muted)
                if (missing.isNotEmpty()) HqPillButton("Grant access", onClick = { launcher.launch(HealthGateway.allPermissions) })
            }
        }

        SectionLabel("Today from Health Connect")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val s = snap
                fun row(label: String, value: String) = label to value
                listOf(
                    row("Steps", s?.steps?.let { formatWhole(it.toDouble()) } ?: "no data"),
                    row("Last sleep", s?.sleepMinutes?.let { "${it / 60} h ${it % 60} min" } ?: "no data"),
                    row("Resting heart rate", s?.restingHr?.let { "$it bpm" } ?: "no data"),
                    row("Active calories", s?.activeKcal?.let { formatWhole(it) + " kcal" } ?: "no data"),
                ).forEach { (l, v) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HqText(l, 15.sp); HqText(v, 15.sp, FontWeight.Medium)
                    }
                }
            }
        }

        SectionLabel("Sync")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HqText(status, 13.sp, color = c.muted)
                HqPillButton(if (busy) "Syncing" else "Sync now and import weights", style = PillStyle.Outline, enabled = !busy, onClick = {
                    busy = true
                    scope.launch { status = HealthSync.run(context, db, hc); busy = false; refresh() }
                })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
