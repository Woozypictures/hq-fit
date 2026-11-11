package uk.woozy.fit.data.imports

import uk.woozy.fit.data.db.DiaryEntryEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.model.Meal
import uk.woozy.fit.model.NutrientMap
import uk.woozy.fit.model.Nutrients

/**
 * Reads Cronometer's "daily summary" export. That file has whole-day totals only; the "servings" export lists food
 * names and amounts with no nutrient figures, so food-level history cannot be rebuilt from it. Each logged day
 * becomes one diary entry holding that day's totals, which keeps charts, averages and the history intact.
 */
object CronometerImport {
    /** Name of the imported day entries. These are not sent to Health Connect: Cronometer already put them there. */
    const val ENTRY_NAME = "Cronometer day total"

    class Day(val date: String, val nutrients: NutrientMap)

    // Cronometer column -> (our key, divisor to convert into our unit)
    private val columns: Map<String, Pair<String, Double>> = mapOf(
        "Energy (kcal)" to ("energy" to 1.0), "Protein (g)" to ("protein" to 1.0), "Carbs (g)" to ("carbs" to 1.0),
        "Fiber (g)" to ("fibre" to 1.0), "Sugars (g)" to ("sugars" to 1.0), "Fat (g)" to ("fat" to 1.0),
        "Saturated (g)" to ("sat_fat" to 1.0), "Monounsaturated (g)" to ("mono_fat" to 1.0),
        "Polyunsaturated (g)" to ("poly_fat" to 1.0), "Trans-Fats (g)" to ("trans_fat" to 1.0),
        "Cholesterol (mg)" to ("cholesterol" to 1.0), "Sodium (mg)" to ("sodium" to 1.0), "Potassium (mg)" to ("potassium" to 1.0),
        "Calcium (mg)" to ("calcium" to 1.0), "Iron (mg)" to ("iron" to 1.0), "Magnesium (mg)" to ("magnesium" to 1.0),
        "Zinc (mg)" to ("zinc" to 1.0), "Phosphorus (mg)" to ("phosphorus" to 1.0), "Selenium (µg)" to ("selenium" to 1.0),
        "Copper (mg)" to ("copper" to 1.0), "Manganese (mg)" to ("manganese" to 1.0),
        "Vitamin A (µg)" to ("vit_a" to 1.0), "Vitamin C (mg)" to ("vit_c" to 1.0),
        "Vitamin D (IU)" to ("vit_d" to 40.0), // 40 IU = 1 mcg
        "Vitamin E (mg)" to ("vit_e" to 1.0), "Vitamin K (µg)" to ("vit_k" to 1.0),
        "B1 (Thiamine) (mg)" to ("thiamin" to 1.0), "B2 (Riboflavin) (mg)" to ("riboflavin" to 1.0), "B3 (Niacin) (mg)" to ("niacin" to 1.0),
        "B5 (Pantothenic Acid) (mg)" to ("pantothenic" to 1.0), "B6 (Pyridoxine) (mg)" to ("vit_b6" to 1.0),
        "Folate (µg)" to ("folate" to 1.0), "B12 (Cobalamin) (µg)" to ("vit_b12" to 1.0),
        "Caffeine (mg)" to ("caffeine" to 1.0), "Alcohol (g)" to ("alcohol" to 1.0),
    )

    /** Splits one CSV record, handling quoted fields. */
    fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                quoted && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /** Days that have an energy figure; empty days (not logged) are skipped. */
    fun parseDaily(text: String): List<Day> {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = splitCsv(lines.first())
        val dateAt = header.indexOf("Date")
        val energyAt = header.indexOf("Energy (kcal)")
        require(dateAt >= 0 && energyAt >= 0) { "That is not a Cronometer daily summary file" }
        val mapped = header.mapIndexedNotNull { i, h -> columns[h]?.let { Triple(i, it.first, it.second) } }
        return lines.drop(1).mapNotNull { l ->
            val f = splitCsv(l)
            val date = f.getOrNull(dateAt)?.trim().orEmpty()
            val energy = f.getOrNull(energyAt)?.toDoubleOrNull() ?: return@mapNotNull null
            if (energy <= 0.0 || date.length != 10) return@mapNotNull null
            val n = LinkedHashMap<String, Double>()
            mapped.forEach { (i, key, div) -> f.getOrNull(i)?.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { n[key] = it / div } }
            Day(date, n)
        }
    }

    /** Adds each day not already imported. Returns how many were added. Safe to run twice. */
    suspend fun import(db: HqFitDatabase, text: String, nowMs: Long): Int {
        val days = parseDaily(text)
        var added = 0
        days.forEach { d ->
            val existing = db.diary().day(d.date)
            if (existing.none { it.name == ENTRY_NAME }) {
                db.diary().insert(
                    DiaryEntryEntity(
                        date = d.date, meal = Meal.SNACKS.key, foodId = null, name = ENTRY_NAME, grams = null,
                        nutrients = Nutrients.encode(d.nutrients), createdMs = nowMs,
                    ),
                )
                added++
            }
        }
        return added
    }
}
