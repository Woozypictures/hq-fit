package uk.woozy.fit.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.woozy.fit.AppContainer
import uk.woozy.fit.BackupRunner
import uk.woozy.fit.data.backup.Backup
import uk.woozy.fit.data.backup.CsvExport
import uk.woozy.fit.data.backup.InvalidBackupException
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel

/** Backups, restore and CSV export. */
@Composable
fun BackupScreen(db: HqFitDatabase, container: AppContainer, onBack: () -> Unit) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(BackupRunner.lastStatus(context)) }
    var nightly by remember { mutableStateOf(BackupRunner.nightlyOn(context)) }
    var folder by remember { mutableStateOf(BackupRunner.folder(context)) }
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(message) { if (message != null) scrollState.animateScrollTo(0) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            BackupRunner.setFolder(context, uri)
            folder = uri
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    val pickCronometer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
                val n = uk.woozy.fit.data.imports.CronometerImport.import(db, text, System.currentTimeMillis())
                if (n == 0) "Nothing new: those days are already imported" else "Imported $n days from Cronometer"
            } catch (e: IllegalArgumentException) {
                e.message
            } catch (e: Exception) {
                "Import failed: ${e.javaClass.simpleName}"
            }
        }
    }

    var pendingCsv by remember { mutableStateOf<(suspend () -> String)?>(null) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val make = pendingCsv
        if (uri != null && make != null) scope.launch {
            val text = withContext(Dispatchers.IO) { make() }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            message = "Saved"
        }
    }
    fun export(name: String, make: suspend () -> String) { pendingCsv = make; saveCsv.launch(name) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackBar("Backup and restore", onBack)
        message?.let { HqCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { HqText(it, 14.sp, FontWeight.Medium) } } }

        RecoveryKeyCard(container)

        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText("Encrypted backups", 16.sp, FontWeight.Medium)
                HqText("A backup is your whole database, still encrypted. It can only be opened with your recovery key, so keep that key safe: without it a backup is useless.", 13.sp, color = c.muted)
                HqText(status, 13.sp)
                HqPillButton(if (busy) "Backing up" else "Back up now", enabled = !busy, onClick = {
                    busy = true
                    scope.launch { status = withContext(Dispatchers.IO) { BackupRunner.run(context, container) }; busy = false }
                })
            }
        }

        SectionLabel("Nightly backup")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HqText("Back up every night", 16.sp, FontWeight.Medium)
                        HqText("Around 03:30. Keeps the last seven on the phone.", 12.sp, color = c.muted)
                    }
                    Switch(checked = nightly, onCheckedChange = { nightly = it; BackupRunner.setNightly(context, it) })
                }
                HqText(folder?.let { "Copies go to the folder you chose." } ?: "Choose a folder (Google Drive, a USB drive, any folder) so a copy survives losing the phone.", 13.sp, color = c.muted)
                HqPillButton(if (folder == null) "Choose folder" else "Change folder", style = PillStyle.Outline, onClick = { pickFolder.launch(null) })
            }
        }

        SectionLabel("Restore")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText("Restoring replaces everything on this phone. Afterwards the app asks for the recovery key that belongs to the backup.", 13.sp, color = c.muted)
                HqPillButton("Restore from a backup file", style = PillStyle.Outline, onClick = { pickRestore.launch(arrayOf("*/*")) })
            }
        }

        SectionLabel("Import from Cronometer")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText("Choose the daily summary CSV from Cronometer. Each logged day becomes one entry with that day's totals. Safe to run twice.", 13.sp, color = c.muted)
                HqPillButton("Choose daily summary file", style = PillStyle.Outline, onClick = { pickCronometer.launch(arrayOf("*/*")) })
            }
        }

        SectionLabel("Import Cronometer custom foods")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText("Adds the 65 custom foods from your Cronometer account (shop and restaurant items) with their label figures. Where Cronometer had no weight, one serving counts as a single unit. Safe to run twice.", 13.sp, color = c.muted)
                HqPillButton("Import custom foods", style = PillStyle.Outline, onClick = {
                    scope.launch {
                        message = try {
                            val text = withContext(Dispatchers.IO) { context.assets.open("cronometer_foods.tsv").use { it.readBytes().toString(Charsets.UTF_8) } }
                            val n = uk.woozy.fit.data.imports.CronometerFoodsImport.import(db, text)
                            if (n == 0) "Nothing new: those foods are already here" else "Added $n custom foods"
                        } catch (e: Exception) {
                            "Import failed: ${e.javaClass.simpleName}"
                        }
                    }
                })
            }
        }

        SectionLabel("Import from Everfit")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText("Adds your past Everfit strength workouts (weights and reps) so history, previous values and records work from day one. Cardio and bodyweight-only entries were not captured. Safe to run twice.", 13.sp, color = c.muted)
                HqPillButton("Import Everfit history", style = PillStyle.Outline, onClick = {
                    scope.launch {
                        message = try {
                            val text = withContext(Dispatchers.IO) { context.assets.open("everfit_history.tsv").use { it.readBytes().toString(Charsets.UTF_8) } }
                            val n = uk.woozy.fit.data.imports.EverfitImport.import(db, text)
                            if (n == 0) "Nothing new: Everfit history is already imported" else "Imported $n workouts from Everfit"
                        } catch (e: Exception) {
                            "Import failed: ${e.javaClass.simpleName}"
                        }
                    }
                })
            }
        }

        SectionLabel("Export to a spreadsheet")
        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HqText("Plain CSV files. These are not encrypted.", 13.sp, color = c.muted)
                HqPillButton("Weights", style = PillStyle.Outline, onClick = { export("hqfit-weights.csv") { CsvExport.weights(db.weights().observeAllOnce()) } })
                HqPillButton("Food diary", style = PillStyle.Outline, onClick = { export("hqfit-diary.csv") { CsvExport.diary(db.diary().between("0000-01-01", "9999-12-31")) } })
                HqPillButton("Workout sets", style = PillStyle.Outline, onClick = {
                    export("hqfit-sets.csv") {
                        val sessions = db.sessions().doneSince(0L)
                        CsvExport.sets(sessions, sessions.flatMap { db.sessions().sets(it.id) })
                    }
                })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val uri = restoreUri
    if (uri != null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { HqText("Replace all data?", 18.sp, FontWeight.SemiBold) },
            text = { HqText("Everything on this phone is replaced by the backup. This cannot be undone.", 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    scope.launch {
                        try {
                            context.contentResolver.openInputStream(uri)!!.use { container.database.restoreFrom(it) }
                            (context as? Activity)?.recreate()
                        } catch (e: InvalidBackupException) {
                            message = e.message
                        } catch (e: Exception) {
                            message = "Restore failed: ${e.javaClass.simpleName}"
                        }
                    }
                }) { HqText("Replace", 15.sp, FontWeight.SemiBold, HqTheme.colors.primary) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { HqText("Cancel", 15.sp) } },
        )
    }
}

/**
 * The recovery key is shown once, at first start. If it was never written down, this makes a new one: shown once,
 * typed back to confirm, and only then does it replace the old one. Backups made before the change still need the
 * old key, so take a new backup straight after.
 */
@Composable
private fun RecoveryKeyCard(container: AppContainer) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var proposed by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    HqCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HqText("Recovery key", 16.sp, FontWeight.Medium)
            val p = proposed
            if (p == null) {
                HqText("The recovery key is the only way back in on a new phone, and it is shown just once. If you did not write yours down, make a new one now. It replaces the old one, so take a new backup afterwards: backups made before still need the old key.", 13.sp, color = c.muted)
                result?.let { HqText(it, 13.sp) }
                HqPillButton("Make a new recovery key", style = PillStyle.Outline, onClick = { proposed = container.database.proposeRecoveryKey(); typed = ""; result = null })
            } else {
                HqText(p, 20.sp, FontWeight.Bold)
                HqText("Write it down or save it in a password manager. It is not saved until you type it back below.", 13.sp, color = c.muted)
                androidx.compose.material3.OutlinedTextField(
                    value = typed, onValueChange = { typed = it },
                    label = { HqText("Type it back to confirm", 13.sp, color = c.muted) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                HqPillButton("Save new recovery key", onClick = {
                    if (uk.woozy.fit.data.crypto.RecoveryCrypto.normalise(typed) != uk.woozy.fit.data.crypto.RecoveryCrypto.normalise(p)) {
                        result = "That does not match the key above."
                    } else scope.launch {
                        result = if (container.database.replaceRecoveryKey(p)) "New recovery key saved. Take a backup now." else "This phone could not open its key file, so nothing was changed."
                        proposed = null
                    }
                })
                result?.let { HqText(it, 13.sp) }
                HqPillButton("Cancel", style = PillStyle.Outline, onClick = { proposed = null; result = null })
            }
        }
    }
}
