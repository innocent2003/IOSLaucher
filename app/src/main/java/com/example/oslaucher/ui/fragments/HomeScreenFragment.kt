package com.example.oslaucher.ui.fragments

import android.annotation.SuppressLint
import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.provider.CalendarContract
import android.util.TypedValue
import androidx.activity.result.contract.ActivityResultContracts
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.oslaucher.models.Weather
import com.example.oslaucher.utils.LauncherWallpaper
import com.example.oslaucher.utils.WeatherWidgetPreferences
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
        override fun onReceive(context: Context?, intent: Intent?) =
            view?.let(::updateWidgets) ?: Unit
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Re-use the page built last time: inflating it, decoding the wallpaper, building the
        // calendar and loading weather on every swipe made the start of each swipe stutter.
        val actions = activity as? HomeScreenActions
        actions?.cachedHomeScreenView(layoutResId)
            ?.takeIf { it.parent == null }
            ?.let {
                isReusedView = true
                return it
            }
        isReusedView = false
        return inflater.inflate(layoutResId, container, false).also {
            actions?.storeHomeScreenView(layoutResId, it)
        }
    }

    /** True when [onCreateView] handed back an already built page instead of a new one. */
    protected var isReusedView = false
        private set

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isReusedView) setUpRoot(view)
        view.findViewById<View?>(R.id.home_settings)?.setOnClickListener {
            (activity as? HomeScreenActions)?.openSettings()
        }
        view.findViewById<View?>(R.id.home_weather_widget)?.setOnClickListener {
            refreshWeather(force = true)
        }
        view.findViewById<View?>(R.id.home_calendar_widget)?.setOnClickListener {
            openCalendar()
        }
        // Tapping "today" asks once for calendar access so today's events can be shown.
        view.findViewById<View?>(R.id.home_calendar_today)?.setOnClickListener {
            if (hasCalendarPermission()) openCalendar()
            else calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
        updateWidgets(view)
    }

    private fun setUpRoot(view: View) {
        view.findViewById<View>(R.id.main)?.let(LauncherWallpaper::applyTo)
        view.findViewById<View>(R.id.main)?.let { root ->
            val baseLeft = root.paddingLeft
            val baseTop = root.paddingTop
            val baseRight = root.paddingRight
            val baseBottom = root.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(root) { target, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                target.setPadding(
                    baseLeft + systemBars.left,
                    baseTop + systemBars.top,
                    baseRight + systemBars.right,
                    baseBottom + systemBars.bottom
                )
                insets
            }
            // Apply the current system-bar insets right away: waiting for the posted inset pass
            // made the page jump down by the status bar height mid-swipe.
            ViewCompat.getRootWindowInsets(requireActivity().window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.systemBars())
                ?.let { bars ->
                    root.setPadding(
                        baseLeft + bars.left,
                        baseTop + bars.top,
                        baseRight + bars.right,
                        baseBottom + bars.bottom
                    )
                }
            root.post { ViewCompat.requestApplyInsets(root) }
        }
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

    override fun onResume() {
        super.onResume()
        // Re-apply only when the background has not been set yet (e.g. activity resume
        // after returning from Settings). Skip when bindScreen already applied it during
        // a swipe gesture to avoid an expensive redundant call per swipe.
        view?.findViewById<View>(R.id.main)?.let { root ->
            if (root.background == null) {
                LauncherWallpaper.applyTo(root)
            }
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
        val locale = resources.configuration.locales[0]
        val localizedDate = SimpleDateFormat("EEEE, d MMMM", locale)
            .format(now)
            .uppercase(locale)
        val batteryPercent = requireContext()
            .getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        val batteryLevel = batteryPercent
            ?.let { "$it%" }
            ?: "--%"

        root.findViewById<TextView>(R.id.home_date)?.text = localizedDate
        root.findViewById<TextView>(R.id.home_battery_widget)?.text = batteryLevel
        root.findViewById<BatteryRingView>(R.id.home_battery_ring)?.batteryLevel = batteryPercent
        root.findViewById<View>(R.id.home_analog_clock)?.invalidate()

        updateMiniCalendar(root, now, locale)
    }

    private fun updateMiniCalendar(root: View, now: Date, locale: Locale) {
        val monthView = root.findViewById<TextView?>(R.id.home_calendar_month) ?: return
        val grid = root.findViewById<GridLayout?>(R.id.home_calendar_widget_grid) ?: return
        val calendar = Calendar.getInstance().apply { time = now }
        val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-" +
            calendar.get(Calendar.DAY_OF_MONTH)
        // updateWidgets runs on every battery/time tick broadcast and on every re-used page;
        // rebuilding the cells drops frames, so only redraw when the day changes. The day is
        // kept on the grid itself so it survives the page being re-used by a new fragment.
        if (grid.tag == dayKey && grid.childCount > 0) return
        grid.tag = dayKey

        // Left side: "THỨ BA" / "17" / today's events.
        root.findViewById<TextView?>(R.id.home_calendar_weekday)?.text =
            SimpleDateFormat("EEEE", locale).format(now).uppercase(locale)
        root.findViewById<TextView?>(R.id.home_calendar_day)?.text =
            calendar.get(Calendar.DAY_OF_MONTH).toString()
        updateTodayEvents(root)

        // Right side: "THÁNG 11", M T W T F S S and the days, today in a red circle.
        monthView.text = SimpleDateFormat("LLLL", locale).format(now).uppercase(locale)
        val firstDay = calendar.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weekRows = (offset + daysInMonth + 6) / 7
        grid.removeAllViews()
        grid.rowCount = weekRows + 1
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { column, label ->
            addCalendarCell(grid, label, 0, column, false)
        }
        for (index in 0 until weekRows * 7) {
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

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    private val calendarPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            view?.let(::updateTodayEvents)
        }

    /** Shows today's first event (and how many more), read in the background. */
    private fun updateTodayEvents(root: View) {
        val eventsView = root.findViewById<TextView?>(R.id.home_calendar_events) ?: return
        if (!hasCalendarPermission()) {
            eventsView.text = getString(R.string.calendar_tap_for_events)
            return
        }
        val resolver = requireContext().applicationContext.contentResolver
        val noEvents = getString(R.string.calendar_no_events_today)
        Thread {
            val titles = try {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                    ContentUris.appendId(it, start.timeInMillis)
                    ContentUris.appendId(it, end.timeInMillis)
                }.build()
                resolver.query(
                    uri,
                    arrayOf(CalendarContract.Instances.TITLE),
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC"
                )?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            cursor.getString(0)?.takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
            } catch (error: SecurityException) {
                Log.w(TAG, "Calendar permission revoked", error)
                emptyList()
            }
            Handler(Looper.getMainLooper()).post {
                eventsView.text = when (titles.size) {
                    0 -> noEvents
                    1 -> titles.first()
                    else -> eventsView.context.getString(
                        R.string.calendar_more_events,
                        titles.first(),
                        titles.size - 1
                    )
                }
            }
        }.apply {
            name = "calendar-events-loader"
            start()
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
            includeFontPadding = false
            // dp, not sp: a large system font must not overflow the fixed-height card.
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
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

    @SuppressLint("StringFormatMatches")
    private fun refreshWeather(force: Boolean = false) {
        if (weatherRequestInProgress) return
        val root = view ?: return
        val condition = root.findViewById<TextView?>(R.id.home_weather_condition) ?: return
        val weatherWidget = root.findViewById<View?>(R.id.home_weather_widget)
        val loadedAt = weatherWidget?.getTag(R.id.home_weather_widget) as? Long
        // Each swipe back to this page used to hit the network and blank the widget again.
        if (!force && loadedAt != null &&
            System.currentTimeMillis() - loadedAt < WEATHER_REFRESH_INTERVAL_MS
        ) return
        val temperatureUnit = WeatherWidgetPreferences.temperatureUnit(requireContext())
        val temperatureSymbol = if (temperatureUnit == WeatherWidgetPreferences.CELSIUS) {
            "°C"
        } else {
            "°F"
        }
        weatherRequestInProgress = true
        condition.text = condition.context.getString(R.string.weather_updating)
        root.findViewById<TextView?>(R.id.home_weather_temperature)?.text = "--$temperatureSymbol"
        root.findViewById<TextView?>(R.id.home_weather_range)?.text = getString(
            R.string.weather_range,
            "--",
            "--",
            temperatureSymbol
        )

        Thread {
            val result = try {
                Result.success(loadWeather(temperatureUnit))
            } catch (error: IOException) {
                Result.failure(error)
            } catch (error: JSONException) {
                Result.failure(error)
            }
            Handler(Looper.getMainLooper()).post {
                weatherRequestInProgress = false
                val currentView = view ?: return@post
                val temperature = currentView.findViewById<TextView?>(R.id.home_weather_temperature)
                val icon = currentView.findViewById<TextView?>(R.id.home_weather_icon)
                val currentCondition = currentView.findViewById<TextView?>(R.id.home_weather_condition)
                val range = currentView.findViewById<TextView?>(R.id.home_weather_range)
                result.onSuccess { weather ->
                    currentView.findViewById<View?>(R.id.home_weather_widget)
                        ?.setTag(R.id.home_weather_widget, System.currentTimeMillis())
                    temperature?.text = "${weather.temperature.toInt()}$temperatureSymbol"
                    icon?.text = weather.icon
                    currentCondition?.text =
                        currentView.context.getString(weather.descriptionResId)
                    range?.text = currentView.context.getString(
                        R.string.weather_range,
                        weather.high.toInt(),
                        weather.low.toInt(),
                        temperatureSymbol
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Unable to load weather", error)
                    currentCondition?.text =
                        currentView.context.getString(R.string.weather_unavailable)
                    range?.text = currentView.context.getString(R.string.weather_retry)
                }
            }
        }.apply {
            name = "weather-widget-loader"
            start()
        }
    }

    private fun loadWeather(temperatureUnit: String): Weather {
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
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=$apiTemperatureUnit" +
                "&timezone=Asia%2FBangkok&forecast_days=1"
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
            val daily = json.getJSONObject("daily")
            val weatherCode = current.getInt("weather_code")
            return Weather(
                temperature = current.getDouble("temperature_2m"),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0),
                descriptionResId = weatherDescription(weatherCode),
                icon = weatherIcon(weatherCode)
            )
        } finally {
            connection.disconnect()
        }
    }

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

    private fun openCalendar() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(
                requireContext(),
                R.string.calendar_not_found,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "HomeScreenFragment"
        const val WEATHER_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    }
}
