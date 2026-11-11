package uk.woozy.fit.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The encrypted database. Version 1 holds the foundations; each later milestone adds its tables with a
 * migration, and the exported schema JSON under core/data/schemas shows every step.
 */
/** The Room schema version. Backups record it, so there is one place to bump. */
const val DB_VERSION = 6

@Database(
    entities = [
        NutrientEntity::class,
        TargetEntity::class,
        ExerciseEntity::class,
        WeightEntryEntity::class,
        WaterEntryEntity::class,
        MetaEntity::class,
        WorkoutTemplateEntity::class,
        TemplateItemEntity::class,
        ProgrammeEntity::class,
        ProgrammeDayEntity::class,
        ScheduledWorkoutEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        FoodEntity::class,
        DiaryEntryEntity::class,
        RecurringFoodEntity::class,
        CheckInEntity::class,
    ],
    version = DB_VERSION,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
    exportSchema = true,
)
abstract class HqFitDatabase : RoomDatabase() {
    abstract fun nutrients(): NutrientDao
    abstract fun targets(): TargetDao
    abstract fun exercises(): ExerciseDao
    abstract fun weights(): WeightDao
    abstract fun water(): WaterDao
    abstract fun meta(): MetaDao
    abstract fun templates(): TemplateDao
    abstract fun programmes(): ProgrammeDao
    abstract fun schedule(): ScheduleDao
    abstract fun sessions(): SessionDao
    abstract fun foods(): FoodDao
    abstract fun diary(): DiaryDao
    abstract fun recurring(): RecurringDao
    abstract fun checkIns(): CheckInDao
}
