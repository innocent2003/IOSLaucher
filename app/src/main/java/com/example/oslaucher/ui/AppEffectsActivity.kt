package com.example.oslaucher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R
import com.example.oslaucher.models.EffectOption

class AppEffectsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app_effects)

        val root = findViewById<View>(R.id.app_effects_root)
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

        findViewById<View>(R.id.app_effects_back).setOnClickListener { finish() }

        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val checkedTrackTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(GREEN, TRACK_OFF)
        )
        listOf(
            EffectOption(
                R.id.app_effects_continue_row,
                R.id.app_effects_continue,
                CONTINUE_EFFECTS_KEY,
                false
            ),
            EffectOption(
                R.id.app_effects_open_close_row,
                R.id.app_effects_open_close,
                OPEN_CLOSE_EFFECTS_KEY,
                true
            )
        ).forEach { option ->
            val toggle = findViewById<SwitchCompat>(option.switchId).apply {
                isChecked = preferences.getBoolean(option.preferenceKey, option.defaultValue)
                thumbTintList = ColorStateList.valueOf(WHITE)
                trackTintList = checkedTrackTint
                setOnCheckedChangeListener { _, isChecked ->
                    preferences.edit().putBoolean(option.preferenceKey, isChecked).apply()
                }
            }
            findViewById<View>(option.rowId).setOnClickListener {
                toggle.isChecked = !toggle.isChecked
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "launcher_settings"
        private const val CONTINUE_EFFECTS_KEY = "app_effects_continue"
        private const val OPEN_CLOSE_EFFECTS_KEY = "app_effects_open_close"
        private const val GREEN = 0xFF30D16B.toInt()
        private const val TRACK_OFF = 0xFF777777.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
