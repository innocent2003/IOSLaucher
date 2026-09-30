package com.example.oslaucher

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.GridView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import com.example.oslaucher.adapters.AppAdapter
import com.example.oslaucher.repositories.InstalledAppRepository
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()

    private val screenLayouts = mapOf(
        Screen.SETTINGS to R.layout.activity_main,
        Screen.HOME to R.layout.screen_home,
        Screen.APPS to R.layout.screen_apps
    )

    private enum class Screen { SETTINGS, HOME, APPS }
    private var currentScreen = Screen.SETTINGS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    Screen.APPS -> showScreen(Screen.HOME)
                    Screen.HOME -> showScreen(Screen.SETTINGS)
                    Screen.SETTINGS -> finish()
                }
            }
        })
        showScreen(Screen.SETTINGS)
    }

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        setContentView(screenLayouts.getValue(screen))
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        when (screen) {
            Screen.SETTINGS -> findViewById<View>(R.id.open_home).setOnClickListener {
                showScreen(Screen.HOME)
            }
            Screen.HOME -> {
                findViewById<View>(R.id.open_apps).setOnClickListener { showScreen(Screen.APPS) }
                findViewById<View>(R.id.home_settings).setOnClickListener { showScreen(Screen.SETTINGS) }
            }
            Screen.APPS -> {
                val appAdapter = AppAdapter(this) { app ->
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                }
                findViewById<GridView>(R.id.apps_grid).adapter = appAdapter
                val appCount = findViewById<TextView>(R.id.apps_count)
                findViewById<EditText>(R.id.app_search).addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                        appAdapter.filter(text?.toString().orEmpty())
                    }
                    override fun afterTextChanged(text: Editable?) = Unit
                })
                appLoadExecutor.execute {
                    val apps = appRepository.loadInstalledApps()
                    runOnUiThread {
                        if (currentScreen == Screen.APPS) {
                            appAdapter.setApps(apps)
                            appCount.text = getString(R.string.apps_count, apps.size)
                        }
                    }
                }
            }
        }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                start: MotionEvent?,
                end: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (currentScreen == Screen.HOME && start != null && start.y - end.y > 90) {
                    showScreen(Screen.APPS)
                    return true
                }
                if (currentScreen == Screen.APPS && start != null && end.y - start.y > 90) {
                    showScreen(Screen.HOME)
                    return true
                }
                return false
            }
        })
        findViewById<View>(R.id.main).setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    override fun onDestroy() {
        appLoadExecutor.shutdownNow()
        super.onDestroy()
    }
}