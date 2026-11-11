package uk.woozy.fit.model

/** One finished set, for records and volume. */
data class SetPoint(val weightKg: Double, val reps: Int)

enum class RecordType(val label: String) {
    HEAVIEST("Heaviest weight"),
    ESTIMATED_1RM("Estimated 1RM"),
    SET_VOLUME("Best set volume"),
}

object Strength {
    /** Epley: weight x (1 + reps / 30). One rep is the weight itself. */
    fun epley(kg: Double, reps: Int): Double = if (reps <= 1) kg else kg * (1 + reps / 30.0)

    /** Brzycki: weight x 36 / (37 - reps). Only defined below 37 reps; falls back to Epley beyond that. */
    fun brzycki(kg: Double, reps: Int): Double = when {
        reps <= 1 -> kg
        reps >= 37 -> epley(kg, reps)
        else -> kg * 36.0 / (37 - reps)
    }

    fun e1rm(set: SetPoint): Double = epley(set.weightKg, set.reps)

    fun volume(sets: List<SetPoint>): Double = sets.sumOf { it.weightKg * it.reps }

    /**
     * Records this exercise's [today] sets break compared with [history]. With no history there is nothing to
     * beat, so nothing is flagged: the first time you do a lift is a baseline, not a record.
     * Only sets with a weight above zero count.
     */
    fun records(history: List<SetPoint>, today: List<SetPoint>): Set<RecordType> {
        val past = history.filter { it.weightKg > 0 && it.reps > 0 }
        val now = today.filter { it.weightKg > 0 && it.reps > 0 }
        if (past.isEmpty() || now.isEmpty()) return emptySet()
        val out = LinkedHashSet<RecordType>()
        if (now.maxOf { it.weightKg } > past.maxOf { it.weightKg }) out += RecordType.HEAVIEST
        if (now.maxOf { e1rm(it) } > past.maxOf { e1rm(it) }) out += RecordType.ESTIMATED_1RM
        if (now.maxOf { it.weightKg * it.reps } > past.maxOf { it.weightKg * it.reps }) out += RecordType.SET_VOLUME
        return out
    }
}

/** Typing numbers on our own keypad. Pure so it can be tested. */
object NumberEntry {
    private const val MAX_LENGTH = 7

    fun digit(text: String, d: Char): String {
        if (text.length >= MAX_LENGTH) return text
        return if (text == "0") d.toString() else text + d
    }

    fun dot(text: String): String = when {
        '.' in text || text.length >= MAX_LENGTH -> text
        text.isEmpty() -> "0."
        else -> "$text."
    }

    fun backspace(text: String): String = text.dropLast(1)

    /** Plus or minus a step, never below zero, shown without a trailing ".0". */
    fun step(text: String, delta: Double): String {
        val value = (text.toDoubleOrNull() ?: 0.0) + delta
        return format(if (value < 0) 0.0 else value)
    }

    fun format(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
