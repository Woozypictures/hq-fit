package uk.woozy.fit.data.db

import uk.woozy.fit.model.Meal
import uk.woozy.fit.model.NutrientMap
import uk.woozy.fit.model.Nutrients

/** Logging food. Everything eaten is stored as a snapshot of the amounts at that moment. */
object Diary {
    fun perHundred(food: FoodEntity): NutrientMap = Nutrients.decode(food.per100)

    /** Logs [grams] of [food]. Energy is worked out from the macros when the food has none. */
    suspend fun log(db: HqFitDatabase, food: FoodEntity, grams: Double, date: String, meal: Meal, nowMs: Long, recurringId: Long? = null): Long {
        val eaten = withEnergy(Nutrients.scale(perHundred(food), grams))
        return db.diary().insert(
            DiaryEntryEntity(
                date = date, meal = meal.key, foodId = food.id, name = food.name, grams = grams,
                nutrients = Nutrients.encode(eaten), createdMs = nowMs, recurringId = recurringId,
            ),
        )
    }

    /** A quick add: a name and whatever figures you know, no food behind it. */
    suspend fun quickAdd(db: HqFitDatabase, name: String, nutrients: NutrientMap, date: String, meal: Meal, nowMs: Long): Long =
        db.diary().insert(
            DiaryEntryEntity(
                date = date, meal = meal.key, foodId = null, name = name, grams = null,
                nutrients = Nutrients.encode(withEnergy(nutrients)), createdMs = nowMs,
            ),
        )

    /**
     * Changes the amount of a logged food by scaling the entry's own snapshot, so a later edit of the food itself
     * never leaks into this past entry. A quick add has no amount, so only its meal can change.
     */
    suspend fun changeAmount(db: HqFitDatabase, entryId: Long, grams: Double, meal: Meal) {
        val entry = db.diary().get(entryId) ?: return
        val old = entry.grams
        if (old == null || old <= 0.0 || grams <= 0.0) {
            db.diary().update(entryId, old, entry.nutrients, meal.key)
            return
        }
        val factor = grams / old
        val scaled = Nutrients.decode(entry.nutrients).mapValues { it.value * factor }
        db.diary().update(entryId, grams, Nutrients.encode(scaled), meal.key)
    }

    /** Adds today's automatic entries once per rule per day. Safe to call every time the diary opens. */
    suspend fun applyRepeats(db: HqFitDatabase, date: String, nowMs: Long) {
        db.recurring().activeOn(date).forEach { rule ->
            if (db.diary().countForRule(date, rule.id) == 0) {
                val food = db.foods().get(rule.foodId) ?: return@forEach
                log(db, food, rule.grams, date, Meal.fromKey(rule.meal), nowMs, recurringId = rule.id)
            }
        }
    }

    fun totals(entries: List<DiaryEntryEntity>): NutrientMap = Nutrients.sum(entries.map { Nutrients.decode(it.nutrients) })

    private fun withEnergy(m: NutrientMap): NutrientMap =
        if ((m[Nutrients.ENERGY] ?: 0.0) > 0.0) m else m + (Nutrients.ENERGY to Nutrients.energyFromMacros(m))
}
