package com.example.oslaucher.ui.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.oslaucher.R
import com.example.oslaucher.ui.widgets.BatteryRingView
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

abstract class HomeScreenFragment : Fragment() {
    protected abstract val layoutResId: Int

    private var receiverRegistered = false
    private var weatherRequestInProgress = false
    private val updatesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            view?.let(::updateWidgets)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(layoutResId, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.main)) { root, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        view.findViewById<View>(R.id.home_settings).setOnClickListener {
            (activity as? HomeScreenActions)?.openSettings()
        }
        view.findViewById<View?>(R.id.home_weather_widget)?.setOnClickListener {
            refreshWeather()
        }
        view.findViewById<View?>(R.id.home_calendar_widget)?.setOnClickListener {
            openCalendar()
        }
        updateWidgets(view)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }
        ContextCompat.registerReceiver(
            requireContext(),
            updatesReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        view?.let(::updateWidgets)
        if (view?.findViewById<View?>(R.id.home_weather_widget) != null) {
            refreshWeather()
        }
    }

    override fun onStop() {
        if (receiverRegistered) {
            requireContext().unregisterReceiver(updatesReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun updateWidgets(root: View) {
        val now = Date()
        val locale = Locale.forLanguageTag("vi")
        val localizedDate = SimpleDateFormat("EEEE, d 'THÁNG' M", locale)
            .format(now)
            .uppercase(locale)
        val batteryPercent = requireContext()
            .getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        val batteryLevel = batteryPercent
            ?.let { "$it%" }
            ?: "--%"

        root.findViewById<TextView>(R.id.home_clock)?.text =
            DateFormat.getTimeFormat(requireContext()).format(now)
        root.findViewById<TextView>(R.id.home_date)?.text = localizedDate
        root.findViewById<TextView>(R.id.home_battery)?.text = batteryLevel
        root.findViewById<TextView>(R.id.home_battery_widget)?.text = batteryLevel
        root.findViewById<BatteryRingView>(R.id.home_battery_ring)?.batteryLevel = batteryPercent
        root.findViewById<View>(R.id.home_analog_clock)?.invalidate()

        updateMiniCalendar(root, now, locale)
    }

    private fun updateMiniCalendar(root: View, now: Date, locale: Locale) {
        val monthView = root.findViewById<TextView?>(R.id.home_calendar_month) ?: return
        val grid = root.findViewById<GridLayout?>(R.id.home_calendar_widget_grid) ?: return
        val calendar = Calendar.getInstance().apply { time = now }
        monthView.text = SimpleDateFormat("'THÁNG' M yyyy", locale).format(now)
            .uppercase(locale)

        grid.removeAllViews()
        val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        labels.forEachIndexed { column, label ->
            addCalendarCell(grid, label, 0, column, false)
        }

        val firstDay = calendar.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (index in 0 until 42) {
            val day = index - offset + 1
            val validDay = day in 1..daysInMonth
            val isToday = validDay && day == calendar.get(Calendar.DAY_OF_MONTH)
            addCalendarCell(
                grid,
                if (validDay) day.toString() else "",
                index / 7 + 1,
                index % 7,
                isToday
            )
        }
    }

    private fun addCalendarCell(
        grid: GridLayout,
        text: String,
        row: Int,
        column: Int,
        isToday: Boolean
    ) {
        val cell = TextView(requireContext()).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (row == 0) 9f else 10f
            setTextColor(if (row == 0) 0xFFBFC8D2.toInt() else Color.WHITE)
            if (isToday) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFE74C5B.toInt())
                }
            }
        }
        val params = GridLayout.LayoutParams(
            GridLayout.spec(row),
            GridLayout.spec(column, 1f)
        ).apply {
            width = 0
            height = dp(15)
        }
        grid.addView(cell, params)
    }

    private fun refreshWeather() {
        if (weatherRequestInProgress) return
        val root = view ?: return
        val condition = root.findViewById<TextView?>(R.id.home_weather_condition) ?: return
        weatherRequestInProgress = true
        condition.text = "Đang cập nhật..."

        Thread {
            val result = try {
                Result.success(loadWeather())
            } catch (error: IOException) {
                Result.failure(error)
            } catch (error: JSONException) {
                Result.failure(error)
            }
            activity?.runOnUiThread {
                weatherRequestInProgress = false
                val currentView = view ?: return@runOnUiThread
                val temperature = currentView.findViewById<TextView?>(R.id.home_weather_temperature)
                val icon = currentView.findViewById<TextView?>(R.id.home_weather_icon)
                val currentCondition = currentView.findViewById<TextView?>(R.id.home_weather_condition)
                val range = currentView.findViewById<TextView?>(R.id.home_weather_range)
                result.onSuccess { weather ->
                    temperature?.text = "${weather.temperature.toInt()}°C"
                    icon?.text = weather.icon
                    currentCondition?.text = weather.description
                    range?.text = "Cao: ${weather.high.toInt()}°  Thấp: ${weather.low.toInt()}°"
                }.onFailure { error ->
                    Log.w(TAG, "Unable to load weather", error)
                    currentCondition?.text = "Không tải được thời tiết"
                    range?.text = "Nhấn để thử lại"
                }
            }
        }.apply {
            name = "weather-widget-loader"
            start()
        }
    }

    private fun loadWeather(): Weather {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=20.9947&longitude=105.8066" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=Asia%2FBangkok&forecast_days=1"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Weather service returned HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val weatherCode = current.getInt("weather_code")
            return Weather(
                temperature = current.getDouble("temperature_2m"),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0),
                description = weatherDescription(weatherCode),
                icon = weatherIcon(weatherCode)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Trời quang"
        1, 2 -> "Ít mây"
        3 -> "Nhiều mây"
        45, 48 -> "Sương mù"
        in 51..57 -> "Mưa phùn"
        in 61..67, in 80..82 -> "Có mưa"
        in 71..77, 85, 86 -> "Có tuyết"
        in 95..99 -> "Dông"
        else -> "Thời tiết hiện tại"
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

    private fun openCalendar() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Không tìm thấy ứng dụng Lịch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "HomeScreenFragment"
    }
}
