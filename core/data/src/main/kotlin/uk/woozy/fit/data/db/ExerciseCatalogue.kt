package uk.woozy.fit.data.db

import java.io.BufferedReader

/** Reads the bundled exercise list (text from the openGym catalogue, see NOTICE): id, name, body part, equipment, muscle, secondary, category, description. */
object ExerciseCatalogue {
    fun parse(reader: BufferedReader): List<ExerciseEntity> {
        val out = ArrayList<ExerciseEntity>(6000)
        reader.useLines { lines ->
            lines.forEach { line ->
                val f = line.split('\t')
                if (f.size >= 5 && f[1].isNotBlank()) {
                    out += ExerciseEntity(
                        name = titleCase(f[1]),
                        muscle = f[4],
                        equipment = f[3],
                        instructions = f.getOrElse(7) { "" },
                    )
                }
            }
        }
        return out
    }

    /** "decline chest press" becomes "Decline Chest Press"; brackets and hyphens are kept. */
    fun titleCase(raw: String): String =
        raw.trim().split(' ').joinToString(" ") { w ->
            w.split('-').joinToString("-") { p -> p.replaceFirstChar { it.uppercase() } }
        }
}
