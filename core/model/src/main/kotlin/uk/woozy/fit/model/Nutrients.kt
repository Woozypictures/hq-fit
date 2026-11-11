package uk.woozy.fit.model

/** Nutrient amounts by key (see the seeded nutrient list), in the unit of that nutrient: g, mg, mcg or kcal. */
typealias NutrientMap = Map<String, Double>

object Nutrients {
    const val ENERGY = "energy"
    const val PROTEIN = "protein"
    const val CARBS = "carbs"
    const val FIBRE = "fibre"
    const val SUGARS = "sugars"
    const val FAT = "fat"
    const val SAT_FAT = "sat_fat"
    const val SODIUM = "sodium"

    fun encode(map: NutrientMap): String =
        map.entries.filter { it.value != 0.0 }.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    /** Ignores malformed pairs rather than failing: a bad stored value must never crash the diary. */
    fun decode(text: String): NutrientMap {
        if (text.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, Double>()
        text.split(';').forEach { pair ->
            val i = pair.indexOf('=')
            if (i > 0) pair.substring(i + 1).toDoubleOrNull()?.let { out[pair.substring(0, i)] = it }
        }
        return out
    }

    /** Amounts for [grams] of a food given per 100 g. */
    fun scale(per100g: NutrientMap, grams: Double): NutrientMap = per100g.mapValues { it.value * grams / 100.0 }

    fun sum(items: List<NutrientMap>): NutrientMap {
        val out = LinkedHashMap<String, Double>()
        items.forEach { m -> m.forEach { (k, v) -> out[k] = (out[k] ?: 0.0) + v } }
        return out
    }

    /** Carbohydrate minus fibre, never below zero: the "net carbs" your target is set in. */
    fun netCarbs(m: NutrientMap): Double = ((m[CARBS] ?: 0.0) - (m[FIBRE] ?: 0.0)).coerceAtLeast(0.0)

    /** Energy from the three macros at 4, 4 and 9 kcal per gram, for foods entered without an energy figure. */
    fun energyFromMacros(m: NutrientMap): Double =
        (m[PROTEIN] ?: 0.0) * 4 + netCarbs(m) * 4 + (m[FAT] ?: 0.0) * 9
}

enum class Meal(val key: String, val title: String) {
    BREAKFAST("breakfast", "Breakfast"),
    LUNCH("lunch", "Lunch"),
    DINNER("dinner", "Dinner"),
    SNACKS("snacks", "Snacks"),
    SUPPLEMENTS("supplements", "Supplements"),
    ;

    companion object {
        fun fromKey(key: String): Meal = entries.firstOrNull { it.key == key } ?: SNACKS
    }
}
