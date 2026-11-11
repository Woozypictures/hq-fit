package uk.woozy.fit.data.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
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

    @Volatile
    var db: HqFitDatabase? = null
        private set

    /** Decide what to show at start. Opens the database straight away when the Keystore can. */
    suspend fun start(): DbGate = withContext(Dispatchers.IO) {
        when (vault.found()) {
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
        Seed.run(built, catalogue = { context.assets.open("exercises.tsv").bufferedReader() })
        db = built
    }

    private fun deleteFiles() {
        context.deleteDatabase(dbName)
    }

    fun isWellFormed(input: String) = RecoveryCrypto.isWellFormed(input)
}
