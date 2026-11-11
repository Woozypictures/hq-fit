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

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int
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
