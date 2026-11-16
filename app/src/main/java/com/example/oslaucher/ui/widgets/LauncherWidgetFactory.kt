package com.example.oslaucher.ui.widgets

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.oslaucher.R
import com.example.oslaucher.models.LauncherWidgetType
import com.example.oslaucher.repositories.WeatherForecast
import com.example.oslaucher.repositories.WeatherForecastRepository
import com.example.oslaucher.utils.IconMetrics
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
        // Compact 2x2 tile like the reference: dark glass card with city, big temperature,
        // a small condition icon, the condition text and "H:.. L:..". Caption under the card.
        mainTemperature.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
        }
        val root = LinearLayout(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(context).apply {
                    background = widgetCardBackground(context)
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 16), dp(context, 12), dp(context, 14), dp(context, 12))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                    addView(weatherText(context, 15f, bold = true).apply {
                        text = "Thanh Xuân"
                        singleLine()
                    })
                    addView(mainTemperature)
                    addView(
                        View(context),
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    )
                    addView(currentIcon.apply { setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f) })
                    addView(condition.apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
                        setTextColor(color(context, R.color.weather_widget_text_primary))
                        singleLine()
                    })
                    addView(highLow.apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
                        setTextColor(color(context, R.color.weather_widget_text_primary))
                        singleLine()
                    })
                }
            )
            addView(widgetCaption(context, context.getString(R.string.weather_widget_caption)))
        }

        var hasForecast = false
        fun show(data: WeatherForecast) {
            hasForecast = true
            mainTemperature.text = "${data.temperature}$temperatureSymbol"
            currentIcon.text = data.icon
            condition.text = data.description
            highLow.text = "H:${data.high}$temperatureSymbol L:${data.low}$temperatureSymbol"
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
                    forecast.onSuccess(::show).onFailure { error ->
                        Log.w(TAG, "Unable to load launcher weather widget", error)
                        // Keep showing the last saved forecast when offline.
                        if (hasForecast) return@onFailure
                        mainTemperature.text = "--$temperatureSymbol"
                        currentIcon.text = "☁"
                        condition.text = context.getString(R.string.weather_unavailable)
                        highLow.text = context.getString(R.string.weather_widget_retry)
                    }
                }
            }.apply {
                name = "launcher-weather-loader"
                start()
            }
        }

        // Show the forecast saved last time right away, then refresh it in the background.
        repository.cached(temperatureUnit)?.let(::show)
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
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun createCalendar(context: Context): View {
        val now = Calendar.getInstance()
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val card = LinearLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(context, WIDGET_CORNER_DP).toFloat()
                setColor(0xF01C1C1E.toInt())
                setStroke(dp(context, 1), 0x2EFFFFFF)
            }
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 12), dp(context, 8))
            addView(TextView(context).apply {
                text = SimpleDateFormat("LLLL", locale).format(now.time).uppercase(locale)
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                setTypeface(typeface, Typeface.BOLD)
                singleLine()
            })
        }
        val firstDay = now.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weekRows = (offset + daysInMonth + 6) / 7
        val grid = GridLayout(context).apply {
            columnCount = 7
            rowCount = weekRows + 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(context, 4)
            }
        }
        // Monday-first single-letter weekday header, as in the reference widget.
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { column, label ->
            addCalendarCell(context, grid, label, 0, column, false)
        }
        for (index in 0 until weekRows * 7) {
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
        card.addView(grid)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.VERTICAL
            addView(
                card,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(widgetCaption(context, context.getString(R.string.category_calendar)))
        }
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
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f)
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, Typeface.BOLD)
            if (isToday) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFC0392B.toInt())
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

    private fun widgetCardBackground(context: Context) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            color(context, R.color.weather_widget_gradient_start),
            color(context, R.color.weather_widget_gradient_end)
        )
    ).apply {
        cornerRadius = dp(context, WIDGET_CORNER_DP).toFloat()
        setStroke(dp(context, 1), color(context, R.color.weather_widget_border))
    }

    /** Small white label under a widget card ("Thời tiết", "Lịch"), like app icon labels. */
    private fun widgetCaption(context: Context, label: String) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        // Same size as app icon labels so the caption reads like one.
        setTextSize(TypedValue.COMPLEX_UNIT_PX, IconMetrics.labelTextSizePx(context))
        includeFontPadding = false
        setShadowLayer(3f, 0f, 1f, 0x66000000)
        setPadding(0, dp(context, 5), 0, 0)
        singleLine()
    }

    private fun TextView.singleLine() {
        maxLines = 1
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
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
    private const val WIDGET_CORNER_DP = 22
}
