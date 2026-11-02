package com.example.oslaucher.ui.widgets

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.oslaucher.R
import com.example.oslaucher.models.LauncherWidgetType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal object LauncherWidgetFactory {
    fun create(context: Context, type: LauncherWidgetType): View = when (type) {
        LauncherWidgetType.WEATHER -> createWeather(context)
        LauncherWidgetType.CALENDAR -> createCalendar(context)
        LauncherWidgetType.CLOCK -> AnalogClockView(context).apply {
            background = context.getDrawable(R.drawable.bg_widget)
            contentDescription = context.getString(R.string.home_widget_clock)
        }
        LauncherWidgetType.BATTERY -> createBattery(context)
    }

    private fun createWeather(context: Context): View =
        LinearLayout(context).apply {
            background = context.getDrawable(R.drawable.bg_widget)
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            addView(TextView(context).apply {
                text = "Thanh Xuân"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(context).apply {
                    text = "--°C"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 31f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(context).apply {
                    text = "🌤"
                    contentDescription = context.getString(R.string.weather_accessibility)
                    textSize = 25f
                })
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.weather_loading)
                setTextColor(0xFFF0F2F5.toInt())
                textSize = 12f
            })
        }

    private fun createCalendar(context: Context): View {
        val now = Calendar.getInstance()
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val container = LinearLayout(context).apply {
            background = context.getDrawable(R.drawable.bg_widget)
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 10), dp(context, 12), dp(context, 10), dp(context, 8))
            addView(TextView(context).apply {
                text = SimpleDateFormat("MMMM yyyy", locale).format(now.time)
                    .uppercase(locale)
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        val grid = GridLayout(context).apply {
            columnCount = 7
            rowCount = 7
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(context, 6)
            }
        }
        val weekday = Calendar.getInstance().apply {
            set(1970, Calendar.JANUARY, 5)
        }
        val weekdayFormatter = SimpleDateFormat("EE", locale)
        repeat(7) { column ->
            addCalendarCell(
                context,
                grid,
                weekdayFormatter.format(weekday.time).uppercase(locale),
                0,
                column,
                false
            )
            weekday.add(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDay = now.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (index in 0 until 42) {
            val day = index - offset + 1
            val isValidDay = day in 1..daysInMonth
            addCalendarCell(
                context,
                grid,
                if (isValidDay) day.toString() else "",
                index / 7 + 1,
                index % 7,
                isValidDay && day == now.get(Calendar.DAY_OF_MONTH)
            )
        }
        container.addView(grid)
        container.addView(TextView(context).apply {
            text = context.getString(R.string.category_calendar)
            gravity = Gravity.CENTER
            setTextColor(0xFFCFD5DC.toInt())
            textSize = 10f
        })
        return container
    }

    private fun addCalendarCell(
        context: Context,
        grid: GridLayout,
        text: String,
        row: Int,
        column: Int,
        isToday: Boolean
    ) {
        val cell = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (row == 0) 9f else 10f
            setTextColor(if (row == 0) 0xFFBFC8D2.toInt() else 0xFFFFFFFF.toInt())
            if (isToday) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFE74C5B.toInt())
                }
            }
        }
        grid.addView(
            cell,
            GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = 0
            }
        )
    }

    private fun createBattery(context: Context): View {
        val level = context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        return LinearLayout(context).apply {
            background = context.getDrawable(R.drawable.bg_widget)
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            addView(BatteryRingView(context).apply {
                batteryLevel = level
                layoutParams = LinearLayout.LayoutParams(dp(context, 68), dp(context, 68))
                contentDescription = context.getString(R.string.battery_accessibility)
            })
            addView(TextView(context).apply {
                text = level?.let { "$it%" } ?: "--%"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 29f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 7)
                }
            })
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
