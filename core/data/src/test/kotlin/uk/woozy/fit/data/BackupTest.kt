package uk.woozy.fit.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.woozy.fit.data.backup.Backup
import uk.woozy.fit.data.backup.CsvExport
import uk.woozy.fit.data.backup.InvalidBackupException
import uk.woozy.fit.data.db.DiaryEntryEntity
import uk.woozy.fit.data.db.SetLogEntity
import uk.woozy.fit.data.db.WeightEntryEntity
import uk.woozy.fit.data.db.WorkoutSessionEntity
import uk.woozy.fit.model.Nutrients
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset

class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val keyText = "salt=AAAA\nrecovery=BBBB\nconfirmed=1"

    private fun zipOf(schema: Int): ByteArray {
        val db = tmp.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        return ByteArrayOutputStream().also { Backup.write(db, keyText, schema, 1000L, it) }.toByteArray()
    }

    @Test
    fun roundTripKeepsTheDatabaseBytesAndKeyFile() {
        val out = Backup.read(ByteArrayInputStream(zipOf(4)), tmp.newFolder(), maxSchema = 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), out.dbFile.readBytes())
        assertEquals(keyText, out.keyText)
        assertEquals(4, out.schemaVersion)
    }

    @Test
    fun aBackupFromANewerVersionIsRefused() {
        try { Backup.read(ByteArrayInputStream(zipOf(9)), tmp.newFolder(), maxSchema = 4); fail() } catch (e: InvalidBackupException) { assertTrue(e.message!!.contains("newer")) }
    }

    @Test
    fun randomBytesAreRefused() {
        try { Backup.read(ByteArrayInputStream(ByteArray(100) { it.toByte() }), tmp.newFolder(), 4); fail() } catch (e: InvalidBackupException) { }
    }

    @Test
    fun aZipWithoutTheKeyIsRefused() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("hqfit.db")); z.write(1); z.closeEntry() }
        try { Backup.read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder(), 4); fail() } catch (e: InvalidBackupException) { }
    }

    @Test
    fun csvEscapesCommasQuotesAndNewlines() {
        assertEquals("\"a,b\"", CsvExport.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.cell("say \"hi\""))
        assertEquals("plain", CsvExport.cell("plain"))
        assertEquals("", CsvExport.cell(null))
    }

    @Test
    fun weightsCsvIsOldestFirstWithHeader() {
        val csv = CsvExport.weights(
            listOf(WeightEntryEntity(1, 2_000_000_000_000L, 86.4, "scale"), WeightEntryEntity(2, 1_000_000_000_000L, 90.0, "manual")),
            ZoneOffset.UTC,
        )
        val lines = csv.trim().lines()
        assertEquals("date_time,kg,source", lines[0])
        assertEquals("2001-09-09T01:46:40,90,manual", lines[1])
        assertEquals("2033-05-18T03:33:20,86.40,scale", lines[2])
    }

    @Test
    fun diaryCsvHasMacros() {
        val n = Nutrients.encode(mapOf(Nutrients.ENERGY to 500.0, Nutrients.PROTEIN to 40.0, Nutrients.CARBS to 60.0, Nutrients.FIBRE to 10.0, Nutrients.FAT to 12.5))
        val csv = CsvExport.diary(listOf(DiaryEntryEntity(1, "2026-11-11", "lunch", null, "Chicken, rice", 300.0, n, 1L)))
        assertEquals("2026-11-11,lunch,\"Chicken, rice\",300,500,40,60,10,12.50", csv.trim().lines()[1])
    }

    @Test
    fun setsCsvJoinsTheSession() {
        val s = WorkoutSessionEntity(1, null, null, "Full body A", 1_000_000_000_000L)
        val set = SetLogEntity(1, 1, 0, 0, "main", 5, "Squat", null, null, 8, 10, 90, "", "normal", 60.0, 10, true, 1L)
        val csv = CsvExport.sets(listOf(s), listOf(set), ZoneOffset.UTC)
        assertEquals("2001-09-09,Full body A,Squat,1,normal,60,10,,1", csv.trim().lines()[1])
    }
}
