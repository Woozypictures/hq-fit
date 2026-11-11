package uk.woozy.fit.data.db

import uk.woozy.fit.model.FullBodyProgramme
import uk.woozy.fit.model.Scheduler
import uk.woozy.fit.model.Targets
import java.io.BufferedReader
import java.time.LocalDate

/** First-run data. Safe to run again: rows are replaced by key, and the version row stops repeat work. */
object Seed {
    const val VERSION = "1"

    // key, name, unit, group, Health Connect NutritionRecord field
    private val nutrientRows = listOf(
        Row("energy", "Energy", "kcal", "General", "energy"),
        Row("protein", "Protein", "g", "Macros", "protein"),
        Row("carbs", "Carbohydrate", "g", "Macros", "totalCarbohydrate"),
        Row("fibre", "Fibre", "g", "Macros", "dietaryFiber"),
        Row("sugars", "Sugars", "g", "Macros", "sugar"),
        Row("fat", "Fat", "g", "Macros", "totalFat"),
        Row("sat_fat", "Saturated fat", "g", "Fats", "saturatedFat"),
        Row("mono_fat", "Monounsaturated fat", "g", "Fats", "monounsaturatedFat"),
        Row("poly_fat", "Polyunsaturated fat", "g", "Fats", "polyunsaturatedFat"),
        Row("trans_fat", "Trans fat", "g", "Fats", "transFat"),
        Row("cholesterol", "Cholesterol", "mg", "Fats", "cholesterol"),
        Row("sodium", "Sodium", "mg", "Minerals", "sodium"),
        Row("potassium", "Potassium", "mg", "Minerals", "potassium"),
        Row("calcium", "Calcium", "mg", "Minerals", "calcium"),
        Row("iron", "Iron", "mg", "Minerals", "iron"),
        Row("magnesium", "Magnesium", "mg", "Minerals", "magnesium"),
        Row("zinc", "Zinc", "mg", "Minerals", "zinc"),
        Row("phosphorus", "Phosphorus", "mg", "Minerals", "phosphorus"),
        Row("selenium", "Selenium", "mcg", "Minerals", "selenium"),
        Row("copper", "Copper", "mg", "Minerals", "copper"),
        Row("manganese", "Manganese", "mg", "Minerals", "manganese"),
        Row("iodine", "Iodine", "mcg", "Minerals", "iodine"),
        Row("vit_a", "Vitamin A", "mcg", "Vitamins", "vitaminA"),
        Row("vit_c", "Vitamin C", "mg", "Vitamins", "vitaminC"),
        Row("vit_d", "Vitamin D", "mcg", "Vitamins", "vitaminD"),
        Row("vit_e", "Vitamin E", "mg", "Vitamins", "vitaminE"),
        Row("vit_k", "Vitamin K", "mcg", "Vitamins", "vitaminK"),
        Row("thiamin", "Thiamin (B1)", "mg", "Vitamins", "thiamin"),
        Row("riboflavin", "Riboflavin (B2)", "mg", "Vitamins", "riboflavin"),
        Row("niacin", "Niacin (B3)", "mg", "Vitamins", "niacin"),
        Row("pantothenic", "Pantothenic acid (B5)", "mg", "Vitamins", "pantothenicAcid"),
        Row("vit_b6", "Vitamin B6", "mg", "Vitamins", "vitaminB6"),
        Row("biotin", "Biotin (B7)", "mcg", "Vitamins", "biotin"),
        Row("folate", "Folate (B9)", "mcg", "Vitamins", "folate"),
        Row("vit_b12", "Vitamin B12", "mcg", "Vitamins", "vitaminB12"),
        Row("caffeine", "Caffeine", "mg", "Other", "caffeine"),
        Row("water", "Water", "ml", "Other", null),
        Row("alcohol", "Alcohol", "g", "Other", null),
    )

    private data class Row(val key: String, val name: String, val unit: String, val grp: String, val hc: String?)

    val nutrients: List<NutrientEntity> =
        nutrientRows.mapIndexed { i, r -> NutrientEntity(r.key, r.name, r.unit, r.grp, r.hc, i) }

    fun targets(t: Targets = Targets()): List<TargetEntity> = listOf(
        TargetEntity("energy", t.energyKcal.toDouble(), "goal"),
        TargetEntity("protein", t.proteinG.toDouble(), "goal"),
        TargetEntity("carbs", t.netCarbsG.toDouble(), "goal"),
        TargetEntity("fat", t.fatG.toDouble(), "goal"),
        TargetEntity("steps", t.steps.toDouble(), "goal"),
        TargetEntity("water", t.waterMl.toDouble(), "goal"),
        TargetEntity("weight_goal_kg", t.goalWeightKg, "goal"),
        TargetEntity("weight_start_kg", t.startWeightKg, "goal"),
        TargetEntity("weight_rate_kg_week", t.weeklyRateKg, "goal"),
    )

    /**
     * Fills what is missing. [catalogue] opens the bundled exercise list (null in tests that do not need it).
     * Each part has its own version row, so an update that adds seed data runs only the new part.
     */
    suspend fun run(db: HqFitDatabase, catalogue: (() -> BufferedReader)? = null, today: LocalDate = LocalDate.now()) {
        if (db.meta().get("seed") != VERSION) {
            db.nutrients().upsertAll(nutrients)
            // Targets are only filled when missing, so a seed re-run never overwrites something edited later.
            val have = db.targets().all().map { it.key }.toSet()
            db.targets().upsertAll(targets().filter { it.key !in have })
            db.meta().put(MetaEntity("seed", VERSION))
        }
        if (catalogue != null && db.meta().get("exercises") == null) {
            db.exercises().insertAll(ExerciseCatalogue.parse(catalogue()))
            db.meta().put(MetaEntity("exercises", "1"))
        }
        if (db.meta().get("programme") == null) {
            seedProgramme(db, today)
            db.meta().put(MetaEntity("programme", "1"))
        }
    }

    /** The Full body A / B / C programme from the Everfit handover, scheduled Mon, Wed, Fri from the Monday of [today]'s week. */
    suspend fun seedProgramme(db: HqFitDatabase, today: LocalDate) {
        val idByName = HashMap<String, Long>()
        for (w in FullBodyProgramme.workouts) {
            val templateId = db.templates().insert(WorkoutTemplateEntity(name = w.name, focus = w.focus))
            val items = w.items.mapIndexed { i, item ->
                val exerciseId = idByName.getOrPut(item.exercise.lowercase()) {
                    db.exercises().findByName(item.exercise)?.id
                        ?: db.exercises().insert(ExerciseEntity(name = item.exercise, muscle = item.muscle, equipment = item.equipment))
                }
                TemplateItemEntity(
                    templateId = templateId, section = item.section.key, position = i, exerciseId = exerciseId,
                    sets = item.sets, repsMin = item.repsMin, repsMax = item.repsMax, durationSec = item.durationSec,
                    weightKg = item.weightKg, restSec = item.restSec, supersetGroup = item.superset, note = item.note,
                )
            }
            db.templates().insertItems(items)
        }
        val templates = db.templates().all().associateBy { it.name }
        val programmeId = db.programmes().insert(
            ProgrammeEntity(name = FullBodyProgramme.NAME, startDate = today.with(java.time.DayOfWeek.MONDAY).toString(), weeks = FullBodyProgramme.WEEKS, active = true),
        )
        db.programmes().insertDays(
            FullBodyProgramme.rules.map { ProgrammeDayEntity(programmeId = programmeId, weekday = it.weekday.value, templateId = templates.getValue(it.workout).id) },
        )
        // From today on: weeks already trained in Everfit are not recreated as missed days.
        val days = Scheduler.generate(today, FullBodyProgramme.WEEKS, FullBodyProgramme.rules)
        db.schedule().insertAll(
            days.map {
                val t = templates.getValue(it.workout)
                ScheduledWorkoutEntity(programmeId, t.id, t.name, it.date.toString(), it.date.toString())
            },
        )
    }
}
