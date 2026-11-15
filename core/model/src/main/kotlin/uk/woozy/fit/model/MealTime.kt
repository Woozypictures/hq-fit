package uk.woozy.fit.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a diary entry happened, for Health Connect. Normally the moment it was logged; but food added for another
 * day (yesterday's dinner, typed in this morning) is placed on that day, at a usual time for the meal, so it
 * counts towards the right day in Samsung Health and HQ.
 */
object MealTime {
    fun at(date: String, meal: String, createdMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return createdMs
        if (Instant.ofEpochMilli(createdMs).atZone(zone).toLocalDate() == day) return createdMs
        val hour = when (meal) { "breakfast" -> 8; "lunch" -> 13; "dinner" -> 19; else -> 16 }
        return day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    }
}
