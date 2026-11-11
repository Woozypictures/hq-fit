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
import java.io.StringReader
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.woozy.fit.data.db.ExerciseCatalogue
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

    private val wednesday = LocalDate.of(2026, 11, 11)

    @Test
    fun programmeIsSeededOnceWithThreeWorkouts() = runBlocking {
        Seed.run(db, today = wednesday)
        Seed.run(db, today = wednesday)
        assertEquals(listOf("Fullbody A", "Fullbody B", "Fullbody C"), db.templates().all().map { it.name })
        assertEquals(1, db.programmes().all().size)
        // 10 weeks, Mon/Wed/Fri, from Wednesday 11 Nov: 3 x 10 minus the Monday already past
        assertEquals(29, db.schedule().count())
    }

    @Test
    fun fullbodyAHasTheHandoverLines() = runBlocking {
        Seed.run(db, today = wednesday)
        val a = db.templates().all().first { it.name == "Fullbody A" }
        val rows = db.templates().observeItems(a.id).first()
        assertEquals(14, rows.size)
        assertEquals("Treadmill Walk", rows.first().exerciseName)
        assertEquals(600, rows.first().durationSec)
        val leg = rows.first { it.exerciseName == "Leg Press" }
        assertEquals(100.0, leg.weightKg!!, 0.0)
        assertEquals(10, leg.repsMin)
        assertEquals(15, leg.repsMax)
        val superset = rows.filter { it.supersetGroup != null }
        assertEquals(listOf("Preacher Curl", "Cable Triceps Pushdown"), superset.map { it.exerciseName })
        assertEquals(listOf(0, 90), superset.map { it.restSec })
        assertEquals("Treadmill Walk", rows.last().exerciseName)
    }

    @Test
    fun movingADayKeepsItsOriginalDate() = runBlocking {
        Seed.run(db, today = wednesday)
        val first = db.schedule().observeBetween("2026-11-11", "2026-11-11").first().single()
        db.schedule().move(first.id, "2026-11-12")
        val moved = db.schedule().get(first.id)!!
        assertEquals("2026-11-12", moved.date)
        assertEquals("2026-11-11", moved.originalDate)
    }

    @Test
    fun deletingTheProgrammeAndWorkoutsLeavesTheCalendarAlone() = runBlocking {
        Seed.run(db, today = wednesday)
        val before = db.schedule().count()
        db.programmes().all().forEach { db.programmes().delete(it.id) }
        db.templates().all().forEach { db.templates().delete(it.id) }
        assertEquals(before, db.schedule().count())
        assertEquals("Fullbody B", db.schedule().observeBetween("2026-11-11", "2026-11-11").first().single().templateName)
    }

    @Test
    fun catalogueParsingSkipsBlankNamesAndTitleCases() {
        val tsv = "0001\t3/4 sit-up\twaist\tbody weight\tabs\thip flexors\tcalisthenics\tA short sit-up.\n" +
            "0002\t\twaist\tbody weight\tabs\t\tx\ty\n" +
            "0003\tdecline chest press (machine)\tchest\tleverage machine\tpectorals\t\tstrength\tPress.\n"
        val parsed = ExerciseCatalogue.parse(java.io.BufferedReader(StringReader(tsv)))
        assertEquals(listOf("3/4 Sit-Up", "Decline Chest Press (machine)"), parsed.map { it.name })
        assertEquals("abs", parsed[0].muscle)
    }

    @Test
    fun programmeExercisesReuseCatalogueRows() = runBlocking {
        val tsv = "0001\tleg press\tupper legs\tleverage machine\tquads\t\tstrength\tPush the platform.\n"
        Seed.run(db, catalogue = { java.io.BufferedReader(StringReader(tsv)) }, today = wednesday)
        val matches = db.exercises().search("leg press")
        assertEquals(1, matches.size)
        assertEquals("Push the platform.", matches.first().instructions)
    }

    @Test
    fun libraryFiltersByMuscleAndEquipment() = runBlocking {
        db.exercises().insert(ExerciseEntity(name = "Barbell Curl", muscle = "biceps", equipment = "barbell"))
        db.exercises().insert(ExerciseEntity(name = "Cable Curl", muscle = "biceps", equipment = "cable"))
        db.exercises().insert(ExerciseEntity(name = "Cable Row", muscle = "lats", equipment = "cable"))
        assertEquals(2, db.exercises().searchFiltered("curl", "biceps", "").size)
        assertEquals(listOf("Cable Curl"), db.exercises().searchFiltered("", "biceps", "cable").map { it.name })
        assertEquals(listOf("biceps", "lats"), db.exercises().muscles())
    }
}
