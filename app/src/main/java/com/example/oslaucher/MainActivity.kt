package com.example.oslaucher

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.adapters.AppAdapter
import com.example.oslaucher.repositories.InstalledAppRepository
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector: GestureDetector
    private lateinit var screenContainer: FrameLayout
    private var appsPages: ViewFlipper? = null
    private var appsIndicator: LinearLayout? = null
    private var appPageIndex = 0
    private var appPageCount = 1

    private val screenLayouts = mapOf(
        Screen.SETTINGS to R.layout.activity_main,
        Screen.HOME to R.layout.screen_home,
        Screen.HOME_SECOND to R.layout.screen_home_second,
        Screen.APPS to R.layout.screen_apps
    )

    private enum class Screen { SETTINGS, HOME, HOME_SECOND, APPS }
    private var currentScreen = Screen.SETTINGS
    private var lastHomeScreen = Screen.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        screenContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(screenContainer)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                start: MotionEvent?,
                end: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (start == null) return false

                val distanceX = end.x - start.x
                val distanceY = end.y - start.y
                if (abs(distanceX) > abs(distanceY) && abs(distanceX) > 80) {
                    when {
                        currentScreen == Screen.HOME && distanceX < 0 -> showScreen(Screen.HOME_SECOND, if (distanceX < 0) 1 else -1)
                        currentScreen == Screen.HOME_SECOND && distanceX > 0 -> showScreen(Screen.HOME, if (distanceX > 0) -1 else 1)
                        currentScreen == Screen.APPS -> {
                            moveAppPage(if (distanceX < 0) 1 else -1)
                            return true
                        }
                        else -> return false
                    }
                    return true
                }

                if (abs(distanceY) > 90) {
                    if ((currentScreen == Screen.HOME || currentScreen == Screen.HOME_SECOND) && distanceY < 0) {
                        showScreen(Screen.APPS, 2)
                        return true
                    }
                    if (currentScreen == Screen.APPS && distanceY > 0) {
                        showScreen(lastHomeScreen, -2)
                        return true
                    }
                }
                return false
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    Screen.APPS -> showScreen(lastHomeScreen, -2)
                    Screen.HOME_SECOND -> showScreen(Screen.HOME, -1)
                    Screen.HOME -> showScreen(Screen.SETTINGS, -1)
                    Screen.SETTINGS -> finish()
                }
            }
        })

        showScreen(Screen.SETTINGS, 0)
    }

    private fun showScreen(screen: Screen, animationDirection: Int = 0) {
        currentScreen = screen
        if (screen == Screen.HOME || screen == Screen.HOME_SECOND) {
            lastHomeScreen = screen
        }

        val previousView = screenContainer.childCount.takeIf { it > 0 }?.let(screenContainer::getChildAt)
        val newView = LayoutInflater.from(this).inflate(screenLayouts.getValue(screen), screenContainer, false)
        bindScreen(newView, screen)

        if (previousView != null && animationDirection != 0) {
            val horizontal = abs(animationDirection) == 1
            val offset = if (horizontal) {
                (screenContainer.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
            } else {
                (screenContainer.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels).toFloat()
            }

            val enterOffset = when {
                horizontal && animationDirection > 0 -> offset
                horizontal && animationDirection < 0 -> -offset
                !horizontal && animationDirection > 0 -> offset
                else -> -offset
            }

            newView.translationX = if (horizontal) enterOffset else 0f
            newView.translationY = if (!horizontal) enterOffset else 0f
            newView.alpha = 1f
            screenContainer.addView(newView)

            previousView.animate()
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .translationX(if (horizontal) -enterOffset else 0f)
                .translationY(if (!horizontal) -enterOffset else 0f)
                .alpha(0f)
                .withEndAction {
                    screenContainer.removeView(previousView)
                }
                .start()

            newView.animate()
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
                .start()
            return
        }

        screenContainer.removeAllViews()
        screenContainer.addView(newView)
    }

    private fun bindScreen(view: View, screen: Screen) {
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        when (screen) {
            Screen.SETTINGS -> view.findViewById<View>(R.id.open_home).setOnClickListener {
                showScreen(Screen.HOME, 1)
            }
            Screen.HOME, Screen.HOME_SECOND -> {
                view.findViewById<View>(R.id.open_apps).setOnClickListener { showScreen(Screen.APPS, 2) }
                view.findViewById<View>(R.id.home_settings).setOnClickListener { showScreen(Screen.SETTINGS, -1) }
            }
            Screen.APPS -> {
                val appPagesView = view.findViewById<ViewFlipper>(R.id.apps_pages)
                val pageIndicator = view.findViewById<LinearLayout>(R.id.apps_page_indicator)
                appsPages = appPagesView
                appsIndicator = pageIndicator
                appPageIndex = 0
                val appCount = view.findViewById<TextView>(R.id.apps_count)
                val searchBox = view.findViewById<EditText>(R.id.app_search)
                val appLauncher: (com.example.oslaucher.models.AppInfo) -> Unit = { app ->
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                }

                fun buildPages(apps: List<com.example.oslaucher.models.AppInfo>, query: String = searchBox.text?.toString().orEmpty()) {
                    val filteredApps = if (query.isBlank()) {
                        apps
                    } else {
                        apps.filter { it.name.contains(query.trim(), ignoreCase = true) }
                    }

                    val pageSize = 12
                    appPageCount = if (filteredApps.isEmpty()) 1 else ((filteredApps.size + pageSize - 1) / pageSize).coerceAtLeast(1)
                    val targetPage = appPageIndex.coerceIn(0, (appPageCount - 1).coerceAtLeast(0))
                    appPageIndex = targetPage

                    appPagesView.removeAllViews()
                    pageIndicator.removeAllViews()

                    for (pageIndex in 0 until appPageCount) {
                        val pageApps = filteredApps.drop(pageIndex * pageSize).take(pageSize)
                        val gridView = GridView(this).apply {
                            numColumns = 4
                            horizontalSpacing = 12
                            verticalSpacing = 16
                            stretchMode = GridView.STRETCH_COLUMN_WIDTH
                            isVerticalScrollBarEnabled = false
                            background = null
                            adapter = AppAdapter(this@MainActivity, appLauncher).apply {
                                setApps(pageApps)
                            }
                        }
                        appPagesView.addView(gridView)

                        val dot = TextView(this).apply {
                            text = "•"
                            setTextColor(0xFF9DB7D0.toInt())
                            textSize = 20f
                            alpha = if (pageIndex == targetPage) 1f else 0.45f
                            setPadding(8, 0, 8, 0)
                            setOnClickListener {
                                appPageIndex = pageIndex
                                appPagesView.displayedChild = pageIndex
                                updateAppPageIndicator()
                            }
                        }
                        pageIndicator.addView(dot)
                    }

                    appPagesView.displayedChild = targetPage
                    updateAppPageIndicator()
                    appCount.text = getString(R.string.apps_count, filteredApps.size)
                }

                searchBox.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                        if (appsPages == null) return
                        buildPages(getCurrentAppListSnapshot(), text?.toString().orEmpty())
                    }
                    override fun afterTextChanged(text: Editable?) = Unit
                })

                appLoadExecutor.execute {
                    val apps = appRepository.loadInstalledApps()
                    runOnUiThread {
                        if (currentScreen == Screen.APPS) {
                            buildPages(apps)
                        }
                    }
                }
            }
        }
    }

    private fun getCurrentAppListSnapshot(): List<com.example.oslaucher.models.AppInfo> {
        return appRepository.loadInstalledApps()
    }

    private fun moveAppPage(delta: Int) {
        if (appPageCount <= 1) return
        val nextIndex = (appPageIndex + delta).coerceIn(0, appPageCount - 1)
        if (nextIndex == appPageIndex) return
        appPageIndex = nextIndex
        appsPages?.displayedChild = nextIndex
        updateAppPageIndicator()
    }

    private fun updateAppPageIndicator() {
        val indicator = appsIndicator ?: return
        for (i in 0 until indicator.childCount) {
            val dot = indicator.getChildAt(i) as? TextView ?: continue
            val selected = i == appPageIndex
            dot.alpha = if (selected) 1f else 0.45f
            dot.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF9DB7D0.toInt())
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return handled
    }

    override fun onDestroy() {
        appLoadExecutor.shutdownNow()
        super.onDestroy()
    }
}