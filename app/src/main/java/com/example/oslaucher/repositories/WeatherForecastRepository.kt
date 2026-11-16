package com.example.oslaucher.repositories

import android.content.Context
import com.example.oslaucher.R
import com.example.oslaucher.utils.WeatherWidgetPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class WeatherHour(
    val time: String,
    val temperature: Int,
    val icon: String
)

internal data class WeatherForecast(
    val temperature: Int,
    val high: Int,
    val low: Int,
    val description: String,
    val icon: String,
    val hours: List<WeatherHour>
)

internal class WeatherForecastRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)

    @Throws(IOException::class, JSONException::class)
    fun load(temperatureUnit: String): WeatherForecast {
        require(
            temperatureUnit == WeatherWidgetPreferences.CELSIUS ||
                temperatureUnit == WeatherWidgetPreferences.FAHRENHEIT
        )
        val apiTemperatureUnit = if (temperatureUnit == WeatherWidgetPreferences.CELSIUS) {
            "celsius"
        } else {
            "fahrenheit"
        }
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=20.9947&longitude=105.8066" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=$apiTemperatureUnit" +
                "&timezone=Asia%2FBangkok&forecast_days=2"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Weather service returned HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            val hourly = json.getJSONObject("hourly")
            val daily = json.getJSONObject("daily")
            val times = hourly.getJSONArray("time")
            val temperatures = hourly.getJSONArray("temperature_2m")
            val weatherCodes = hourly.getJSONArray("weather_code")
            val currentTime = current.getString("time")
            val firstHour = (0 until times.length()).firstOrNull {
                times.getString(it) >= currentTime
            } ?: (times.length() - HOURS_TO_SHOW).coerceAtLeast(0)
            val hours = buildList {
                for (index in firstHour until minOf(firstHour + HOURS_TO_SHOW, times.length())) {
                    val code = weatherCodes.getInt(index)
                    add(
                        WeatherHour(
                            time = times.getString(index).substringAfter('T').take(5),
                            temperature = temperatures.getDouble(index).toInt(),
                            icon = weatherIcon(code)
                        )
                    )
                }
            }
            val locale = appContext.resources.configuration.locales[0]
            val description = appContext.getString(weatherDescription(current.getInt("weather_code")))
            return WeatherForecast(
                temperature = current.getDouble("temperature_2m").toInt(),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt(),
                description = description.replaceFirstChar { it.uppercase(locale) },
                icon = weatherIcon(current.getInt("weather_code")),
                hours = hours
            ).also { saveCached(temperatureUnit, it) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Last forecast saved in SharedPreferences, so a newly created weather widget can show
     * real data instantly instead of "--" while the network request is running.
     */
    fun cached(temperatureUnit: String): WeatherForecast? {
        val stored = preferences.getString(cacheKey(temperatureUnit), null) ?: return null
        return try {
            val json = JSONObject(stored)
            val jsonHours = json.optJSONArray(KEY_HOURS)
            WeatherForecast(
                temperature = json.getInt(KEY_TEMPERATURE),
                high = json.getInt(KEY_HIGH),
                low = json.getInt(KEY_LOW),
                description = json.getString(KEY_DESCRIPTION),
                icon = json.getString(KEY_ICON),
                hours = buildList {
                    for (index in 0 until (jsonHours?.length() ?: 0)) {
                        val hour = jsonHours!!.getJSONObject(index)
                        add(
                            WeatherHour(
                                hour.getString(KEY_TIME),
                                hour.getInt(KEY_TEMPERATURE),
                                hour.getString(KEY_ICON)
                            )
                        )
                    }
                }
            )
        } catch (_: JSONException) {
            null
        }
    }

    private fun saveCached(temperatureUnit: String, forecast: WeatherForecast) {
        val hours = JSONArray()
        forecast.hours.forEach { hour ->
            hours.put(
                JSONObject()
                    .put(KEY_TIME, hour.time)
                    .put(KEY_TEMPERATURE, hour.temperature)
                    .put(KEY_ICON, hour.icon)
            )
        }
        val json = JSONObject()
            .put(KEY_TEMPERATURE, forecast.temperature)
            .put(KEY_HIGH, forecast.high)
            .put(KEY_LOW, forecast.low)
            .put(KEY_DESCRIPTION, forecast.description)
            .put(KEY_ICON, forecast.icon)
            .put(KEY_HOURS, hours)
        preferences.edit().putString(cacheKey(temperatureUnit), json.toString()).apply()
    }

    private fun cacheKey(temperatureUnit: String) = "forecast_$temperatureUnit"

    private fun weatherDescription(code: Int): Int = when (code) {
        0 -> R.string.weather_clear
        1, 2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        in 51..57 -> R.string.weather_drizzle
        in 61..67, in 80..82 -> R.string.weather_rain
        in 71..77, 85, 86 -> R.string.weather_snow
        in 95..99 -> R.string.weather_thunderstorm
        else -> R.string.weather_current
    }

    private fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "🌤"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⛈"
        else -> "☁"
    }

    private companion object {
        const val HOURS_TO_SHOW = 6
        const val CACHE_PREFERENCES = "weather_forecast_cache"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_HIGH = "high"
        const val KEY_LOW = "low"
        const val KEY_DESCRIPTION = "description"
        const val KEY_ICON = "icon"
        const val KEY_HOURS = "hours"
        const val KEY_TIME = "time"
    }
}
