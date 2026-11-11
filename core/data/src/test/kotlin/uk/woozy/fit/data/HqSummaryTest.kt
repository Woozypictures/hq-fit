package uk.woozy.fit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.ScheduledWorkoutEntity
import uk.woozy.fit.data.db.WaterEntryEntity
import uk.woozy.fit.data.db.WeightEntryEntity
import uk.woozy.fit.data.share.HqSummaryBuilder
import uk.woozy.fit.model.Meal
import uk.woozy.fit.model.Nutrients
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HqSummaryTest {
    private lateinit var db: HqFitDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HqFitDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun emptyDatabaseGivesZerosAndNoWeight() = runBlocking {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 11, 11, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val s = HqSummaryBuilder.build(db, now, zone)
        assertEquals("2026-11-11", s.date)
        assertEquals(0.0, s.kcal, 0.0)
        assertNull(s.weightKg)
        assertNull(s.workoutToday)
        assertFalse(s.workoutTodayDone)
    }

    @Test
    fun totalsWaterWeightAndWeekAreReported() = runBlocking {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 11, 11, 12, 0, 0, 0, zone).toInstant().toEpochMilli() // a Wednesday
        db.diary().insert(
            uk.woozy.fit.data.db.DiaryEntryEntity(
                date = "2026-11-11", meal = Meal.LUNCH.key, foodId = null, name = "Test", grams = null,
                nutrients = Nutrients.encode(mapOf(Nutrients.ENERGY to 500.0, Nutrients.PROTEIN to 40.0, Nutrients.CARBS to 60.0, Nutrients.FIBRE to 10.0, Nutrients.FAT to 10.0)),
                createdMs = now,
            ),
        )
        db.water().insert(WaterEntryEntity(epochMs = now - 1000, ml = 250))
        db.weights().insert(WeightEntryEntity(epochMs = now - 5000, kg = 86.4, source = "manual"))
        db.targets().upsert(uk.woozy.fit.data.db.TargetEntity(Nutrients.ENERGY, 2297.0, "goal"))
        db.schedule().insertAll(
            listOf(
                ScheduledWorkoutEntity(programmeId = null, templateId = 1, templateName = "Full body A", date = "2026-11-09", originalDate = "2026-11-09", status = "done"),
                ScheduledWorkoutEntity(programmeId = null, templateId = 2, templateName = "Full body B", date = "2026-11-11", originalDate = "2026-11-11"),
                ScheduledWorkoutEntity(programmeId = null, templateId = 3, templateName = "Full body C", date = "2026-11-13", originalDate = "2026-11-13"),
            ),
        )
        val s = HqSummaryBuilder.build(db, now, zone)
        assertEquals(500.0, s.kcal, 0.0)
        assertEquals(50.0, s.netCarbsG, 0.0)
        assertEquals(250, s.waterMl)
        assertEquals(86.4, s.weightKg!!, 0.0)
        assertEquals(2297.0, s.kcalTarget!!, 0.0)
        assertEquals("Full body B", s.workoutToday)
        assertFalse(s.workoutTodayDone)
        assertEquals(1, s.workoutsDoneThisWeek)
        assertEquals(3, s.workoutsPlannedThisWeek)
    }
}
