package uk.woozy.fit.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A tracked nutrient. [hcField] names the matching Health Connect NutritionRecord field, when there is one. */
@Entity(tableName = "nutrient")
data class NutrientEntity(
    @PrimaryKey val key: String,
    val name: String,
    val unit: String,
    val grp: String,
    val hcField: String?,
    val sort: Int,
)

/** One target per nutrient (or per other measure such as steps). [kind] is "goal" or "max". */
@Entity(tableName = "target")
data class TargetEntity(
    @PrimaryKey val key: String,
    val value: Double,
    val kind: String,
)

@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscle: String,
    val equipment: String,
    val instructions: String = "",
    val custom: Boolean = false,
)

@Entity(tableName = "weight_entry", indices = [Index("epochMs")])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMs: Long,
    val kg: Double,
    /** "manual", "scale" or "import". */
    val source: String,
)

@Entity(tableName = "water_entry", indices = [Index("epochMs")])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMs: Long,
    val ml: Int,
)

/** Small key-value rows for things like the seed version. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** A saved workout: sections and exercises live in [TemplateItemEntity]. */
@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val focus: String = "",
)

@Entity(
    tableName = "template_item",
    foreignKeys = [
        ForeignKey(WorkoutTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    /** Section key: warm_up, workout or cool_down. */
    val section: String,
    val position: Int,
    val exerciseId: Long,
    val sets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val weightKg: Double?,
    val restSec: Int,
    /** Items sharing a non-null group within a template are a superset. */
    val supersetGroup: Int?,
    val note: String,
)

@Entity(tableName = "programme")
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    val weeks: Int,
    val active: Boolean,
)

/** A weekday rule: [weekday] is 1 (Monday) to 7 (Sunday). */
@Entity(
    tableName = "programme_day",
    foreignKeys = [ForeignKey(ProgrammeEntity::class, parentColumns = ["id"], childColumns = ["programmeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("programmeId")],
)
data class ProgrammeDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programmeId: Long,
    val weekday: Int,
    val templateId: Long,
)

/**
 * One dated workout on the calendar. No foreign keys on purpose: deleting a programme or a template must never
 * remove a day that was already trained. [date] moves when rescheduled; [originalDate] never does.
 */
@Entity(tableName = "scheduled_workout", indices = [Index("date")])
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programmeId: Long?,
    val templateId: Long,
    val templateName: String,
    val date: String,
    val originalDate: String,
    /** planned, done or skipped. */
    val status: String = "planned",
)

/** One time you trained. Sets are written the moment they change, so a killed app resumes where it was. */
@Entity(tableName = "workout_session", indices = [Index("status")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledId: Long?,
    val templateId: Long?,
    val name: String,
    val startedMs: Long,
    val endedMs: Long? = null,
    /** active or done. */
    val status: String = "active",
    /** 1 to 5, how it felt. */
    val feeling: Int? = null,
)

/**
 * One set of one exercise inside a session. Rows are created when the session starts from the template,
 * with the name copied in so later edits to the library never change a past workout.
 */
@Entity(
    tableName = "set_log",
    foreignKeys = [ForeignKey(WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Order of the exercise within the session. */
    val position: Int,
    val setIndex: Int,
    val section: String,
    val exerciseId: Long,
    val exerciseName: String,
    val supersetGroup: Int?,
    /** Timed items (treadmill, stretches) use this instead of reps. */
    val durationSec: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val restSec: Int,
    val note: String,
    /** normal, warmup, drop or failure. */
    val setType: String = "normal",
    val weightKg: Double?,
    val reps: Int?,
    val done: Boolean = false,
    val doneAtMs: Long? = null,
)
