package com.example.oslaucher.ui

import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.repositories.InstalledAppRepository
import com.example.oslaucher.utils.HiddenAppsPreferences
import java.util.concurrent.Executors

class HiddenAppsActivity : AppCompatActivity() {
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var appsContainer: LinearLayout
    private lateinit var emptyMessage: TextView
    private var hiddenApps = emptyList<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_hidden_apps)

        val root = findViewById<View>(R.id.hidden_apps_root)
        val isNightMode = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
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

        findViewById<View>(R.id.hidden_apps_back).setOnClickListener { finish() }
        appsContainer = findViewById(R.id.hidden_apps_list)
        emptyMessage = findViewById(R.id.hidden_apps_empty)
        loadHiddenApps()
    }

    override fun onDestroy() {
        appLoadExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadHiddenApps() {
        val hiddenPackages = HiddenAppsPreferences.packages(this)
        appLoadExecutor.execute {
            val installedApps = InstalledAppRepository(applicationContext)
                .loadInstalledApps()
            val apps = installedApps.filter { it.packageName in hiddenPackages }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                hiddenApps = apps
                HiddenAppsPreferences.save(this, apps.mapTo(mutableSetOf()) { it.packageName })
                renderApps()
            }
        }
    }

    private fun renderApps() {
        appsContainer.removeAllViews()
        emptyMessage.visibility = if (hiddenApps.isEmpty()) View.VISIBLE else View.GONE
        hiddenApps.forEach { app ->
            appsContainer.addView(
                createAppRow(app),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(62)
                ).apply {
                    bottomMargin = dp(12)
                }
            )
        }
    }

    private fun createAppRow(app: AppInfo): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(14), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(
                    ContextCompat.getColor(
                        this@HiddenAppsActivity,
                        R.color.settings_surface_secondary
                    )
                )
            }
        }
        val unhideButton = TextView(this).apply {
            text = "−"
            gravity = Gravity.CENTER
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HiddenAppsActivity, R.color.white))
            contentDescription = getString(R.string.unhide_app_accessibility, app.name)
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    ContextCompat.getColor(
                        this@HiddenAppsActivity,
                        R.color.hidden_apps_remove
                    )
                )
            }
            setOnClickListener {
                HiddenAppsPreferences.unhide(this@HiddenAppsActivity, app.packageName)
                hiddenApps = hiddenApps.filterNot { it.packageName == app.packageName }
                renderApps()
            }
        }
        row.addView(
            unhideButton,
            LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginEnd = dp(24)
            }
        )
        row.addView(
            ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.name
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginEnd = dp(16)
            }
        )
        row.addView(
            TextView(this).apply {
                text = app.name
                textSize = 16f
                setTextColor(
                    ContextCompat.getColor(
                        this@HiddenAppsActivity,
                        R.color.settings_text_primary
                    )
                )
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
