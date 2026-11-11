package uk.woozy.fit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.woozy.fit.data.db.ExerciseEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.Seed
import uk.woozy.fit.data.db.TargetEntity
import uk.woozy.fit.data.db.WaterEntryEntity
import uk.woozy.fit.data.db.WeightEntryEntity

/**
 * Runs the real DAOs against an in-memory Room database on the JVM. SQLCipher itself needs the phone's native
 * library, so encryption on disk is a phone check (see HANDOVER.md); the tables, queries and seed are covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: HqFitDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HqFitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    @Test
    fun seedFillsNutrientsAndTargets() = runBlocking {
        Seed.run(db)
        assertEquals(Seed.nutrients.size, db.nutrients().count())
        assertEquals(2297.0, db.targets().get("energy")!!.value, 0.0)
        assertEquals(8500.0, db.targets().get("steps")!!.value, 0.0)
        assertEquals(2000.0, db.targets().get("water")!!.value, 0.0)
    }

    @Test
    fun seedTwiceKeepsEditedTargets() = runBlocking {
        Seed.run(db)
        db.targets().upsert(TargetEntity("protein", 160.0, "goal"))
        Seed.run(db)
        assertEquals(160.0, db.targets().get("protein")!!.value, 0.0)
    }

    @Test
    fun seededNutrientsHaveUniqueKeys() {
        assertEquals(Seed.nutrients.size, Seed.nutrients.map { it.key }.toSet().size)
    }

    @Test
    fun exerciseSearchMatchesPartOfName() = runBlocking {
        db.exercises().insert(ExerciseEntity(name = "Barbell Back Squat", muscle = "Quads", equipment = "Barbell"))
        db.exercises().insert(ExerciseEntity(name = "Bench Press", muscle = "Chest", equipment = "Barbell"))
        assertEquals(listOf("Barbell Back Squat"), db.exercises().search("squat").map { it.name })
    }

    @Test
    fun latestWeightIsTheNewest() = runBlocking {
        assertNull(db.weights().latest())
        db.weights().insert(WeightEntryEntity(epochMs = 1_000, kg = 87.4, source = "manual"))
        db.weights().insert(WeightEntryEntity(epochMs = 2_000, kg = 86.9, source = "scale"))
        assertEquals(86.9, db.weights().latest()!!.kg, 0.0)
    }

    @Test
    fun waterTotalsOnlyTheRequestedWindow() = runBlocking {
        db.water().insert(WaterEntryEntity(epochMs = 100, ml = 250))
        db.water().insert(WaterEntryEntity(epochMs = 200, ml = 500))
        db.water().insert(WaterEntryEntity(epochMs = 900, ml = 250))
        assertEquals(750, db.water().totalBetween(0, 500))
        assertNotNull(db.meta())
    }
}
