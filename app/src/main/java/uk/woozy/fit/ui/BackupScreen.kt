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

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            BackupRunner.setFolder(context, uri)
            folder = uri
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

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

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackBar("Backup and restore", onBack)

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
        message?.let { HqText(it, 13.sp, color = c.muted) }
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
