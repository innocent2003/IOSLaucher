package com.example.oslaucher.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.OslauncherApp
import com.example.oslaucher.R

class AppearanceActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    private var selectedMode = LIGHT_MODE
    private var autoEnabled = true
    private var systemEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_appearance)

        val root = findViewById<View>(R.id.appearance_root)
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

        preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        selectedMode = preferences.getInt(MODE_KEY, LIGHT_MODE).coerceIn(LIGHT_MODE, DARK_MODE)
        autoEnabled = preferences.getBoolean(AUTO_KEY, true)
        systemEnabled = preferences.getBoolean(SYSTEM_KEY, false)

        findViewById<View>(R.id.appearance_back).setOnClickListener { finish() }
        findViewById<View>(R.id.appearance_light_option).setOnClickListener {
            selectMode(LIGHT_MODE)
        }
        findViewById<View>(R.id.appearance_dark_option).setOnClickListener {
            selectMode(DARK_MODE)
        }

        findViewById<View>(R.id.appearance_auto_row).setOnClickListener {
            autoEnabled = !autoEnabled
            preferences.edit().putBoolean(AUTO_KEY, autoEnabled).apply()
            updateSwitches()
        }
        findViewById<View>(R.id.appearance_system_row).setOnClickListener {
            systemEnabled = !systemEnabled
            preferences.edit().putBoolean(SYSTEM_KEY, systemEnabled).apply()
            updateSwitches()
            updateModeSelection()
            OslauncherApp.applyNightMode(this)
        }

        createHomePreview(R.id.appearance_light_preview, isDark = false)
        createHomePreview(R.id.appearance_dark_preview, isDark = true)
        updateModeSelection()
        updateSwitches()
    }

    /**
     * Lưu lựa chọn rồi áp dụng ngay cho toàn app: setDefaultNightMode làm AppCompat
     * recreate mọi activity đang sống, nên không cần khởi động lại app.
     */
    private fun selectMode(mode: Int) {
        if (selectedMode == mode && !systemEnabled) return
        selectedMode = mode
        systemEnabled = false
        preferences.edit()
            .putInt(MODE_KEY, mode)
            .putBoolean(SYSTEM_KEY, false)
            .apply()
        updateModeSelection()
        updateSwitches()
        OslauncherApp.applyNightMode(this)
    }

    private fun createHomePreview(containerId: Int, isDark: Boolean) {
        val container = findViewById<FrameLayout>(containerId)
        container.background = roundedBackground(
            if (isDark) 0xFF38383A.toInt() else 0xFFFFFDFB.toInt(),
            dp(18).toFloat()
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(10), dp(4), dp(4))
        }
        val status = TextView(this).apply {
            text = "9:41"
            textSize = 6f
            gravity = Gravity.CENTER
            setTextColor(if (isDark) Color.WHITE else 0xFF333333.toInt())
        }
        content.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(14)
            )
        )

        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 6
        }
        PREVIEW_ICONS.forEachIndexed { index, icon ->
            val tile = TextView(this).apply {
                text = icon
                textSize = 7f
                gravity = Gravity.CENTER
                background = roundedBackground(PREVIEW_COLORS[index])
                setTextColor(Color.WHITE)
            }
            val margin = dp(2)
            grid.addView(
                tile,
                GridLayout.LayoutParams().apply {
                    width = dp(20)
                    height = dp(20)
                    setMargins(margin, margin, margin, margin)
                }
            )
        }
        content.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val dock = TextView(this).apply {
            text = "☎   ●   ◉   ●"
            textSize = 8f
            gravity = Gravity.CENTER
            setTextColor(if (isDark) Color.WHITE else 0xFF454545.toInt())
            background = roundedBackground(if (isDark) 0xFF555557.toInt() else 0xFFE7E7E7.toInt())
        }
        val dockParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(24)
        ).apply {
            topMargin = dp(10)
            marginStart = dp(4)
            marginEnd = dp(4)
        }
        content.addView(dock, dockParams)
        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun updateModeSelection() {
        val activeMode = if (systemEnabled) systemMode() else selectedMode
        val modeSelectable = !systemEnabled
        updateRadio(R.id.appearance_light_radio, activeMode == LIGHT_MODE)
        updateRadio(R.id.appearance_dark_radio, activeMode == DARK_MODE)
        findViewById<View>(R.id.appearance_light_option).apply {
            isClickable = modeSelectable
            alpha = if (modeSelectable) 1f else 0.4f
        }
        findViewById<View>(R.id.appearance_dark_option).apply {
            isClickable = modeSelectable
            alpha = if (modeSelectable) 1f else 0.4f
        }
    }

    private fun systemMode(): Int {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) DARK_MODE else LIGHT_MODE
    }

    private fun updateRadio(viewId: Int, selected: Boolean) {
        val radio = findViewById<TextView>(viewId)
        radio.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (selected) color(R.color.settings_surface_secondary) else Color.TRANSPARENT
            )
            setStroke(
                dp(2),
                if (selected) color(R.color.settings_accent) else color(R.color.settings_radio_border)
            )
        }
        radio.text = if (selected) "●" else ""
    }

    private fun updateSwitches() {
        updateSwitch(R.id.appearance_auto_switch, R.id.appearance_auto_thumb, autoEnabled)
        updateSwitch(R.id.appearance_system_switch, R.id.appearance_system_thumb, systemEnabled)
    }

    private fun updateSwitch(trackId: Int, thumbId: Int, enabled: Boolean) {
        val track = findViewById<FrameLayout>(trackId)
        val thumb = findViewById<View>(thumbId)
        track.background = roundedBackground(
            if (enabled) color(R.color.settings_switch_on) else color(R.color.settings_switch_off)
        )
        thumb.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color(R.color.settings_thumb))
        }
        val params = thumb.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or
            (if (enabled) Gravity.END else Gravity.START)
        thumb.layoutParams = params
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(24).toFloat()
        setColor(color)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFERENCES_NAME = "launcher_settings"
        private const val MODE_KEY = "appearance_mode"
        private const val AUTO_KEY = "appearance_auto"
        private const val SYSTEM_KEY = "appearance_system"
        private const val LIGHT_MODE = 0
        private const val DARK_MODE = 1

        private val PREVIEW_ICONS = arrayOf(
            "🌈", "▥", "✎", "⚑",
            "▤", "▦", "31", "◉",
            "◷", "✣", "◉", "▣",
            "▰", "🎨", "🎬", "◉",
            "A", "♫", "♥", "⌂",
            "●", "✉", "▧", "☼"
        )
        private val PREVIEW_COLORS = intArrayOf(
            0xFFFF9558.toInt(), 0xFF35C97B.toInt(), 0xFFFFA22E.toInt(), 0xFF55A8EF.toInt(),
            0xFFFFB947.toInt(), 0xFFE1E1E1.toInt(), 0xFFFFFFFF.toInt(), 0xFF777777.toInt(),
            0xFFFFFFFF.toInt(), 0xFF55545E.toInt(), 0xFFDBD9D3.toInt(), 0xFF25D366.toInt(),
            0xFF4EA7E8.toInt(), 0xFF6757B8.toInt(), 0xFFFF684D.toInt(), 0xFF44BA75.toInt(),
            0xFF8DDBF3.toInt(), 0xFFFF7355.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
            0xFF4CBED3.toInt(), 0xFFEA755B.toInt(), 0xFFFFFFFF.toInt(), 0xFF55A8EF.toInt()
        )
    }
}
