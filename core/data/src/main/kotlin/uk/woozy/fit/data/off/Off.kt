package uk.woozy.fit.data.off

import org.json.JSONObject
import uk.woozy.fit.model.NutrientMap
import uk.woozy.fit.model.Nutrients

/** What Open Food Facts told us about one product, per 100 g. */
data class FoodDraft(
    val name: String,
    val brand: String,
    val barcode: String?,
    val servingG: Double?,
    val servingLabel: String,
    val per100: NutrientMap,
    /** True when there was no energy figure, so totals for this food may be low. */
    val incomplete: Boolean,
)

/** Reads Open Food Facts JSON. Tolerant: missing or odd fields give an incomplete food, never a crash. */
object OffParser {
    /** One product response from /api/v2/product/{code}. Null when the product is not found. */
    fun product(json: String): FoodDraft? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("status", 1) == 0 && !root.has("product")) return null
        val p = root.optJSONObject("product") ?: return null
        return draft(p, fallbackCode = root.optString("code").ifBlank { null })
    }

    /** A search response: a list of products. */
    fun search(json: String): List<FoodDraft> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("products") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { draft(it, null) } }
    }

    private fun draft(p: JSONObject, fallbackCode: String?): FoodDraft? {
        val name = p.optString("product_name").ifBlank { p.optString("generic_name") }.trim()
        if (name.isEmpty()) return null
        val n = p.optJSONObject("nutriments")
        fun g(key: String): Double? = n?.takeIf { it.has(key) && !it.isNull(key) }?.optDouble(key)?.takeIf { !it.isNaN() }
        val per = LinkedHashMap<String, Double>()
        val kcal = g("energy-kcal_100g") ?: g("energy_100g")?.let { it / 4.184 } // kJ to kcal when only kJ is given
        kcal?.let { per[Nutrients.ENERGY] = it }
        g("proteins_100g")?.let { per[Nutrients.PROTEIN] = it }
        g("carbohydrates_100g")?.let { per[Nutrients.CARBS] = it }
        g("fiber_100g")?.let { per[Nutrients.FIBRE] = it }
        g("sugars_100g")?.let { per[Nutrients.SUGARS] = it }
        g("fat_100g")?.let { per[Nutrients.FAT] = it }
        g("saturated-fat_100g")?.let { per[Nutrients.SAT_FAT] = it }
        // Open Food Facts gives sodium in grams; we hold it in milligrams.
        (g("sodium_100g") ?: g("salt_100g")?.let { it / 2.5 })?.let { per[Nutrients.SODIUM] = it * 1000.0 }
        val serving = p.optString("serving_quantity").toDoubleOrNull()?.takeIf { it > 0 }
        return FoodDraft(
            name = name,
            brand = p.optString("brands").split(',').firstOrNull().orEmpty().trim(),
            barcode = p.optString("code").ifBlank { fallbackCode },
            servingG = serving,
            servingLabel = p.optString("serving_size"),
            per100 = per,
            incomplete = kcal == null,
        )
    }
}

/** Keeps calls to Open Food Facts under their limits: at most [limit] calls in any [windowMs]. Pure, so it is tested. */
class RateLimiter(private val limit: Int, private val windowMs: Long) {
    private val stamps = ArrayDeque<Long>()

    /** Returns 0 when a call may go now (and counts it), else how many ms to wait. */
    @Synchronized
    fun reserve(nowMs: Long): Long {
        while (stamps.isNotEmpty() && nowMs - stamps.first() >= windowMs) stamps.removeFirst()
        if (stamps.size < limit) {
            stamps.addLast(nowMs)
            return 0L
        }
        return windowMs - (nowMs - stamps.first())
    }
}
