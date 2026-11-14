package uk.woozy.fit.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** The same weather line as HQ's Home (Open-Meteo, Slough), cached so it shows instantly and refreshes in the background. */
object FitWeather {
    private fun prefs(c: Context) = c.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(c: Context): String? = prefs(c).getString("line", null)

    private fun label(code: Int) = when (code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Cloudy"
        45, 48 -> "Fog"; in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"
        in 80..82 -> "Showers"; in 85..86 -> "Snow showers"; in 95..99 -> "Thunder"; else -> "-"
    }

    private fun t(v: Double) = "${v.roundToInt()}°C"

    suspend fun fetch(c: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=51.51&longitude=-0.59" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=Europe%2FLondon&forecast_days=1",
            ).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val cur = o.getJSONObject("current")
            val d = o.getJSONObject("daily")
            val line = "${t(cur.getDouble("temperature_2m"))} · ${label(cur.getInt("weather_code"))} · H ${t(d.getJSONArray("temperature_2m_max").getDouble(0))}" +
                " L ${t(d.getJSONArray("temperature_2m_min").getDouble(0))} · Rain ${d.getJSONArray("precipitation_probability_max").optInt(0)}%"
            prefs(c).edit().putString("line", line).apply()
            line
        }.getOrNull()
    }
}

/** Weather line for Home: the saved one first, then a fresh one. [tick] changes on pull to refresh. */
@Composable
fun rememberWeatherLine(tick: Any? = null): String? {
    val context = LocalContext.current
    val line by produceState(FitWeather.cached(context), tick) { FitWeather.fetch(context)?.let { value = it } }
    return line
}

/** Opens Samsung Weather (or Google's), like HQ. */
fun openWeather(c: Context) {
    for (pkg in listOf("com.sec.android.daemonapp", "com.google.android.apps.weather")) {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: continue
        if (runCatching { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
    runCatching {
        c.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=weather+Slough")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
