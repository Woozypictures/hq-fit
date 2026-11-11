package uk.woozy.fit.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class InvalidBackupException(message: String) : Exception(message)

/**
 * The backup file: a zip holding the encrypted database exactly as it sits on the phone, plus the wrapped key file
 * with this phone's Keystore part removed. The database is AES-256 encrypted and the key is wrapped by the recovery
 * key, so the file is safe to keep in cloud storage; it can only be opened with your recovery key.
 */
object Backup {
    const val DB_ENTRY = "hqfit.db"
    const val KEY_ENTRY = "dbkey.txt"
    const val INFO_ENTRY = "info.txt"
    const val EXTENSION = "hqfitbak"

    class Contents(val dbFile: File, val keyText: String, val schemaVersion: Int)

    fun write(dbFile: File, keyText: String, schemaVersion: Int, createdMs: Long, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(INFO_ENTRY))
        zip.write("app=uk.woozy.fit\nschema=$schemaVersion\ncreated=$createdMs\n".toByteArray())
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(KEY_ENTRY))
        zip.write(keyText.toByteArray())
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(DB_ENTRY))
        dbFile.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        zip.finish()
    }

    /** Unpacks into [workDir]. Throws [InvalidBackupException] for anything that is not a usable HQ Fit backup. */
    fun read(input: InputStream, workDir: File, maxSchema: Int): Contents {
        workDir.mkdirs()
        val db = File(workDir, "restore.db")
        var key: String? = null
        var info: String? = null
        var haveDb = false
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when (e.name) {
                        DB_ENTRY -> { db.outputStream().use { zip.copyTo(it) }; haveDb = true }
                        KEY_ENTRY -> key = zip.readBytes().toString(Charsets.UTF_8)
                        INFO_ENTRY -> info = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
        } catch (e: java.io.IOException) {
            throw InvalidBackupException("That file is not an HQ Fit backup")
        }
        if (!haveDb || key == null || info == null || db.length() == 0L) throw InvalidBackupException("That file is not an HQ Fit backup")
        val lines = info!!.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        if (lines["app"] != "uk.woozy.fit") throw InvalidBackupException("That file is not an HQ Fit backup")
        val schema = lines["schema"]?.toIntOrNull() ?: throw InvalidBackupException("That backup has no version")
        if (schema > maxSchema) throw InvalidBackupException("That backup was made by a newer version of HQ Fit. Update the app first")
        if (!key!!.contains("recovery=") || !key!!.contains("salt=")) throw InvalidBackupException("That backup has no recovery data")
        return Contents(db, key!!, schema)
    }

    fun fileName(nowMs: Long): String =
        "hqfit-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.UK).format(java.util.Date(nowMs)) + "." + EXTENSION
}
