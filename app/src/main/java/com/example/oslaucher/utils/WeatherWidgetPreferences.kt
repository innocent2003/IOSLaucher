package com.example.oslaucher.utils

import android.content.Context
import android.content.SharedPreferences

internal object WeatherWidgetPreferences {
    const val CELSIUS = "celsius"
    const val FAHRENHEIT = "fahrenheit"
    const val DEFAULT_REFRESH_HOURS = 24
    val REFRESH_INTERVALS_HOURS = listOf(1, 3, 6, 9, 12, 24)

    private const val PREFERENCES_NAME = "weather_widget_settings"
    private const val TEMPERATURE_UNIT_KEY = "temperature_unit"
    private const val REFRESH_INTERVAL_KEY = "refresh_interval_hours"
    private const val LAST_REFRESH_AT_KEY = "last_refresh_at"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun temperatureUnit(context: Context): String =
        preferences(context).getString(TEMPERATURE_UNIT_KEY, CELSIUS) ?: CELSIUS

    fun refreshIntervalHours(context: Context): Int =
        preferences(context).getInt(REFRESH_INTERVAL_KEY, DEFAULT_REFRESH_HOURS)
            .takeIf { it in REFRESH_INTERVALS_HOURS }
            ?: DEFAULT_REFRESH_HOURS

    fun lastRefreshTime(context: Context): Long =
        preferences(context).getLong(LAST_REFRESH_AT_KEY, 0L)

    fun isRefreshDue(context: Context): Boolean {
        val intervalMillis = refreshIntervalHours(context) * 60L * 60L * 1000L
        return System.currentTimeMillis() - lastRefreshTime(context) >= intervalMillis
    }

    fun recordRefresh(context: Context) {
        preferences(context).edit()
            .putLong(LAST_REFRESH_AT_KEY, System.currentTimeMillis())
            .apply()
    }

    fun setTemperatureUnit(context: Context, unit: String) {
        require(unit == CELSIUS || unit == FAHRENHEIT)
        preferences(context).edit().putString(TEMPERATURE_UNIT_KEY, unit).apply()
    }

    fun setRefreshIntervalHours(context: Context, hours: Int) {
        require(hours in REFRESH_INTERVALS_HOURS)
        preferences(context).edit().putInt(REFRESH_INTERVAL_KEY, hours).apply()
    }
}
