package com.example.oslaucher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R
import com.example.oslaucher.models.SwitchOption

class GeneralSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_general)

        val root = findViewById<View>(R.id.general_settings_root)
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

        findViewById<View>(R.id.general_settings_back).setOnClickListener { finish() }

        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val options = listOf(
            SwitchOption(
                R.id.general_hide_navigation_row,
                R.id.general_hide_navigation,
                HIDE_NAVIGATION_KEY,
                false
            ),
            SwitchOption(
                R.id.general_auto_arrange_row,
                R.id.general_auto_arrange,
                AUTO_ARRANGE_KEY,
                false
            ),
            SwitchOption(R.id.general_blur_row, R.id.general_blur, BLUR_KEY, true),
            SwitchOption(
                R.id.general_flexible_text_row,
                R.id.general_flexible_text,
                FLEXIBLE_TEXT_KEY,
                true
            )
        )
        val checkedTrackTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(GREEN, ContextCompat.getColor(this, R.color.settings_switch_off))
        )
        options.forEach { option ->
            val toggle = findViewById<SwitchCompat>(option.switchId).apply {
                isChecked = preferences.getBoolean(option.preferenceKey, option.defaultValue)
                thumbTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@GeneralSettingsActivity, R.color.settings_thumb)
                )
                trackTintList = checkedTrackTint
                setOnCheckedChangeListener { _, isChecked ->
                    preferences.edit().putBoolean(option.preferenceKey, isChecked).apply()
                }
            }
            findViewById<View>(option.rowId).setOnClickListener {
                toggle.isChecked = !toggle.isChecked
            }
        }

        val sizeSlider = findViewById<SeekBar>(R.id.general_icon_size)
        val sizeValue = findViewById<TextView>(R.id.general_icon_size_value)
        val previewIcon = findViewById<View>(R.id.general_preview_icon)
        sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val percentage =
                    MIN_ICON_SIZE_PERCENT + progress * ICON_SIZE_PERCENT_RANGE / seekBar.max
                sizeValue.text = "$percentage%"
                val scale = percentage / 100f
                previewIcon.scaleX = scale
                previewIcon.scaleY = scale
                if (fromUser) {
                    preferences.edit().putInt(ICON_SIZE_KEY, progress).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        sizeSlider.progress = preferences.getInt(ICON_SIZE_KEY, DEFAULT_ICON_SIZE_PROGRESS)
            .coerceIn(0, sizeSlider.max)
    }

    companion object {
        private const val PREFERENCES_NAME = "launcher_settings"
        private const val HIDE_NAVIGATION_KEY = "general_hide_navigation"
        private const val AUTO_ARRANGE_KEY = "general_auto_arrange"
        private const val BLUR_KEY = "general_blur_effect"
        private const val FLEXIBLE_TEXT_KEY = "general_flexible_text_size"
        private const val ICON_SIZE_KEY = "general_icon_size_progress"
        private const val DEFAULT_ICON_SIZE_PROGRESS = 20
        private const val MIN_ICON_SIZE_PERCENT = 50
        private const val ICON_SIZE_PERCENT_RANGE = 100
        private const val GREEN = 0xFF30D16B.toInt()
    }
}
