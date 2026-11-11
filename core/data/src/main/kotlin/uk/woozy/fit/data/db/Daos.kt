package uk.woozy.fit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(row: WaterEntryEntity): Long

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_entry WHERE epochMs >= :from AND epochMs < :to")
    suspend fun totalBetween(from: Long, to: Long): Int
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
