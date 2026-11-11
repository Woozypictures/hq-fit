package uk.woozy.fit.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The encrypted database. Version 1 holds the foundations; each later milestone adds its tables with a
 * migration, and the exported schema JSON under core/data/schemas shows every step.
 */
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
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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
}
