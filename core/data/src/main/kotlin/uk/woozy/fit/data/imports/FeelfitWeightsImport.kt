package uk.woozy.fit.data.imports

import java.time.LocalDate
import java.time.ZoneId
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.WeightEntryEntity

/**
 * Adds weigh-ins copied from the Feelfit app (date and kg per line, tab separated). Feelfit cannot export its data and
 * does not share with Health Connect, so these were read off its All Data list: a sample of days, not every weigh-in.
 * A day that already has a weight is left alone, so this is safe to run twice.
 */
object FeelfitWeightsImport {
    suspend fun import(db: HqFitDatabase, text: String): Int {
        val dao = db.weights()
        val zone = ZoneId.systemDefault()
        var added = 0
        for (line in text.lines()) {
            val f = line.trim().split('\t')
            if (f.size < 2) continue
            val date = runCatching { LocalDate.parse(f[0]) }.getOrNull() ?: continue
            val kg = f[1].toDoubleOrNull() ?: continue
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            if (dao.countBetween(dayStart, dayStart + 86_400_000L - 1) > 0) continue
            dao.insert(WeightEntryEntity(epochMs = dayStart + 8 * 3_600_000L, kg = kg, source = "import"))
            added++
        }
        return added
    }
}
