package com.example.oslaucher.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R
import com.example.oslaucher.utils.WeatherWidgetPreferences

class WeatherSettingsActivity : AppCompatActivity() {
    private lateinit var options: LinearLayout
    private var showingRefreshIntervals = false
    private val pageIndex: Int
        get() = intent.getIntExtra(EXTRA_PAGE_INDEX, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_weather_settings)

        val root = findViewById<View>(R.id.weather_settings_root)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft + systemBars.left,
                baseTop + systemBars.top,
                baseRight + systemBars.right,
                baseBottom + systemBars.bottom
            )
            insets
        }

        options = findViewById(R.id.weather_settings_options)
        findViewById<View>(R.id.weather_settings_back).setOnClickListener { handleBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
        renderTemperatureSettings()
    }

    private fun handleBack() {
        if (showingRefreshIntervals) {
            showingRefreshIntervals = false
            renderTemperatureSettings()
        } else {
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_PAGE_INDEX, pageIndex)
            )
            finish()
        }
    }

    private fun renderTemperatureSettings() {
        findViewById<TextView>(R.id.weather_settings_title)
            .setText(R.string.weather_settings_title)
        options.removeAllViews()
        val selectedUnit = WeatherWidgetPreferences.temperatureUnit(this)
        addOption(
            getString(R.string.weather_unit_celsius),
            selected = selectedUnit == WeatherWidgetPreferences.CELSIUS
        ) {
            WeatherWidgetPreferences.setTemperatureUnit(this, WeatherWidgetPreferences.CELSIUS)
            renderTemperatureSettings()
        }
        addOption(
            getString(R.string.weather_unit_fahrenheit),
            selected = selectedUnit == WeatherWidgetPreferences.FAHRENHEIT
        ) {
            WeatherWidgetPreferences.setTemperatureUnit(this, WeatherWidgetPreferences.FAHRENHEIT)
            renderTemperatureSettings()
        }
        addRefreshIntervalRow()
    }

    private fun renderRefreshIntervals() {
        showingRefreshIntervals = true
        findViewById<TextView>(R.id.weather_settings_title)
            .setText(R.string.weather_auto_refresh_title)
        options.removeAllViews()
        val selectedHours = WeatherWidgetPreferences.refreshIntervalHours(this)
        WeatherWidgetPreferences.REFRESH_INTERVALS_HOURS.forEach { hours ->
            addOption(
                refreshIntervalLabel(hours),
                selected = hours == selectedHours
            ) {
                WeatherWidgetPreferences.setRefreshIntervalHours(this, hours)
                showingRefreshIntervals = false
                renderTemperatureSettings()
            }
        }
    }

    private fun addRefreshIntervalRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(14), 0)
            background = roundedBackground(color(R.color.settings_surface_secondary))
            isClickable = true
            isFocusable = true
            setOnClickListener { renderRefreshIntervals() }
        }
        val title = TextView(this).apply {
            text = getString(R.string.weather_auto_refresh)
            textSize = 14f
            setTextColor(color(R.color.settings_text_primary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val summary = TextView(this).apply {
            text = refreshIntervalLabel(WeatherWidgetPreferences.refreshIntervalHours(this@WeatherSettingsActivity))
            textSize = 13f
            setTextColor(color(R.color.settings_text_secondary))
            maxLines = 1
        }
        val chevron = TextView(this).apply {
            text = "›"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.settings_text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp(24), -2).apply {
                marginStart = dp(4)
            }
        }
        row.addView(title)
        row.addView(summary)
        row.addView(chevron)
        options.addView(
            row,
            LinearLayout.LayoutParams(-1, dp(52)).apply {
                topMargin = dp(18)
            }
        )
    }

    private fun addOption(label: String, selected: Boolean, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(16), 0)
            background = roundedBackground(color(R.color.settings_surface_secondary))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val title = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(color(R.color.settings_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        row.addView(title)
        if (selected) {
            row.addView(
                TextView(this).apply {
                    text = "✓"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(CHECK_COLOR)
                    }
                },
                LinearLayout.LayoutParams(dp(26), dp(26))
            )
        }
        options.addView(
            row,
            LinearLayout.LayoutParams(-1, dp(52)).apply {
                bottomMargin = dp(16)
            }
        )
    }

    private fun refreshIntervalLabel(hours: Int): String = when (hours) {
        1 -> getString(R.string.weather_refresh_hourly)
        3 -> getString(R.string.weather_refresh_every_three_hours)
        6 -> getString(R.string.weather_refresh_every_six_hours)
        9 -> getString(R.string.weather_refresh_every_nine_hours)
        12 -> getString(R.string.weather_refresh_every_twelve_hours)
        else -> getString(R.string.weather_refresh_daily)
    }

    private fun roundedBackground(fillColor: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(fillColor)
    }

    private fun color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAGE_INDEX = "weather_settings_page_index"
        private const val CHECK_COLOR = 0xFF2196F3.toInt()
    }
}
