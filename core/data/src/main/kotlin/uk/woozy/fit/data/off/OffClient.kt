package uk.woozy.fit.data.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Result of a lookup: found, not in the database, or the network failed (so nothing is known). */
sealed interface OffResult<out T> {
    data class Found<T>(val value: T) : OffResult<T>
    data object Missing : OffResult<Nothing>
    data object Offline : OffResult<Nothing>
}

/**
 * Open Food Facts over plain HTTPS. The User-Agent names the app as their API rules ask. Product reads stay under
 * 15 a minute and searches under 10 a minute. A failed call never touches stored foods.
 */
class OffClient(private val userAgent: String = "HQFit/1.0 (personal Android app; https://github.com/Woozypictures/hq-fit)") {
    private val productLimit = RateLimiter(15, 60_000)
    private val searchLimit = RateLimiter(10, 60_000)

    suspend fun product(barcode: String): OffResult<FoodDraft> {
        val code = barcode.filter { it.isDigit() }
        if (code.length < 6) return OffResult.Missing
        val url = "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=code,product_name,generic_name,brands,nutriments,serving_quantity,serving_size"
        val body = get(url, productLimit) ?: return OffResult.Offline
        return OffParser.product(body)?.let { OffResult.Found(it) } ?: OffResult.Missing
    }

    suspend fun search(text: String): OffResult<List<FoodDraft>> {
        val q = URLEncoder.encode(text.trim(), "UTF-8")
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=25" +
            "&fields=code,product_name,generic_name,brands,nutriments,serving_quantity,serving_size"
        val body = get(url, searchLimit) ?: return OffResult.Offline
        return OffResult.Found(OffParser.search(body))
    }

    private suspend fun get(url: String, limiter: RateLimiter): String? {
        val wait = limiter.reserve(System.currentTimeMillis())
        if (wait > 0) delay(wait)
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "GET"
                    c.setRequestProperty("User-Agent", userAgent)
                    c.connectTimeout = 10_000
                    c.readTimeout = 15_000
                    if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
                } finally {
                    c.disconnect()
                }
            }.getOrNull()
        }
    }
}
