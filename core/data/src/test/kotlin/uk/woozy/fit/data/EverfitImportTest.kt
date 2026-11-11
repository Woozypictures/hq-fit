package uk.woozy.fit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.imports.EverfitImport

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EverfitImportTest {
    private lateinit var db: HqFitDatabase

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HqFitDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    private val tsv = "2026-11-09\tPUSH\t1\tPreacher Curl\t22.5x10*2,25x8\n" +
        "2026-11-09\tPUSH\t1\tFace Pull\t20x12*3\n" +
        "2026-11-09\tPUSH\t2\tFace Pull\t20x12\n" +
        "2026-11-09\tPUSH\t1\tTreadmill Walk\t\n"

    @Test fun expandsRepeatedSets() {
        assertEquals(listOf(22.5 to 10, 22.5 to 10, 25.0 to 8), EverfitImport.parseSets("22.5x10*2,25x8"))
    }

    @Test fun skipsEmptyLines() {
        assertEquals(3, EverfitImport.parse(tsv).size)
    }

    @Test fun importsOncePerDayAndIsIdempotent() = runBlocking {
        assertEquals(2, EverfitImport.import(db, tsv))
        assertEquals(0, EverfitImport.import(db, tsv))
        val sessions = db.sessions().doneSince(0L)
        assertEquals(2, sessions.size)
        val first = sessions.first()
        assertEquals(EverfitImport.MARKER, first.scheduledId)
        assertEquals(6, db.sessions().sets(first.id).size)
    }
}
