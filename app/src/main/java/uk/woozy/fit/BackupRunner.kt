package uk.woozy.fit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.runBlocking
import uk.woozy.fit.data.backup.Backup
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Nightly encrypted backups. Each run writes a file into the app's own storage (the last seven are kept) and, if
 * you picked a folder, a copy there too (Drive, a USB drive, any folder the system file picker offers).
 * The app-private copies are lost if the app is uninstalled, so the chosen folder is the one that protects you.
 */
object BackupRunner {
    private fun prefs(c: Context) = c.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun nightlyOn(c: Context) = prefs(c).getBoolean("nightly", false)
    fun setNightly(c: Context, on: Boolean) { prefs(c).edit().putBoolean("nightly", on).apply(); BackupAlarm.schedule(c) }
    fun folder(c: Context): Uri? = prefs(c).getString("folder", null)?.let(Uri::parse)
    fun setFolder(c: Context, uri: Uri) = prefs(c).edit().putString("folder", uri.toString()).apply()
    fun lastStatus(c: Context): String = prefs(c).getString("status", "No backup yet") ?: "No backup yet"
    private fun setStatus(c: Context, s: String) = prefs(c).edit().putString("status", s).apply()

    private fun dir(c: Context) = File(c.filesDir, "backups").also { it.mkdirs() }

    /** Makes a backup now. Returns a one-line result for the screen. */
    fun run(c: Context, container: AppContainer, nowMs: Long = System.currentTimeMillis()): String {
        val msg = try {
            synchronized(SummaryProvider.LOCK) { if (container.database.db == null) runBlocking { container.database.start() } }
            if (container.database.db == null) return "Nothing to back up yet".also { setStatus(c, it) }
            val name = Backup.fileName(nowMs)
            val local = File(dir(c), name)
            val ok = local.outputStream().use { runBlocking { container.database.backupTo(it, nowMs) } }
            if (!ok) { local.delete(); return "Finish the recovery key step first".also { setStatus(c, it) } }
            dir(c).listFiles { f -> f.name.endsWith("." + Backup.EXTENSION) }?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
            var where = "on this phone"
            folder(c)?.let { tree ->
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(c.contentResolver, parent, "application/octet-stream", name)
                if (doc != null) {
                    c.contentResolver.openOutputStream(doc)?.use { out -> local.inputStream().use { it.copyTo(out) } }
                    where = "on this phone and in your folder"
                }
            }
            "Backed up $where, " + java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.UK).format(java.util.Date(nowMs))
        } catch (e: Exception) {
            "Backup failed: ${e.javaClass.simpleName}"
        }
        setStatus(c, msg)
        return msg
    }

    fun latestLocal(c: Context): File? = dir(c).listFiles { f -> f.name.endsWith("." + Backup.EXTENSION) }?.maxByOrNull { it.name }
}

object BackupAlarm {
    fun nextMs(now: LocalDateTime = LocalDateTime.now()): Long {
        var t = now.withHour(3).withMinute(30).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(c, 5, Intent(c, BackupReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (BackupRunner.nightlyOn(c)) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMs(), pi) else am.cancel(pi)
    }
}

class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { BackupAlarm.schedule(app); return }
        BackupAlarm.schedule(app)
        val pending = goAsync()
        Thread {
            try { BackupRunner.run(app, (app as HqFitApplication).container) } finally { pending.finish() }
        }.start()
    }
}
