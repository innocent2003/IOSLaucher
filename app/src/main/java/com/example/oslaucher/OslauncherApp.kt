package com.example.oslaucher

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class OslauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightMode(this)
    }

    companion object {
        private const val PREFERENCES_NAME = "launcher_settings"
        private const val MODE_KEY = "appearance_mode"
        private const val SYSTEM_KEY = "appearance_system"
        private const val LIGHT_MODE = 0
        private const val DARK_MODE = 1

        /**
         * Đọc preference đã lưu và apply night mode cho toàn app.
         * Gọi khi app khởi động và mỗi khi user thay đổi chế độ sáng/tối.
         */
        fun applyNightMode(context: Context) {
            val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val nightMode = when {
                prefs.getBoolean(SYSTEM_KEY, false) ->
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                prefs.getInt(MODE_KEY, LIGHT_MODE) == DARK_MODE ->
                    AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}
