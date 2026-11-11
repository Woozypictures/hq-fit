package uk.woozy.fit.model

/**
 * The "highlighted nutrients" on Today, like Cronometer's second summary page. Reference intakes are adult-male
 * daily values (the same set Cronometer uses by default); a target you set in Targets replaces the default.
 * [upper] is the tolerable upper intake from food and supplements combined; above it the bar turns red.
 * Null means no upper limit worth warning about (B12, potassium and fibre from food).
 */
data class Micro(val key: String, val label: String, val unit: String, val reference: Double, val upper: Double?)

object Micros {
    val highlighted = listOf(
        Micro("fibre", "Fibre", "g", 38.0, null),
        Micro("vit_c", "Vitamin C", "mg", 90.0, 2000.0),
        Micro("iron", "Iron", "mg", 8.0, 45.0),
        Micro("vit_b12", "B12", "mcg", 2.4, null),
        Micro("calcium", "Calcium", "mg", 1000.0, 2500.0),
        Micro("folate", "Folate", "mcg", 400.0, 1000.0),
        Micro("vit_a", "Vitamin A", "mcg", 900.0, 3000.0),
        Micro("potassium", "Potassium", "mg", 3400.0, null),
    )

    /** Share of the target eaten (1.0 = 100 %), and whether it is over the upper limit (or a "stay under" target). */
    fun status(m: Micro, eaten: Double, target: Double?, targetIsMax: Boolean): Pair<Double, Boolean> {
        val t = (target ?: m.reference).coerceAtLeast(0.0001)
        val over = if (targetIsMax) eaten > t else m.upper != null && eaten > m.upper
        return eaten / t to over
    }
}
