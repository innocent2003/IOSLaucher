package com.example.oslaucher.ui.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.oslaucher.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

interface HomeScreenActions {
    fun openApps()
    fun openSettings()
}

abstract class HomeScreenFragment : Fragment() {
    protected abstract val layoutResId: Int

    private var receiverRegistered = false
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
        view.findViewById<View>(R.id.open_apps).setOnClickListener {
            (activity as? HomeScreenActions)?.openApps()
        }
        view.findViewById<View>(R.id.home_settings).setOnClickListener {
            (activity as? HomeScreenActions)?.openSettings()
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
        val batteryLevel = requireContext()
            .getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
            ?.let { "$it%" }
            ?: "--%"

        root.findViewById<TextView>(R.id.home_clock)?.text =
            DateFormat.getTimeFormat(requireContext()).format(now)
        root.findViewById<TextView>(R.id.home_date)?.text = localizedDate
        root.findViewById<TextView>(R.id.home_battery)?.text = batteryLevel
        root.findViewById<TextView>(R.id.home_battery_widget)?.text =
            "◉  $batteryLevel\n\n\n$batteryLevel"

        val calendar = Calendar.getInstance().apply { time = now }
        root.findViewById<TextView>(R.id.home_weekday)?.text =
            SimpleDateFormat("EEEE", locale).format(now).uppercase(locale)
        root.findViewById<TextView>(R.id.home_day_number)?.text =
            calendar.get(Calendar.DAY_OF_MONTH).toString()
        root.findViewById<TextView>(R.id.home_month)?.text =
            "THÁNG ${calendar.get(Calendar.MONTH) + 1}"

        val firstDayOffset = (calendar.apply { set(Calendar.DAY_OF_MONTH, 1) }
            .get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthGrid = StringBuilder("T2 T3 T4 T5 T6 T7 CN\n")
        repeat(firstDayOffset) { monthGrid.append("   ") }
        for (day in 1..daysInMonth) {
            monthGrid.append(String.format(Locale.ROOT, "%3d", day))
            if ((firstDayOffset + day) % 7 == 0 && day < daysInMonth) {
                monthGrid.append('\n')
            }
        }
        root.findViewById<TextView>(R.id.home_calendar_grid)?.text = monthGrid
    }
}

class HomeFragment : HomeScreenFragment() {
    override val layoutResId = R.layout.screen_home
}

class HomeSecondFragment : HomeScreenFragment() {
    override val layoutResId = R.layout.screen_home_second
}
