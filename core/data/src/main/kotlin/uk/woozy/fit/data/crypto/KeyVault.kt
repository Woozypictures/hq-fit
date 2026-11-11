package uk.woozy.fit.data.crypto

import android.content.Context
import java.io.File
import java.util.Base64

/**
 * Where the wrapped database key lives: a small text file in app-private storage that is excluded from
 * Android backup. Nothing in it is usable without the Keystore key or the recovery key.
 */
class KeyVault(context: Context) {
    private val file = File(context.noBackupFilesDir, "dbkey.txt")
    private val keystore = KeystoreWrapper()

    /** What was found on disk at start. */
    enum class Found { NOTHING, UNCONFIRMED, READY }

    fun found(): Found {
        val f = read() ?: return Found.NOTHING
        return if (f["confirmed"] == "1") Found.READY else Found.UNCONFIRMED
    }

    /** Makes a new database key. Returns the key and the recovery key to show once. */
    fun create(): Pair<ByteArray, String> {
        wipe()
        val dbKey = RecoveryCrypto.newDbKey()
        val recovery = RecoveryCrypto.newRecoveryKey()
        val salt = RecoveryCrypto.newSalt()
        write(
            mapOf(
                "salt" to b64(salt),
                "recovery" to b64(RecoveryCrypto.wrap(dbKey, recovery, salt)),
                "keystore" to b64(keystore.wrap(dbKey)),
                "confirmed" to "0",
            ),
        )
        return dbKey to recovery
    }

    fun confirm() {
        val f = read() ?: return
        write(f + ("confirmed" to "1"))
    }

    /** The day-to-day path. Null when the Keystore key cannot open it (new phone, restored data). */
    fun unlock(): ByteArray? {
        val f = read() ?: return null
        return runCatching { keystore.unwrap(unb64(f.getValue("keystore"))) }.getOrNull()
    }

    /** Opens with the recovery key, then re-wraps with this phone's Keystore so the next start is silent. */
    fun recover(recoveryKey: String): ByteArray {
        val f = read() ?: throw WrongRecoveryKeyException()
        val dbKey = RecoveryCrypto.unwrap(unb64(f.getValue("recovery")), recoveryKey, unb64(f.getValue("salt")))
        write(f + ("keystore" to b64(keystore.wrap(dbKey))) + ("confirmed" to "1"))
        return dbKey
    }

    fun wipe() {
        file.delete()
        runCatching { keystore.delete() }
    }

    private fun read(): Map<String, String>? {
        if (!file.exists()) return null
        return runCatching {
            file.readLines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        }.getOrNull()
    }

    private fun write(map: Map<String, String>) {
        val tmp = File(file.parentFile, "dbkey.tmp")
        tmp.writeText(map.entries.joinToString("\n") { "${it.key}=${it.value}" })
        check(tmp.renameTo(file)) { "Could not save the key file" }
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)
}
