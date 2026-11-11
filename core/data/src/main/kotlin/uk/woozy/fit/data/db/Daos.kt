package uk.woozy.fit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutrientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NutrientEntity>)

    @Query("SELECT * FROM nutrient ORDER BY sort")
    suspend fun all(): List<NutrientEntity>

    @Query("SELECT COUNT(*) FROM nutrient")
    suspend fun count(): Int
}

@Dao
interface TargetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TargetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TargetEntity)

    @Query("SELECT * FROM target")
    suspend fun all(): List<TargetEntity>

    @Query("SELECT * FROM target WHERE `key` = :key")
    suspend fun get(key: String): TargetEntity?
}

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insert(row: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE name LIKE '%' || :text || '%' ORDER BY name LIMIT 50")
    suspend fun search(text: String): List<ExerciseEntity>

    @Query(
        "SELECT * FROM exercise WHERE name LIKE '%' || :text || '%' " +
            "AND (:muscle = '' OR muscle = :muscle) AND (:equipment = '' OR equipment = :equipment) ORDER BY name LIMIT 100",
    )
    suspend fun searchFiltered(text: String, muscle: String, equipment: String): List<ExerciseEntity>

    @Query("SELECT DISTINCT muscle FROM exercise WHERE muscle != '' ORDER BY muscle")
    suspend fun muscles(): List<String>

    @Query("SELECT DISTINCT equipment FROM exercise WHERE equipment != '' ORDER BY equipment")
    suspend fun equipments(): List<String>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun findById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?
}

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(row: WeightEntryEntity): Long

    @Query("SELECT * FROM weight_entry ORDER BY epochMs DESC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry ORDER BY epochMs DESC LIMIT 1")
    suspend fun latest(): WeightEntryEntity?

    @Query("SELECT * FROM weight_entry ORDER BY epochMs DESC")
    suspend fun observeAllOnce(): List<WeightEntryEntity>

    @Query("SELECT COUNT(*) FROM weight_entry WHERE epochMs BETWEEN :from AND :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT * FROM weight_entry WHERE epochMs >= :from AND source != 'import' ORDER BY epochMs")
    suspend fun ownSince(from: Long): List<WeightEntryEntity>

    @Query("DELETE FROM weight_entry WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(row: WaterEntryEntity): Long

    @Query("SELECT * FROM water_entry WHERE epochMs >= :from ORDER BY epochMs")
    suspend fun since(from: Long): List<WaterEntryEntity>

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_entry WHERE epochMs >= :from AND epochMs < :to")
    suspend fun totalBetween(from: Long, to: Long): Int

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_entry WHERE epochMs >= :from AND epochMs < :to")
    fun observeTotal(from: Long, to: Long): Flow<Int>

    /** Takes back the most recent drink in a window (a mis-tap). */
    @Query("DELETE FROM water_entry WHERE id = (SELECT id FROM water_entry WHERE epochMs >= :from AND epochMs < :to ORDER BY epochMs DESC LIMIT 1)")
    suspend fun deleteLatestBetween(from: Long, to: Long)
}

@Dao
interface MetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: MetaEntity)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?
}

/** A template line joined with its exercise, ready to show. */
data class TemplateItemRow(
    val id: Long,
    val section: String,
    val position: Int,
    val exerciseId: Long,
    val exerciseName: String,
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val weightKg: Double?,
    val restSec: Int,
    val supersetGroup: Int?,
    val note: String,
)

@Dao
interface TemplateDao {
    @Insert
    suspend fun insert(row: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertItems(rows: List<TemplateItemEntity>)

    @Query("SELECT * FROM workout_template ORDER BY name")
    suspend fun all(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun get(id: Long): WorkoutTemplateEntity?

    @Query(
        "SELECT i.id, i.section, i.position, i.exerciseId, e.name AS exerciseName, i.sets, i.repsMin, i.repsMax, " +
            "i.durationSec, i.weightKg, i.restSec, i.supersetGroup, i.note " +
            "FROM template_item i JOIN exercise e ON e.id = i.exerciseId WHERE i.templateId = :templateId ORDER BY i.position",
    )
    fun observeItems(templateId: Long): Flow<List<TemplateItemRow>>

    @Query(
        "SELECT i.id, i.section, i.position, i.exerciseId, e.name AS exerciseName, i.sets, i.repsMin, i.repsMax, " +
            "i.durationSec, i.weightKg, i.restSec, i.supersetGroup, i.note " +
            "FROM template_item i JOIN exercise e ON e.id = i.exerciseId WHERE i.templateId = :templateId ORDER BY i.position",
    )
    suspend fun items(templateId: Long): List<TemplateItemRow>

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProgrammeDao {
    @Insert
    suspend fun insert(row: ProgrammeEntity): Long

    @Insert
    suspend fun insertDays(rows: List<ProgrammeDayEntity>)

    @Query("SELECT * FROM programme ORDER BY id")
    suspend fun all(): List<ProgrammeEntity>

    @Query("DELETE FROM programme WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insertAll(rows: List<ScheduledWorkoutEntity>)

    @Query("SELECT * FROM scheduled_workout WHERE date >= :from AND date <= :to ORDER BY date, id")
    fun observeBetween(from: String, to: String): Flow<List<ScheduledWorkoutEntity>>

    @Query("SELECT * FROM scheduled_workout WHERE date >= :from AND date <= :to ORDER BY date, id")
    suspend fun between(from: String, to: String): List<ScheduledWorkoutEntity>

    @Query("SELECT * FROM scheduled_workout WHERE id = :id")
    suspend fun get(id: Long): ScheduledWorkoutEntity?

    /** Only [date] changes; originalDate is left as it was first set. */
    @Query("UPDATE scheduled_workout SET date = :date WHERE id = :id")
    suspend fun move(id: Long, date: String)

    @Query("UPDATE scheduled_workout SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM scheduled_workout")
    suspend fun count(): Int
}

/** The best a lift has done, from finished sessions. */
data class RecordRow(
    val exerciseId: Long,
    val exerciseName: String,
    val bestKg: Double,
    val bestE1rm: Double,
    val sessions: Int,
    val lastMs: Long,
)

data class HistoryRow(val sessionId: Long, val startedMs: Long, val setIndex: Int, val weightKg: Double, val reps: Int)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(row: WorkoutSessionEntity): Long

    @Insert
    suspend fun insertSets(rows: List<SetLogEntity>)

    @Insert
    suspend fun insertSet(row: SetLogEntity): Long

    @Query("SELECT * FROM workout_session WHERE status = 'active' ORDER BY startedMs DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status = 'active' ORDER BY startedMs DESC LIMIT 1")
    suspend fun active(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun get(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId ORDER BY position, setIndex")
    fun observeSets(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId ORDER BY position, setIndex")
    suspend fun sets(sessionId: Long): List<SetLogEntity>

    @Query("UPDATE set_log SET weightKg = :weightKg, reps = :reps WHERE id = :id")
    suspend fun updateValues(id: Long, weightKg: Double?, reps: Int?)

    @Query("UPDATE set_log SET done = :done, doneAtMs = :doneAtMs WHERE id = :id")
    suspend fun updateDone(id: Long, done: Boolean, doneAtMs: Long?)

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE set_log SET exerciseId = :exerciseId, exerciseName = :name, done = 0, doneAtMs = NULL, reps = NULL WHERE sessionId = :sessionId AND position = :position")
    suspend fun replaceExercise(sessionId: Long, position: Int, exerciseId: Long, name: String)

    @Query("UPDATE set_log SET position = position + 1 WHERE sessionId = :sessionId AND position > :position")
    suspend fun shiftAfter(sessionId: Long, position: Int)

    @Query("UPDATE set_log SET position = position + 1 WHERE sessionId = :sessionId AND position >= :position")
    suspend fun shiftFrom(sessionId: Long, position: Int)

    @Query("UPDATE set_log SET supersetGroup = :group WHERE sessionId = :sessionId AND position = :position")
    suspend fun setSuperset(sessionId: Long, position: Int, group: Int?)

    @Query("SELECT COALESCE(MAX(supersetGroup), 0) FROM set_log WHERE sessionId = :sessionId")
    suspend fun maxSuperset(sessionId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM set_log WHERE sessionId = :sessionId")
    suspend fun maxPosition(sessionId: Long): Int

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun exercise(id: Long): ExerciseEntity?

    @Query("SELECT * FROM workout_session WHERE status = 'done' AND endedMs IS NOT NULL AND startedMs >= :from ORDER BY startedMs")
    suspend fun doneSince(from: Long): List<WorkoutSessionEntity>

    @Query("UPDATE workout_session SET status = 'done', endedMs = :endedMs, feeling = :feeling WHERE id = :id")
    suspend fun finish(id: Long, endedMs: Long, feeling: Int?)

    @Query("UPDATE workout_session SET feeling = :feeling WHERE id = :id")
    suspend fun setFeeling(id: Long, feeling: Int?)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    /** The same set number from the last time this exercise was done (finished sessions only). */
    @Query(
        "SELECT l.* FROM set_log l JOIN workout_session s ON s.id = l.sessionId " +
            "WHERE l.exerciseId = :exerciseId AND l.setIndex = :setIndex AND l.done = 1 AND s.status = 'done' " +
            "AND l.sessionId != :excludeSession ORDER BY l.doneAtMs DESC LIMIT 1",
    )
    suspend fun previous(exerciseId: Long, setIndex: Int, excludeSession: Long): SetLogEntity?

    /** Every finished set of this exercise from other sessions, for records. */
    @Query(
        "SELECT l.* FROM set_log l JOIN workout_session s ON s.id = l.sessionId " +
            "WHERE l.exerciseId = :exerciseId AND l.done = 1 AND s.status = 'done' AND l.sessionId != :excludeSession " +
            "AND l.weightKg IS NOT NULL AND l.reps IS NOT NULL",
    )
    suspend fun history(exerciseId: Long, excludeSession: Long): List<SetLogEntity>

    @Query(
        "SELECT l.exerciseId AS exerciseId, l.exerciseName AS exerciseName, MAX(l.weightKg) AS bestKg, " +
            "MAX(CASE WHEN l.reps <= 1 THEN l.weightKg ELSE l.weightKg * (1 + l.reps / 30.0) END) AS bestE1rm, " +
            "COUNT(DISTINCT l.sessionId) AS sessions, MAX(l.doneAtMs) AS lastMs " +
            "FROM set_log l JOIN workout_session s ON s.id = l.sessionId " +
            "WHERE l.done = 1 AND s.status = 'done' AND l.durationSec IS NULL AND l.weightKg > 0 AND l.reps > 0 " +
            "GROUP BY l.exerciseId ORDER BY lastMs DESC",
    )
    fun observeRecords(): Flow<List<RecordRow>>

    @Query(
        "SELECT l.sessionId AS sessionId, s.startedMs AS startedMs, l.setIndex AS setIndex, l.weightKg AS weightKg, l.reps AS reps " +
            "FROM set_log l JOIN workout_session s ON s.id = l.sessionId " +
            "WHERE l.exerciseId = :exerciseId AND l.done = 1 AND s.status = 'done' AND l.weightKg IS NOT NULL AND l.reps IS NOT NULL " +
            "ORDER BY s.startedMs DESC, l.setIndex",
    )
    suspend fun exerciseHistory(exerciseId: Long): List<HistoryRow>
}

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(row: FoodEntity): Long

    @Update
    suspend fun update(row: FoodEntity)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: Long): FoodEntity?

    @Query("SELECT * FROM food WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM food WHERE name LIKE '%' || :text || '%' OR brand LIKE '%' || :text || '%' ORDER BY (source = 'custom') DESC, name LIMIT 60")
    suspend fun search(text: String): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM food")
    suspend fun count(): Int
}

@Dao
interface DiaryDao {
    @Insert
    suspend fun insert(row: DiaryEntryEntity): Long

    @Insert
    suspend fun insertAll(rows: List<DiaryEntryEntity>)

    @Query("SELECT * FROM diary_entry WHERE date = :date ORDER BY createdMs, id")
    fun observeDay(date: String): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entry WHERE date = :date ORDER BY createdMs, id")
    suspend fun day(date: String): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_entry WHERE id = :id")
    suspend fun get(id: Long): DiaryEntryEntity?

    @Query("UPDATE diary_entry SET grams = :grams, nutrients = :nutrients, meal = :meal WHERE id = :id")
    suspend fun update(id: Long, grams: Double?, nutrients: String, meal: String)

    @Query("DELETE FROM diary_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM diary_entry WHERE date = :date AND recurringId = :recurringId")
    suspend fun countForRule(date: String, recurringId: Long): Int

    @Query("SELECT * FROM diary_entry WHERE date >= :from AND date <= :to ORDER BY date, createdMs")
    suspend fun between(from: String, to: String): List<DiaryEntryEntity>
}

@Dao
interface RecurringDao {
    @Insert
    suspend fun insert(row: RecurringFoodEntity): Long

    @Query("SELECT * FROM recurring_food WHERE active = 1 AND fromDate <= :date")
    suspend fun activeOn(date: String): List<RecurringFoodEntity>

    @Query("SELECT * FROM recurring_food ORDER BY id")
    suspend fun all(): List<RecurringFoodEntity>

    @Query("UPDATE recurring_food SET active = 0 WHERE id = :id")
    suspend fun stop(id: Long)
}

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: CheckInEntity)

    @Query("SELECT * FROM check_in WHERE date = :date")
    fun observe(date: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_in WHERE date = :date")
    suspend fun forDate(date: String): List<CheckInEntity>
}
