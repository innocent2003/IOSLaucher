package com.example.oslaucher.models

import java.util.UUID

data class HomeWidgetPlacement(
    val appWidgetId: Int,
    val pageIndex: Int,
    val launcherWidgetType: LauncherWidgetType? = null,
    val positionX: Float? = null,
    val positionY: Float? = null,
    val instanceId: String = UUID.randomUUID().toString()
)

enum class LauncherWidgetType(val id: String, val labelResId: Int) {
    WEATHER("weather", com.example.oslaucher.R.string.home_widget_weather),
    CALENDAR("calendar", com.example.oslaucher.R.string.home_widget_calendar),
    CLOCK("clock", com.example.oslaucher.R.string.home_widget_clock),
    BATTERY("battery", com.example.oslaucher.R.string.home_widget_battery);

    companion object {
        fun fromId(id: String): LauncherWidgetType? =
            entries.firstOrNull { it.id == id }
    }
}
