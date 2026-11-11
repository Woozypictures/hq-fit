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
import uk.woozy.fit.data.db.Sessions
import uk.woozy.fit.model.RecordType
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

    private suspend fun scheduledB() = db.schedule().observeBetween("2026-11-11", "2026-11-11").first().single()

    private suspend fun trainAll(sessionId: Long, weightFor: (String) -> Double, reps: Int) {
        db.sessions().sets(sessionId).forEach {
            db.sessions().updateValues(it.id, weightFor(it.exerciseName), reps)
            db.sessions().updateDone(it.id, true, 1_000L + it.id)
        }
    }

    @Test
    fun startingLaysOutEverySetFromTheTemplate() = runBlocking {
        Seed.run(db, today = wednesday)
        val id = Sessions.start(db, scheduledB(), nowMs = 1_000)
        val sets = db.sessions().sets(id)
        // warm up 5 + prehab 2 + five movements x 2 + superset 2 x 2 + cool down 1
        assertEquals(22, sets.size)
        assertEquals(sets.first().exerciseName, "Treadmill Walk")
        assertEquals(600, sets.first().durationSec)
        assertEquals(45.0, sets.first { it.exerciseName == "Wide-Grip Lat Pulldown" }.weightKg!!, 0.0)
    }

    @Test
    fun startingTwiceResumesTheSameSession() = runBlocking {
        Seed.run(db, today = wednesday)
        val first = Sessions.start(db, scheduledB(), nowMs = 1_000)
        val again = Sessions.start(db, scheduledB(), nowMs = 9_000)
        assertEquals(first, again)
        assertEquals(22, db.sessions().sets(first).size)
    }

    @Test
    fun ticksAreSavedImmediatelyAndSurviveReopening() = runBlocking {
        Seed.run(db, today = wednesday)
        val id = Sessions.start(db, scheduledB(), nowMs = 1_000)
        val set = db.sessions().sets(id).first { it.exerciseName == "Leg Extensions" }
        db.sessions().updateValues(set.id, 52.5, 12)
        db.sessions().updateDone(set.id, true, 5_000)
        val back = db.sessions().sets(id).first { it.id == set.id }
        assertEquals(52.5, back.weightKg!!, 0.0)
        assertEquals(12, back.reps)
        assertEquals(true, back.done)
        assertEquals(id, db.sessions().active()!!.id)
    }

    @Test
    fun finishingMarksTheCalendarDayDone() = runBlocking {
        Seed.run(db, today = wednesday)
        val scheduled = scheduledB()
        val id = Sessions.start(db, scheduled, nowMs = 1_000)
        Sessions.finish(db, id, nowMs = 61_000, feeling = 4)
        assertNull(db.sessions().active())
        assertEquals("done", db.schedule().get(scheduled.id)!!.status)
        assertEquals(60_000L, Sessions.summary(db, id, 99_999)!!.durationMs)
    }

    @Test
    fun nextSessionStartsFromLastTimesWeightAndShowsPrevious() = runBlocking {
        Seed.run(db, today = wednesday)
        val first = Sessions.start(db, scheduledB(), nowMs = 1_000)
        trainAll(first, weightFor = { 60.0 }, reps = 10)
        Sessions.finish(db, first, nowMs = 2_000)
        val nextDay = db.schedule().observeBetween("2026-11-13", "2026-11-30").first().first { it.templateName == "Fullbody B" }
        val second = Sessions.start(db, nextDay, nowMs = 3_000)
        val set = db.sessions().sets(second).first { it.exerciseName == "Leg Extensions" && it.setIndex == 1 }
        assertEquals(60.0, set.weightKg!!, 0.0)
        assertNull(set.reps)
        val prev = db.sessions().previous(set.exerciseId, 1, second)!!
        assertEquals(10, prev.reps)
    }

    @Test
    fun recordsAreFoundAgainstEarlierSessionsOnly() = runBlocking {
        Seed.run(db, today = wednesday)
        val first = Sessions.start(db, scheduledB(), nowMs = 1_000)
        trainAll(first, weightFor = { 50.0 }, reps = 10)
        Sessions.finish(db, first, nowMs = 2_000)
        // The very first time is a baseline, not a record.
        assertEquals(0, Sessions.summary(db, first, 2_000)!!.records.size)
        val nextDay = db.schedule().observeBetween("2026-11-13", "2026-11-30").first().first { it.templateName == "Fullbody B" }
        val second = Sessions.start(db, nextDay, nowMs = 3_000)
        trainAll(second, weightFor = { 55.0 }, reps = 10)
        Sessions.finish(db, second, nowMs = 4_000)
        val summary = Sessions.summary(db, second, 4_000)!!
        assertEquals(true, summary.records.isNotEmpty())
        assertEquals(true, summary.records.all { RecordType.HEAVIEST in it.records })
        assertEquals(summary.records.size, summary.records.map { it.exerciseName }.toSet().size)
    }

    @Test
    fun discardingRemovesTheSessionButNotTheCalendar() = runBlocking {
        Seed.run(db, today = wednesday)
        val scheduled = scheduledB()
        val id = Sessions.start(db, scheduled, nowMs = 1_000)
        Sessions.discard(db, id)
        assertNull(db.sessions().active())
        assertEquals(0, db.sessions().sets(id).size)
        assertEquals("planned", db.schedule().get(scheduled.id)!!.status)
    }

    @Test
    fun replacingAnExerciseKeepsWeightsAndClearsTicks() = runBlocking {
        Seed.run(db, today = wednesday)
        val id = Sessions.start(db, scheduledB(), nowMs = 1_000)
        val before = db.sessions().sets(id).first { it.exerciseName == "Leg Extensions" }
        db.sessions().updateDone(before.id, true, 5_000)
        val other = ExerciseEntity(id = db.exercises().insert(ExerciseEntity(name = "Sissy Squat", muscle = "quads", equipment = "body weight")), name = "Sissy Squat", muscle = "quads", equipment = "body weight")
        Sessions.replace(db, id, before.position, other)
        val after = db.sessions().sets(id).filter { it.position == before.position }
        assertEquals(listOf("Sissy Squat", "Sissy Squat"), after.map { it.exerciseName })
        assertEquals(50.0, after.first().weightKg!!, 0.0)
        assertEquals(false, after.first().done)
    }

    @Test
    fun addingAfterBeforeAndSupersetKeepsOrderAndGroups() = runBlocking {
        Seed.run(db, today = wednesday)
        val id = Sessions.start(db, scheduledB(), nowMs = 1_000)
        val anchor = db.sessions().sets(id).first { it.exerciseName == "Leg Extensions" }
        fun ex(name: String) = ExerciseEntity(id = 0, name = name, muscle = "x", equipment = "x")
        suspend fun saved(name: String) = ex(name).let { it.copy(id = db.exercises().insert(it)) }
        Sessions.add(db, id, anchor.position, saved("After One"), Sessions.Where.AFTER)
        Sessions.add(db, id, anchor.position, saved("Before One"), Sessions.Where.BEFORE)
        val order = db.sessions().sets(id).map { it.exerciseName }.distinct()
        val i = order.indexOf("Leg Extensions")
        assertEquals("Before One", order[i - 1])
        assertEquals("After One", order[i + 1])
        val positions = db.sessions().sets(id).map { it.position }.distinct()
        assertEquals(positions.sorted(), positions)
        // superset: new exercise follows the anchor and both share a group
        val anchor2 = db.sessions().sets(id).first { it.exerciseName == "Lying Leg Curls" }
        Sessions.add(db, id, anchor2.position, saved("Paired"), Sessions.Where.SUPERSET)
        val grouped = db.sessions().sets(id).filter { it.exerciseName in listOf("Lying Leg Curls", "Paired") }
        assertEquals(1, grouped.map { it.supersetGroup }.toSet().size)
        assertEquals(true, grouped.first().supersetGroup != null)
    }

    @Test
    fun recordsTableAndHistoryComeFromFinishedSessions() = runBlocking {
        Seed.run(db, today = wednesday)
        val first = Sessions.start(db, scheduledB(), nowMs = 1_000)
        trainAll(first, weightFor = { 50.0 }, reps = 10)
        // an unfinished session does not count yet
        assertEquals(0, db.sessions().observeRecords().first().size)
        Sessions.finish(db, first, nowMs = 2_000)
        val rows = db.sessions().observeRecords().first()
        val ext = rows.first { it.exerciseName == "Leg Extensions" }
        assertEquals(50.0, ext.bestKg, 0.0)
        assertEquals(66.667, ext.bestE1rm, 0.001)
        assertEquals(1, ext.sessions)
        val history = db.sessions().exerciseHistory(ext.exerciseId)
        assertEquals(2, history.size)
        assertEquals(10, history.first().reps)
    }
}
