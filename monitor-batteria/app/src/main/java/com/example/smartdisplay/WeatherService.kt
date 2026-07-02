package com.example.smartdisplay

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WeatherData(
    val temperature: String = "--",
    val condition: String = "N/D",
    val icon: String = "unknown",
    val humidity: String = "--",
    val windSpeed: String = "--",
    val feelsLike: String = "--",
    val forecast: List<ForecastDay> = emptyList()
)

data class ForecastDay(
    val day: String,
    val tempHigh: String,
    val tempLow: String,
    val condition: String,
    val icon: String
)

object WeatherService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedWeather: WeatherData? = null
    private var lastFetch: Long = 0
    private const val CACHE_DURATION = 15 * 60 * 1000L

    fun getCached(): WeatherData? {
        if (System.currentTimeMillis() - lastFetch < CACHE_DURATION) {
            return cachedWeather
        }
        return null
    }

    fun fetchWeather(lat: String, lon: String): WeatherData? {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val current = json.optJSONObject("current") ?: return null
            val temp = current.optDouble("temperature_2m", 0.0)
            val hum = current.optDouble("relative_humidity_2m", 0.0)
            val feels = current.optDouble("apparent_temperature", 0.0)
            val wind = current.optDouble("wind_speed_10m", 0.0)
            val wmoCode = current.optInt("weather_code", 0)

            val daily = json.optJSONObject("daily")
            val forecast = mutableListOf<ForecastDay>()
            if (daily != null) {
                val times = daily.optJSONArray("time")
                val codes = daily.optJSONArray("weather_code")
                val highs = daily.optJSONArray("temperature_2m_max")
                val lows = daily.optJSONArray("temperature_2m_min")
                if (times != null) {
                    for (i in 0 until minOf(times.length(), 4)) {
                        val dateStr = times.optString(i, "")
                        val dayName = try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val d = sdf.parse(dateStr)
                            if (d != null) {
                                if (i == 0) "Oggi"
                                else SimpleDateFormat("EEE", Locale.getDefault()).format(d)
                            } else dateStr
                        } catch (_: Exception) { dateStr }
                        val wc = codes?.optInt(i, 0) ?: 0
                        forecast.add(ForecastDay(
                            day = dayName,
                            tempHigh = String.format("%.0f", highs?.optDouble(i, 0.0) ?: 0.0),
                            tempLow = String.format("%.0f", lows?.optDouble(i, 0.0) ?: 0.0),
                            condition = wmoDescription(wc),
                            icon = wc.toString()
                        ))
                    }
                }
            }

            val data = WeatherData(
                temperature = String.format("%.0f", temp),
                condition = wmoDescription(wmoCode),
                icon = wmoCode.toString(),
                humidity = String.format("%.0f", hum),
                windSpeed = String.format("%.0f", wind),
                feelsLike = String.format("%.0f", feels),
                forecast = forecast
            )
            cachedWeather = data
            lastFetch = System.currentTimeMillis()
            return data
        } catch (_: Exception) {
            return cachedWeather
        }
    }

    fun weatherEmoji(code: String): String {
        val wmo = code.toIntOrNull() ?: return "\uD83C\uDF24\uFE0F"
        return when (wmo) {
            0 -> "\u2600\uFE0F"
            1 -> "\uD83C\uDF24\uFE0F"
            2 -> "\u26C5"
            3 -> "\u2601\uFE0F"
            45, 48 -> "\uD83C\uDF2B\uFE0F"
            51, 53, 55 -> "\uD83C\uDF26\uFE0F"
            56, 57 -> "\uD83C\uDF27\uFE0F"
            61, 63, 65 -> "\uD83C\uDF27\uFE0F"
            66, 67 -> "\u2744\uFE0F"
            71, 73, 75 -> "\u2744\uFE0F"
            77 -> "\uD83C\uDF28\uFE0F"
            80, 81, 82 -> "\uD83C\uDF27\uFE0F"
            85, 86 -> "\u2744\uFE0F"
            95 -> "\u26C8\uFE0F"
            96, 99 -> "\u26C8\uFE0F"
            else -> "\uD83C\uDF24\uFE0F"
        }
    }

    private fun wmoDescription(code: Int): String = when (code) {
        0 -> "Sereno"
        1 -> "Preval. sereno"
        2 -> "Parz. nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51 -> "Piovigg. legg."
        53 -> "Piovigg. mod."
        55 -> "Piovigg. densa"
        56, 57 -> "Piovigg. gel."
        61 -> "Pioggia legg."
        63 -> "Pioggia mod."
        65 -> "Pioggia forte"
        66, 67 -> "Pioggia gel."
        71 -> "Neve legg."
        73 -> "Neve mod."
        75 -> "Neve forte"
        77 -> "Granelli neve"
        80 -> "Rovesci legg."
        81 -> "Rovesci mod."
        82 -> "Rovesci forti"
        85 -> "Neve rovesci"
        86 -> "Neve rovesci f."
        95 -> "Temporale"
        96, 99 -> "Temporale grand."
        else -> "N/D"
    }
}
