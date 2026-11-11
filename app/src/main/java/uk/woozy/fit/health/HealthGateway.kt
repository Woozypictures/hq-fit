package uk.woozy.fit.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** What Health Connect says about today, for the Today screen and Settings. */
data class HealthSnapshot(
    val steps: Long?,
    val sleepMinutes: Long?,
    val restingHr: Long?,
    val activeKcal: Double?,
)

/** A weight another app wrote to Health Connect. */
data class ImportedWeight(val epochMs: Long, val kg: Double)

/**
 * Health Connect reads and writes. Every write carries a clientRecordId built from the HQ Fit row, so sending the
 * same row twice updates it instead of duplicating it (meals and workouts carry a rising clientRecordVersion, otherwise an edit would be ignored). Reads skip anything HQ Fit wrote itself.
 * Food, water, weights and workouts are written. Not yet tried on a phone.
 */
class HealthGateway(private val context: Context) {
    private val own = context.packageName

    val available: Boolean get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    companion object {
        val readPermissions: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
        )
        val writePermissions: Set<String> = setOf(
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getWritePermission(HydrationRecord::class),
        )
        val allPermissions: Set<String> = readPermissions + writePermissions
    }

    suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun snapshot(dayStart: Instant, now: Instant): HealthSnapshot {
        val g = granted()
        fun ok(p: String) = p in g
        val range = TimeRangeFilter.between(dayStart, now)
        // Aggregate, not a sum of records: Health Connect removes the overlap when the phone and the watch both count
        // the same steps (HQ reads it the same way, so both apps show the same number).
        val steps = if (ok(HealthPermission.getReadPermission(StepsRecord::class)))
            runCatching { client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] }.getOrNull() else null
        val sleep = if (ok(HealthPermission.getReadPermission(SleepSessionRecord::class)))
            runCatching {
                val from = dayStart.minus(Duration.ofHours(12))
                client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, now))).records
                    .filter { it.metadata.dataOrigin.packageName != own }
                    .maxByOrNull { it.endTime }?.let { Duration.between(it.startTime, it.endTime).toMinutes() }
            }.getOrNull() else null
        val hr = if (ok(HealthPermission.getReadPermission(RestingHeartRateRecord::class)))
            runCatching {
                val from = now.minus(Duration.ofDays(3))
                client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(from, now))).records
                    .filter { it.metadata.dataOrigin.packageName != own }
                    .maxByOrNull { it.time }?.beatsPerMinute
            }.getOrNull() else null
        val kcal = if (ok(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)))
            runCatching {
                client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            }.getOrNull() else null
        return HealthSnapshot(steps, sleep, hr, kcal)
    }

    /** Weight history written by other apps (Feelfit, Renpho and so on), oldest first. */
    suspend fun importedWeights(since: Instant, until: Instant): List<ImportedWeight> {
        if (HealthPermission.getReadPermission(WeightRecord::class) !in granted()) return emptyList()
        val out = ArrayList<ImportedWeight>()
        var token: String? = null
        do {
            val res = client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(since, until), pageToken = token))
            res.records.filter { it.metadata.dataOrigin.packageName != own }
                .forEach { out += ImportedWeight(it.time.toEpochMilli(), it.weight.inKilograms) }
            token = res.pageToken
        } while (token != null)
        return out.sortedBy { it.epochMs }
    }

    suspend fun writeWeight(rowId: Long, epochMs: Long, kg: Double) {
        val t = Instant.ofEpochMilli(epochMs)
        client.insertRecords(
            listOf(
                WeightRecord(
                    time = t, zoneOffset = ZoneId.systemDefault().rules.getOffset(t), weight = Mass.kilograms(kg),
                    metadata = Metadata(clientRecordId = "hqfit-weight-$rowId"),
                ),
            ),
        )
    }

    suspend fun writeWorkout(sessionId: Long, name: String, startMs: Long, endMs: Long) {
        val s = Instant.ofEpochMilli(startMs)
        val e = Instant.ofEpochMilli(maxOf(endMs, startMs + 60_000))
        val zone = ZoneId.systemDefault().rules
        client.insertRecords(
            listOf(
                ExerciseSessionRecord(
                    startTime = s, startZoneOffset = zone.getOffset(s), endTime = e, endZoneOffset = zone.getOffset(e),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, title = name,
                    metadata = Metadata(clientRecordId = "hqfit-session-$sessionId", clientRecordVersion = System.currentTimeMillis()),
                ),
            ),
        )
    }

    suspend fun writeMeal(entryId: Long, name: String, meal: String, atMs: Long, n: Map<String, Double>) {
        val s = Instant.ofEpochMilli(atMs)
        val e = s.plusSeconds(60)
        val zone = ZoneId.systemDefault().rules
        fun g(key: String): Mass? = n[key]?.takeIf { it > 0 }?.let { Mass.grams(it) }
        client.insertRecords(
            listOf(
                NutritionRecord(
                    startTime = s, startZoneOffset = zone.getOffset(s), endTime = e, endZoneOffset = zone.getOffset(e),
                    metadata = Metadata(clientRecordId = "hqfit-meal-$entryId", clientRecordVersion = System.currentTimeMillis()),
                    name = name,
                    mealType = when (meal) {
                        "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
                        "lunch" -> MealType.MEAL_TYPE_LUNCH
                        "dinner" -> MealType.MEAL_TYPE_DINNER
                        else -> MealType.MEAL_TYPE_SNACK
                    },
                    energy = n["energy"]?.takeIf { it > 0 }?.let { Energy.kilocalories(it) },
                    protein = g("protein"), totalCarbohydrate = g("carbs"), dietaryFiber = g("fibre"),
                    totalFat = g("fat"), saturatedFat = g("sat_fat"), sugar = g("sugars"),
                    sodium = n["sodium"]?.takeIf { it > 0 }?.let { Mass.milligrams(it) },
                ),
            ),
        )
    }

    suspend fun writeWater(rowId: Long, atMs: Long, ml: Int) {
        val s = Instant.ofEpochMilli(atMs)
        val e = s.plusSeconds(1)
        val zone = ZoneId.systemDefault().rules
        client.insertRecords(
            listOf(
                HydrationRecord(
                    startTime = s, startZoneOffset = zone.getOffset(s), endTime = e, endZoneOffset = zone.getOffset(e),
                    volume = Volume.milliliters(ml.toDouble()), metadata = Metadata(clientRecordId = "hqfit-water-$rowId"),
                ),
            ),
        )
    }

    suspend fun deleteMeals(entryIds: List<Long>) {
        if (entryIds.isEmpty()) return
        client.deleteRecords(NutritionRecord::class, emptyList(), entryIds.map { "hqfit-meal-$it" })
    }

    suspend fun deleteWater(rowId: Long) {
        client.deleteRecords(HydrationRecord::class, emptyList(), listOf("hqfit-water-$rowId"))
    }

    suspend fun deleteWeights(rowIds: List<Long>) {
        if (rowIds.isEmpty()) return
        client.deleteRecords(WeightRecord::class, emptyList(), rowIds.map { "hqfit-weight-$it" })
    }
}
