package uk.woozy.fit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.woozy.fit.data.off.OffParser
import uk.woozy.fit.data.off.RateLimiter

/**
 * Fixtures below are written by hand to Open Food Facts' documented response shape, not saved from the live API.
 * Replace them with real saved responses when you have some from the phone (see HANDOVER.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OffTest {
    private val full = """
        {"code":"5000169003651","status":1,"product":{"code":"5000169003651","product_name":"Greek Style Yogurt","brands":"Tesco, Other",
        "serving_quantity":"150","serving_size":"150 g",
        "nutriments":{"energy-kcal_100g":97,"proteins_100g":5.1,"carbohydrates_100g":4.2,"sugars_100g":4.0,"fat_100g":6.9,
        "saturated-fat_100g":4.6,"fiber_100g":0,"sodium_100g":0.05}}}
    """.trimIndent()

    @Test
    fun parsesPer100gAndConvertsSodiumToMilligrams() {
        val d = OffParser.product(full)!!
        assertEquals("Greek Style Yogurt", d.name)
        assertEquals("Tesco", d.brand)
        assertEquals("5000169003651", d.barcode)
        assertEquals(150.0, d.servingG!!, 0.0)
        assertEquals(97.0, d.per100.getValue("energy"), 0.0)
        assertEquals(5.1, d.per100.getValue("protein"), 0.0)
        assertEquals(50.0, d.per100.getValue("sodium"), 1e-9)
        assertFalse(d.incomplete)
    }

    @Test
    fun missingEnergyMarksTheFoodIncomplete() {
        val json = """{"status":1,"product":{"code":"123456789","product_name":"Mystery","nutriments":{"proteins_100g":3}}}"""
        val d = OffParser.product(json)!!
        assertTrue(d.incomplete)
        assertEquals(3.0, d.per100.getValue("protein"), 0.0)
    }

    @Test
    fun kilojouleOnlyIsConverted() {
        val json = """{"status":1,"product":{"code":"1234567","product_name":"X","nutriments":{"energy_100g":418.4}}}"""
        assertEquals(100.0, OffParser.product(json)!!.per100.getValue("energy"), 1e-9)
    }

    @Test
    fun notFoundAndGarbageGiveNull() {
        assertNull(OffParser.product("""{"status":0,"status_verbose":"product not found","code":"000"}"""))
        assertNull(OffParser.product("not json"))
        assertNull(OffParser.product("""{"status":1,"product":{"nutriments":{}}}""")) // no name
    }

    @Test
    fun searchSkipsNamelessProducts() {
        val json = """{"products":[{"code":"1111111","product_name":"A","nutriments":{"energy-kcal_100g":10}},{"code":"2222222"}]}"""
        val list = OffParser.search(json)
        assertEquals(listOf("A"), list.map { it.name })
        assertNotNull(list.first().barcode)
    }

    @Test
    fun saltIsUsedForSodiumWhenSodiumIsMissing() {
        val json = """{"status":1,"product":{"code":"1234567","product_name":"X","nutriments":{"energy-kcal_100g":1,"salt_100g":1.25}}}"""
        assertEquals(500.0, OffParser.product(json)!!.per100.getValue("sodium"), 1e-9)
    }

    @Test
    fun rateLimiterAllowsTheLimitThenMakesYouWait() {
        val r = RateLimiter(limit = 3, windowMs = 60_000)
        assertEquals(0L, r.reserve(0))
        assertEquals(0L, r.reserve(1_000))
        assertEquals(0L, r.reserve(2_000))
        assertEquals(58_000L, r.reserve(2_000)) // wait until the first call is a minute old
        assertEquals(0L, r.reserve(60_000)) // the first call has aged out
    }
}
