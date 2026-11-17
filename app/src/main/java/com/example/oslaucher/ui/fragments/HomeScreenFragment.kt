package com.example.oslaucher.ui.fragments

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
import com.example.oslaucher.utils.LauncherWallpaper
import com.example.oslaucher.ui.widgets.BatteryRingView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

abstract class HomeScreenFragment : Fragment() {
    protected abstract val layoutResId: Int

    private var receiverRegistered = false
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
    }
}
