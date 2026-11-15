package com.example.oslaucher.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }

        findViewById<View>(R.id.open_home).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setClass(this, MainActivity::class.java)
            )
            finish()
        }
        findViewById<View>(R.id.default_home_screen_setting).setOnClickListener {
            requestDefaultHomeRole()
        }
        findViewById<View>(R.id.default_home_screen_label).setOnClickListener {
            requestDefaultHomeRole()
        }
        findViewById<View>(R.id.appearance_setting).setOnClickListener { open(AppearanceActivity::class.java) }
        findViewById<View>(R.id.general_setting).setOnClickListener { open(GeneralSettingsActivity::class.java) }
        findViewById<View>(R.id.hidden_apps_setting).setOnClickListener { open(HiddenAppsActivity::class.java) }
        findViewById<View>(R.id.language_setting).setOnClickListener { open(LanguageActivity::class.java) }
        findViewById<View>(R.id.screen_grid_setting).setOnClickListener { open(ScreenGridActivity::class.java) }
        findViewById<View>(R.id.page_transition_setting).setOnClickListener { open(PageTransitionActivity::class.java) }
        findViewById<View>(R.id.wallpaper_setting).setOnClickListener { open(WallpaperActivity::class.java) }
        findViewById<View>(R.id.weather_setting).setOnClickListener { open(WeatherSettingsActivity::class.java) }
        findViewById<View>(R.id.app_effects_setting).setOnClickListener { open(AppEffectsActivity::class.java) }
        findViewById<View>(R.id.change_app_icon_setting).setOnClickListener { open(ChangeAppIconActivity::class.java) }
    }

    private fun open(activity: Class<*>) = startActivity(Intent(this, activity))

    private fun requestDefaultHomeRole() {
        AlertDialog.Builder(this)
            .setTitle(R.string.default_home_confirm_title)
            .setMessage(R.string.default_home_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.continue_action) { _, _ -> openDefaultHomeSelection() }
            .show()
    }

    private fun openDefaultHomeSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                try {
                    startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                } catch (exception: ActivityNotFoundException) {
                    Log.w(TAG, "Unable to request the default home role", exception)
                }
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (exception: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addCategory(Intent.CATEGORY_DEFAULT)
            try {
                startActivity(fallback)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.home_settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val TAG = "SettingsActivity"
    }
}
