package uk.woozy.fit.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.woozy.fit.data.db.ReadyPlans

class ReadyPlansTest {
    @Test fun everyPlanExerciseIsInTheCatalogue() {
        val f = listOf(File("src/main/assets/exercises.tsv"), File("core/data/src/main/assets/exercises.tsv")).first { it.exists() }
        val names = f.readLines().mapNotNull { it.split('\t').getOrNull(1)?.trim()?.lowercase() }.toSet()
        val missing = ReadyPlans.all.flatMap { p -> p.days.flatMap { d -> d.items.map { it.exercise } } }.filter { it.lowercase() !in names }
        assertTrue("Missing from catalogue: $missing", missing.isEmpty())
    }
}
