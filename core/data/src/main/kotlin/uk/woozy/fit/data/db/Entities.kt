package uk.woozy.fit.data.db

import androidx.room.Entity
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
