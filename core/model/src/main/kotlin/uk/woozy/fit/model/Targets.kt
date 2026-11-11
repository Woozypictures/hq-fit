package uk.woozy.fit.model

/**
 * Starting targets, taken from the Cronometer and Everfit settings in use today.
 * They become editable in Settings once the food diary lands (milestone 5).
 */
data class Targets(
    val energyKcal: Int = 2297,
    val proteinG: Int = 150,
    val netCarbsG: Int = 260,
    val fatG: Int = 70,
    val steps: Int = 8500,
    val startWeightKg: Double = 87.4,
    val goalWeightKg: Double = 80.0,
    val weeklyRateKg: Double = 0.454,
    val waterGlasses: Int = 8,
    val waterGlassMl: Int = 250,
) {
    /** Energy the fixed macro grams add up to, at 4 / 4 / 9 kcal per gram. */
    val macroKcal: Int get() = proteinG * 4 + netCarbsG * 4 + fatG * 9

    /** Daily water target in millilitres. */
    val waterMl: Int get() = waterGlasses * waterGlassMl
}
