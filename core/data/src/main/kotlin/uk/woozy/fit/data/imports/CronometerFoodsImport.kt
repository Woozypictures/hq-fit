package uk.woozy.fit.data.imports

import uk.woozy.fit.data.db.FoodEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.model.Nutrients

/**
 * Adds the custom foods copied from the Cronometer website. The file holds figures per serving (tab separated: name,
 * serving text, serving grams or 0, kcal, fat, sat fat, carbs, sugars, fibre, protein, sodium mg). Where Cronometer had
 * no gram weight, one serving is stored as a 100 g unit so the figures are still exact per serving. Safe to run twice.
 */
object CronometerFoodsImport {
    suspend fun import(db: HqFitDatabase, text: String): Int {
        val dao = db.foods()
        var added = 0
        for (line in text.lines()) {
            if (line.isBlank()) continue
            val f = line.split('\t')
            if (f.size < 11) continue
            val name = f[0].trim()
            if (dao.countCustom(name) > 0) continue
            val grams = f[2].toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0
            val known = f[2].toDoubleOrNull()?.let { it > 0 } == true
            fun v(i: Int): Double? = f[i].toDoubleOrNull()
            val keys = listOf(Nutrients.ENERGY, Nutrients.FAT, Nutrients.SAT_FAT, Nutrients.CARBS, Nutrients.SUGARS, Nutrients.FIBRE, Nutrients.PROTEIN, Nutrients.SODIUM)
            val per = LinkedHashMap<String, Double>()
            keys.forEachIndexed { k, key -> v(3 + k)?.let { per[key] = it * 100.0 / grams } }
            val incomplete = v(4) == null && v(6) == null && v(9) == null
            dao.insert(
                FoodEntity(
                    name = name, brand = "", source = "custom", servingG = grams,
                    servingLabel = if (known) f[1].trim() else "1 serving",
                    per100 = Nutrients.encode(per), incomplete = incomplete, createdMs = System.currentTimeMillis(),
                ),
            )
            added++
        }
        return added
    }
}
