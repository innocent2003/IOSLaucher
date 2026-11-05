package com.example.oslaucher.ui.widgets

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.oslaucher.R
import com.example.oslaucher.models.LauncherWidgetType
import com.example.oslaucher.repositories.WeatherForecastRepository
import com.example.oslaucher.utils.WeatherWidgetPreferences
import org.json.JSONException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal object LauncherWidgetFactory {
    fun create(
        context: Context,
        type: LauncherWidgetType,
        onWeatherSettingsClick: () -> Unit = {}
    ): View = when (type) {
        LauncherWidgetType.WEATHER -> createWeather(context, onWeatherSettingsClick)
        LauncherWidgetType.CALENDAR -> createCalendar(context)
        LauncherWidgetType.CLOCK -> AnalogClockView(context).apply {
            background = context.getDrawable(R.drawable.bg_widget)
            contentDescription = context.getString(R.string.home_widget_clock)
        }
        LauncherWidgetType.BATTERY -> createBattery(context)
    }

    private fun createWeather(context: Context, onSettingsClick: () -> Unit): View {
        val repository = WeatherForecastRepository(context)
        val temperatureUnit = WeatherWidgetPreferences.temperatureUnit(context)
        val temperatureSymbol = if (temperatureUnit == WeatherWidgetPreferences.CELSIUS) {
            "°C"
        } else {
            "°F"
        }
        val mainTemperature = weatherText(context, 36f).apply {
            text = "--$temperatureSymbol"
        }
        val condition = weatherText(context, 11f, secondary = true).apply {
            text = context.getString(R.string.weather_updating)
        }
        val currentIcon = weatherText(context, 20f).apply {
            text = "…"
        }
        val highLow = weatherText(context, 10f, secondary = true).apply {
            text = "…"
        }
        val hourlyForecast = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        val root = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(context).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            color(context, R.color.weather_widget_gradient_start),
                            color(context, R.color.weather_widget_gradient_end)
                        )
                    ).apply {
                        cornerRadius = dp(context, 24).toFloat()
                        setStroke(dp(context, 1), color(context, R.color.weather_widget_border))
                    }
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 6))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )

                    addView(
                        LinearLayout(context).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            orientation = LinearLayout.HORIZONTAL
                            addView(weatherText(context, 13f, bold = true).apply {
                                text = "Thanh Xuân"
                                layoutParams = LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    1f
                                )
                            })
                            addView(currentIcon)
                            addView(condition)
                        }
                    )
                    addView(
                        LinearLayout(context).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            orientation = LinearLayout.HORIZONTAL
                            addView(mainTemperature.apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            })
                            addView(
                                View(context).apply {
                                    setBackgroundColor(color(context, R.color.weather_widget_divider))
                                },
                                LinearLayout.LayoutParams(0, 1, 1f)
                            )
                            addView(highLow)
                        }
                    )
                    addView(
                        View(context),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(context, 1)
                        ).apply {
                            topMargin = dp(context, 1)
                            bottomMargin = dp(context, 3)
                        }.also { it.height = dp(context, 1) }
                    )
                    addView(
                        hourlyForecast,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            0,
                            1f
                        )
                    )
                }
            )
            addView(weatherText(context, 9f, secondary = true).apply {
                text = context.getString(R.string.weather_widget_caption)
                setPadding(0, dp(context, 2), 0, 0)
            })
        }

        fun refresh() {
            Thread {
                val forecast = try {
                    Result.success(repository.load(temperatureUnit))
                } catch (exception: IOException) {
                    Result.failure(exception)
                } catch (exception: JSONException) {
                    Result.failure(exception)
                }
                Handler(Looper.getMainLooper()).post {
                    forecast.onSuccess { data ->
                        mainTemperature.text = "${data.temperature}$temperatureSymbol"
                        currentIcon.text = data.icon
                        condition.text = data.description
                        highLow.text = context.getString(
                            R.string.weather_widget_high_low,
                            data.high,
                            data.low,
                            temperatureSymbol
                        )
                        hourlyForecast.removeAllViews()
                        data.hours.forEach { hour ->
                            hourlyForecast.addView(
                                LinearLayout(context).apply {
                                    gravity = Gravity.CENTER
                                    orientation = LinearLayout.VERTICAL
                                    addView(weatherText(context, 9f, secondary = true).apply {
                                        text = hour.time
                                    })
                                    addView(weatherText(context, 17f).apply {
                                        text = hour.icon
                                        contentDescription =
                                            context.getString(R.string.weather_accessibility)
                                    })
                                    addView(weatherText(context, 10f, bold = true).apply {
                                        text = "${hour.temperature}$temperatureSymbol"
                                    })
                                },
                                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                            )
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Unable to load launcher weather widget", error)
                        mainTemperature.text = "--$temperatureSymbol"
                        currentIcon.text = "☁"
                        condition.text = context.getString(R.string.weather_unavailable)
                        highLow.text = context.getString(R.string.weather_widget_retry)
                        hourlyForecast.removeAllViews()
                    }
                }
            }.apply {
                name = "launcher-weather-loader"
                start()
            }
        }

        root.setOnClickListener { onSettingsClick() }
        refresh()
        return root
    }

    private fun weatherText(
        context: Context,
        size: Float,
        bold: Boolean = false,
        secondary: Boolean = false
    ) = TextView(context).apply {
        setTextColor(
            color(
                context,
                if (secondary) R.color.weather_widget_text_secondary
                else R.color.weather_widget_text_primary
            )
        )
        textSize = size
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, Typeface.BOLD)
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
                layoutParams = LinearLayout.LayoutParams(dp(context, 58), dp(context, 58))
                contentDescription = context.getString(R.string.battery_accessibility)
            })
            addView(TextView(context).apply {
                text = level?.let { "$it%" } ?: "--%"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 25f
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

    private fun color(context: Context, resourceId: Int): Int =
        ContextCompat.getColor(context, resourceId)

    private const val TAG = "LauncherWidgetFactory"
}
