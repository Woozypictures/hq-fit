package uk.woozy.fit

import android.content.Intent
import uk.woozy.fit.data.db.DbGate
import uk.woozy.fit.data.db.DiaryEntryEntity
import uk.woozy.fit.data.db.WaterEntryEntity
import uk.woozy.fit.data.db.WeightEntryEntity
import uk.woozy.fit.model.Nutrients
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug builds only (never in the release APK): launched with the extra demo=true, skips the recovery key step and
 * fills a few days of food, water and weights so CI can take screenshots on an emulator at phone and tablet sizes.
 */
object Demo {
    fun wanted(i: Intent?) = i?.getBooleanExtra("demo", false) == true

    suspend fun start(c: AppContainer): DbGate {
        val p = c.database
        if (p.start() == DbGate.Ready) return DbGate.Ready
        p.createNew()
        p.confirm()
        val db = p.db ?: return DbGate.FirstRun
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val meals = listOf(
            Triple("breakfast", "Porridge with banana", mapOf(Nutrients.ENERGY to 420.0, Nutrients.PROTEIN to 18.0, Nutrients.CARBS to 66.0, Nutrients.FIBRE to 8.0, Nutrients.FAT to 9.0)),
            Triple("lunch", "Paneer wrap", mapOf(Nutrients.ENERGY to 610.0, Nutrients.PROTEIN to 34.0, Nutrients.CARBS to 58.0, Nutrients.FIBRE to 6.0, Nutrients.FAT to 24.0)),
            Triple("snack", "Greek yoghurt", mapOf(Nutrients.ENERGY to 180.0, Nutrients.PROTEIN to 20.0, Nutrients.CARBS to 9.0, Nutrients.FIBRE to 0.0, Nutrients.FAT to 6.0)),
        )
        for (back in 0L..13L) {
            val d = today.minusDays(back)
            val at = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli().coerceAtMost(now)
            meals.take(if (back == 0L) 3 else 3).forEachIndexed { i, (meal, name, n) ->
                val scale = 1.0 + ((back + i) % 4) * 0.12
                db.diary().insert(DiaryEntryEntity(date = d.toString(), meal = meal, foodId = null, name = name, grams = null,
                    nutrients = Nutrients.encode(n.mapValues { it.value * scale }), createdMs = at))
            }
            if (back % 3 == 0L) db.weights().insert(WeightEntryEntity(epochMs = at, kg = 87.4 - (13 - back) * 0.08, source = "manual"))
        }
        repeat(5) { db.water().insert(WaterEntryEntity(epochMs = now - it * 60_000L, ml = 250)) }
        return DbGate.Ready
    }
}
