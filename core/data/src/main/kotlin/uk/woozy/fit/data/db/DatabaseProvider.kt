package uk.woozy.fit.data.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import uk.woozy.fit.data.crypto.KeyVault
import uk.woozy.fit.data.crypto.RecoveryCrypto
import uk.woozy.fit.data.crypto.WrongRecoveryKeyException

/** What the app should show before the database is usable. */
sealed interface DbGate {
    data object Loading : DbGate
    /** No database yet, or one made but never confirmed. Show the recovery key flow. */
    data object FirstRun : DbGate
    /** Data exists but this phone's Keystore cannot open it. Ask for the recovery key. */
    data object NeedsRecovery : DbGate
    data object Ready : DbGate
}

class DatabaseProvider(private val context: Context) {
    private val vault = KeyVault(context)
    private val dbName = "hqfit.db"
    private val SCHEMA_VERSION = DB_VERSION

    @Volatile
    var db: HqFitDatabase? = null
        private set

    /** Decide what to show at start. Opens the database straight away when the Keystore can. */
    suspend fun start(): DbGate = withContext(Dispatchers.IO) {
        // One open at a time, and never a second Room instance: the provider and the screen can both ask.
        gate.withLock { startLocked() }
    }

    private val gate = kotlinx.coroutines.sync.Mutex()

    private suspend fun startLocked(): DbGate {
        if (db != null) return DbGate.Ready
        return when (vault.found()) {
            KeyVault.Found.NOTHING, KeyVault.Found.UNCONFIRMED -> DbGate.FirstRun
            KeyVault.Found.READY -> {
                val key = vault.unlock()
                if (key == null) DbGate.NeedsRecovery else { open(key); DbGate.Ready }
            }
        }
    }

    /**
     * Creates a fresh key and database, and returns the recovery key to show once. Anything unconfirmed from an
     * earlier attempt is replaced: its recovery key was never shown to be saved, and it holds no data.
     */
    suspend fun createNew(): String = withContext(Dispatchers.IO) {
        db?.close()
        db = null
        deleteFiles()
        val (key, recovery) = vault.create()
        open(key)
        recovery
    }

    /** The user typed the recovery key back correctly. */
    suspend fun confirm() = withContext(Dispatchers.IO) { vault.confirm() }

    /** Returns false for a wrong key. */
    suspend fun recover(recoveryKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            open(vault.recover(recoveryKey))
            true
        } catch (e: WrongRecoveryKeyException) {
            false
        }
    }

    private suspend fun open(key: ByteArray) {
        System.loadLibrary("sqlcipher")
        val built = Room.databaseBuilder(context, HqFitDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
        Seed.run(built, catalogue = { context.assets.open("exercises.tsv").bufferedReader() }, cofid = { context.assets.open("cofid.tsv").bufferedReader() })
        db = built
    }

    private fun deleteFiles() {
        context.deleteDatabase(dbName)
    }

    /** Writes a backup zip of the database and key file. Returns false when there is nothing safe to back up yet. */
    suspend fun backupTo(out: java.io.OutputStream, nowMs: Long): Boolean = withContext(Dispatchers.IO) {
        val open = db ?: return@withContext false
        val keyText = vault.exportForBackup() ?: return@withContext false
        // Fold the write-ahead log into the main file so the copy is complete.
        open.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        uk.woozy.fit.data.backup.Backup.write(context.getDatabasePath(dbName), keyText, SCHEMA_VERSION, nowMs, out)
        true
    }

    /**
     * Replaces everything on this phone with a backup. The old data is gone afterwards. The next start asks for
     * the backup's recovery key. Throws [uk.woozy.fit.data.backup.InvalidBackupException] for a bad file, before
     * anything is touched.
     */
    suspend fun restoreFrom(input: java.io.InputStream) = withContext(Dispatchers.IO) {
        val work = java.io.File(context.cacheDir, "restore")
        work.deleteRecursively()
        val c = uk.woozy.fit.data.backup.Backup.read(input, work, SCHEMA_VERSION)
        db?.close()
        db = null
        deleteFiles()
        val target = context.getDatabasePath(dbName)
        target.parentFile?.mkdirs()
        c.dbFile.copyTo(target, overwrite = true)
        vault.installFromBackup(c.keyText)
        work.deleteRecursively()
    }

    fun isWellFormed(input: String) = RecoveryCrypto.isWellFormed(input)
}
