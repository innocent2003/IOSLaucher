package com.example.oslaucher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R
import com.example.oslaucher.adapters.AppAdapter
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.repositories.InstalledAppRepository
import com.example.oslaucher.ui.fragments.HomeFragment
import com.example.oslaucher.ui.fragments.HomeScreenActions
import com.example.oslaucher.ui.fragments.HomeSecondFragment
import com.example.oslaucher.utils.AppIconUtils
import com.example.oslaucher.utils.LauncherWallpaper
import com.example.oslaucher.utils.Screen
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity(), HomeScreenActions {
    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var screenContainer: FrameLayout
    private var installedApps: List<AppInfo>? = null
    private var renderApps: ((List<AppInfo>) -> Unit)? = null
    private var appsPages: ViewFlipper? = null
    private var appsIndicator: LinearLayout? = null
    private var appPageIndex = 0
    private var appPageCount = 1
    private var appPageSwipeTransition: AppPageSwipeTransition? = null
    private var isLoadingApps = false
    private var isAppSelectionMode = false
    private val selectedAppPackages = mutableSetOf<String>()
    private val appFolders = mutableListOf<AppFolder>()
    private var downX = 0f
    private var downY = 0f
    private var homeSwipeTransition: HomeSwipeTransition? = null
    private var homeSwipeSettling = false
    private var ignoreTouchSequence = false
    private var velocityTracker: VelocityTracker? = null
    private val hiddenAppPackages = mutableSetOf<String>()

    private data class AppFolder(
        val id: String,
        val name: String,
        val packageNames: List<String>
    )
    private val swipeTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val swipeMinVelocity by lazy { ViewConfiguration.get(this).scaledMinimumFlingVelocity }

    private val screenLayouts = mapOf(
        Screen.SETTINGS to R.layout.activity_main,
        Screen.APPS to R.layout.screen_apps
    )

    private var currentScreen = Screen.SETTINGS
    private var lastHomeScreen = Screen.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        screenContainer = FrameLayout(this).apply {
            id = R.id.screen_container
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(screenContainer)
        appFolders.addAll(loadAppFolders())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAppSelectionMode) {
                    exitAppSelectionMode()
                    return
                }
                when (currentScreen) {
                    Screen.APPS -> showScreen(
                        lastHomeScreen,
                        if (lastHomeScreen == Screen.HOME) 1 else -1
                    )
                    Screen.HOME_SECOND -> showScreen(Screen.HOME, -1)
                    Screen.HOME -> showScreen(Screen.SETTINGS, -1)
                    Screen.SETTINGS -> finish()
                }
            }
        })

        showScreen(Screen.SETTINGS, 0)
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen == Screen.APPS) {
            screenContainer.getChildAt(0)
                ?.findViewById<View>(R.id.main)
                ?.let(LauncherWallpaper::applyTo)
        }
    }

    private fun showScreen(screen: Screen, animationDirection: Int = 0) {
        currentScreen = screen
        if (screen == Screen.HOME || screen == Screen.HOME_SECOND) {
            lastHomeScreen = screen
            showHomeFragment(screen, animationDirection)
            return
        }

        val previousFragment = supportFragmentManager.findFragmentById(R.id.screen_container)
        val previousView = previousFragment?.view
            ?: screenContainer.childCount.takeIf { it > 0 }?.let(screenContainer::getChildAt)
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
                    if (previousFragment != null) {
                        supportFragmentManager.beginTransaction()
                            .remove(previousFragment)
                            .commitNow()
                    } else {
                        screenContainer.removeView(previousView)
                    }
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

        if (previousFragment != null) {
            supportFragmentManager.beginTransaction()
                .remove(previousFragment)
                .commitNow()
        }
        screenContainer.removeAllViews()
        screenContainer.addView(newView)
    }

    private fun showHomeFragment(screen: Screen, animationDirection: Int) {
        val fragmentManager = supportFragmentManager
        val previousFragment = fragmentManager.findFragmentById(R.id.screen_container)
        val previousView = if (previousFragment == null) {
            screenContainer.getChildAt(0)
        } else {
            null
        }
        val fragment = when (screen) {
            Screen.HOME -> HomeFragment()
            Screen.HOME_SECOND -> HomeSecondFragment()
            else -> error("Not a home screen: $screen")
        }

        if (animationDirection != 0) {
            fragmentManager.beginTransaction()
                .add(R.id.screen_container, fragment)
                .commitNow()
            val incomingView = fragment.view
                ?: error("Home fragment did not create a view")
            val outgoingView = previousFragment?.view ?: previousView
            if (outgoingView != null) {
                animateScreenTransition(incomingView, outgoingView, animationDirection) {
                    if (previousFragment != null) {
                        fragmentManager.beginTransaction()
                            .remove(previousFragment)
                            .commitNow()
                    } else {
                        screenContainer.removeView(outgoingView)
                    }
                }
            }
        } else {
            val transaction = fragmentManager.beginTransaction()
            if (previousFragment != null) {
                transaction.remove(previousFragment)
            } else if (previousView != null) {
                screenContainer.removeView(previousView)
            }
            transaction.add(R.id.screen_container, fragment).commitNow()
        }
    }

    private fun animateScreenTransition(
        incomingView: View,
        outgoingView: View,
        direction: Int,
        onEnd: () -> Unit
    ) {
        val horizontal = abs(direction) == 1
        val offset = if (horizontal) {
            (screenContainer.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
        } else {
            (screenContainer.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels).toFloat()
        }
        val enterOffset = if (direction > 0) offset else -offset
        val exitOffset = -enterOffset

        incomingView.translationX = if (horizontal) enterOffset else 0f
        incomingView.translationY = if (horizontal) 0f else enterOffset
        val easing = PathInterpolator(0.2f, 0f, 0.2f, 1f)
        outgoingView.animate()
            .setDuration(260L)
            .setInterpolator(easing)
            .translationX(if (horizontal) exitOffset else 0f)
            .translationY(if (!horizontal) exitOffset else 0f)
            .withEndAction(onEnd)
            .start()
        incomingView.animate()
            .setDuration(260L)
            .setInterpolator(easing)
            .translationX(0f)
            .translationY(0f)
            .start()
    }

    private fun startHomeSwipe(direction: Int): HomeSwipeTransition? {
        val fromScreen = currentScreen
        val toScreen = when {
            fromScreen == Screen.HOME && direction < 0 -> Screen.APPS
            fromScreen == Screen.HOME_SECOND && direction > 0 -> Screen.APPS
            else -> return null
        }
        val fragmentManager = supportFragmentManager
        val outgoingFragment = fragmentManager.findFragmentById(R.id.screen_container)
        val outgoingView = outgoingFragment?.view
            ?: screenContainer.childCount.takeIf { it > 0 }?.let(screenContainer::getChildAt)
            ?: return null
        val incomingFragment = when (toScreen) {
            Screen.HOME -> HomeFragment()
            Screen.HOME_SECOND -> HomeSecondFragment()
            Screen.APPS -> null
            Screen.SETTINGS -> return null
        }
        val incomingView = if (incomingFragment != null) {
            fragmentManager.beginTransaction()
                .add(R.id.screen_container, incomingFragment)
                .commitNow()
            incomingFragment.view ?: run {
                fragmentManager.beginTransaction().remove(incomingFragment).commitNow()
                return null
            }
        } else {
            LayoutInflater.from(this)
                .inflate(screenLayouts.getValue(toScreen), screenContainer, false)
                .also { view ->
                    bindScreen(view, toScreen)
                    screenContainer.addView(view)
                }
        }
        val width = screenContainer.width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        PageTransitionAnimator.applyProgress(
            this,
            outgoingView,
            incomingView,
            direction,
            getPageTransitionEffect(),
            0f
        )
        return HomeSwipeTransition(
            fromScreen,
            toScreen,
            incomingFragment,
            incomingView,
            outgoingFragment,
            outgoingView,
            width,
            direction
        )
    }

    private fun updateHomeSwipe(transition: HomeSwipeTransition, distanceX: Float) {
        val resistedDistance = if (distanceX * transition.direction < 0f) {
            distanceX * 0.25f
        } else {
            distanceX
        }
        val offset = resistedDistance.coerceIn(-transition.width, transition.width)
        val progress = (offset * transition.direction / transition.width)
            .coerceIn(0f, 1f)
        PageTransitionAnimator.applyProgress(
            this,
            transition.outgoingView,
            transition.incomingView,
            transition.direction,
            getPageTransitionEffect(),
            progress
        )
    }

    private fun settleHomeSwipe(transition: HomeSwipeTransition, commit: Boolean, velocityX: Float) {
        homeSwipeSettling = true
        val progress = (
            transition.outgoingView.translationX * transition.direction / transition.width
            ).coerceIn(0f, 1f)
        val targetProgress = if (commit) 1f else 0f
        val distanceRemaining = abs(targetProgress - progress)
        val velocityDuration = if (abs(velocityX) >= swipeMinVelocity) {
            distanceRemaining * transition.width / abs(velocityX) * 1000f
        } else {
            320f * distanceRemaining
        }
        val duration = velocityDuration.toLong().coerceIn(120L, 360L)

        fun finishTransition() {
            if (commit) {
                currentScreen = transition.toScreen
                if (transition.toScreen == Screen.APPS) {
                    lastHomeScreen = transition.fromScreen
                    installedApps?.let { renderApps?.invoke(it) }
                } else if (transition.toScreen == Screen.HOME ||
                    transition.toScreen == Screen.HOME_SECOND
                ) {
                    lastHomeScreen = transition.toScreen
                }
                removeTransitionScreen(transition.outgoingFragment, transition.outgoingView)
            } else {
                removeTransitionScreen(transition.incomingFragment, transition.incomingView)
            }
            homeSwipeTransition = null
            homeSwipeSettling = false
        }

        PageTransitionAnimator.animateProgress(
            this,
            transition.outgoingView,
            transition.incomingView,
            transition.direction,
            getPageTransitionEffect(),
            progress,
            targetProgress,
            duration,
            ::finishTransition
        )
    }

    private fun removeTransitionScreen(fragment: Fragment?, view: View) {
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        } else {
            screenContainer.removeView(view)
        }
    }

    private fun bindScreen(view: View, screen: Screen) {
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (screen == Screen.APPS) {
            view.findViewById<View>(R.id.main)?.let(LauncherWallpaper::applyTo)
        }

        when (screen) {
            Screen.SETTINGS -> {
                view.findViewById<View>(R.id.open_home).setOnClickListener {
                    showScreen(Screen.HOME, 1)
                }
                view.findViewById<View>(R.id.appearance_setting).setOnClickListener {
                    startActivity(Intent(this, AppearanceActivity::class.java))
                }
                view.findViewById<View>(R.id.general_setting).setOnClickListener {
                    startActivity(Intent(this, GeneralSettingsActivity::class.java))
                }
                view.findViewById<View>(R.id.screen_grid_setting).setOnClickListener {
                    startActivity(Intent(this, ScreenGridActivity::class.java))
                }
                view.findViewById<View>(R.id.page_transition_setting).setOnClickListener {
                    startActivity(Intent(this, PageTransitionActivity::class.java))
                }
                view.findViewById<View>(R.id.wallpaper_setting).setOnClickListener {
                    startActivity(Intent(this, WallpaperActivity::class.java))
                }
                view.findViewById<View>(R.id.app_effects_setting).setOnClickListener {
                    startActivity(Intent(this, AppEffectsActivity::class.java))
                }
                view.findViewById<View>(R.id.change_app_icon_setting).setOnClickListener {
                    startActivity(Intent(this, ChangeAppIconActivity::class.java))
                }
            }
            Screen.HOME, Screen.HOME_SECOND -> Unit
            Screen.APPS -> {
                val pages = view.findViewById<ViewFlipper>(R.id.apps_pages)
                val pageIndicator = view.findViewById<LinearLayout>(R.id.apps_page_indicator)
                val selectionBar = view.findViewById<LinearLayout>(R.id.apps_selection_bar)
                val selectionCount = view.findViewById<TextView>(R.id.apps_selection_count)
                appsPages = pages
                appsIndicator = pageIndicator
                appPageIndex = 0
                val dock = view.findViewById<LinearLayout>(R.id.apps_dock)
                val appLauncher: (AppInfo) -> Unit = { app ->
                    if (app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)) {
                        val folderId = app.packageName.removePrefix(FOLDER_PACKAGE_PREFIX)
                        appFolders.find { it.id == folderId }?.let { folder ->
                            showFolderContents(folder, installedApps.orEmpty())
                        }
                    } else if (isAppSelectionMode) {
                        if (!selectedAppPackages.add(app.packageName)) {
                            selectedAppPackages.remove(app.packageName)
                        }
                        installedApps?.let { renderApps?.invoke(it) }
                    } else {
                        packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                    }
                }
                val appLongPress: (AppInfo, View) -> Unit = { app, anchor ->
                    if (!app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)) {
                        showAppOptions(app, anchor)
                    }
                }

                fun buildHomeApps(apps: List<AppInfo>) {
                    val folderPackages = appFolders.flatMapTo(mutableSetOf()) { it.packageNames }
                    val visibleApps = apps.filterNot {
                        it.packageName in hiddenAppPackages || it.packageName in folderPackages
                    }
                    val dockApps = visibleApps.take(4)
                    val folderTiles = appFolders.map { folder ->
                        val folderApps = apps.filter { it.packageName in folder.packageNames }
                        AppInfo(
                            packageName = FOLDER_PACKAGE_PREFIX + folder.id,
                            name = folder.name,
                            icon = createFolderIcon(folderApps)
                        )
                    }
                    val gridApps = visibleApps.drop(dockApps.size) + folderTiles
                    val gridRows = getSharedPreferences(
                        ScreenGridActivity.PREFERENCES_NAME,
                        MODE_PRIVATE
                    ).getInt(
                        ScreenGridActivity.GRID_ROWS_KEY,
                        ScreenGridActivity.DEFAULT_GRID_ROWS
                    ).coerceIn(
                        ScreenGridActivity.MIN_GRID_ROWS,
                        ScreenGridActivity.MAX_GRID_ROWS
                    )
                    val appPageSize = 4 * gridRows
                    val appChunks = gridApps.chunked(appPageSize).ifEmpty { listOf(emptyList()) }
                    appPageCount = appChunks.size
                    appPageIndex = appPageIndex.coerceIn(0, appPageCount - 1)
                    pages.removeAllViews()
                    pageIndicator.removeAllViews()

                    appChunks.forEachIndexed { pageIndex, pageApps ->
                        val gridView = GridView(this).apply {
                            numColumns = 4
                            horizontalSpacing = dp(6)
                            verticalSpacing = dp(0)
                            stretchMode = GridView.STRETCH_COLUMN_WIDTH
                            isVerticalScrollBarEnabled = false
                            clipToPadding = false
                            adapter = AppAdapter(
                                this@MainActivity,
                                appLauncher,
                                appLongPress,
                                gridRows,
                                selectedAppPackages,
                                { isAppSelectionMode },
                                { !it.packageName.startsWith(FOLDER_PACKAGE_PREFIX) }
                            ).apply {
                                setApps(pageApps)
                            }
                        }
                        pages.addView(gridView)

                        val dot = TextView(this).apply {
                            text = "•"
                            textSize = 18f
                            setPadding(dp(5), 0, dp(5), 0)
                            setOnClickListener { showAppPage(pageIndex) }
                        }
                        pageIndicator.addView(dot)
                    }
                    pages.displayedChild = appPageIndex
                    updateAppPageIndicator()
                    selectionBar.visibility = if (isAppSelectionMode) View.VISIBLE else View.GONE
                    selectionCount.text = "${selectedAppPackages.size} đã chọn"

                    dock.removeAllViews()
                    dockApps.forEach { app ->
                        val icon = ImageView(this).apply {
                            setImageDrawable(AppIconUtils.getIcon(this@MainActivity, app))
                            contentDescription = app.name
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(dp(6), dp(4), dp(6), dp(4))
                        }
                        val iconContainer = FrameLayout(this).apply {
                            addView(
                                icon,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            addView(
                                TextView(this@MainActivity).apply {
                                    val selected = app.packageName in selectedAppPackages
                                    visibility = if (isAppSelectionMode) View.VISIBLE else View.GONE
                                    text = if (selected) "✓" else ""
                                    gravity = Gravity.CENTER
                                    textSize = 13f
                                    setTextColor(0xFFFFFFFF.toInt())
                                    background = android.graphics.drawable.GradientDrawable().apply {
                                        shape = android.graphics.drawable.GradientDrawable.OVAL
                                        setColor(
                                            if (selected) 0xFF1685E8.toInt() else 0x66505050
                                        )
                                        setStroke(dp(1), 0xFFFFFFFF.toInt())
                                    }
                                },
                                FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.START)
                            )
                            setOnClickListener { appLauncher(app) }
                        }
                        dock.addView(
                            iconContainer,
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        )
                    }
                }

                view.findViewById<View>(R.id.apps_selection_cancel).setOnClickListener {
                    exitAppSelectionMode()
                }
                view.findViewById<View>(R.id.apps_selection_folder).setOnClickListener {
                    promptCreateAppFolder()
                }
                renderApps = { apps -> buildHomeApps(apps) }

                val loadedApps = installedApps
                if (loadedApps != null) {
                    buildHomeApps(loadedApps)
                } else if (!isLoadingApps) {
                    isLoadingApps = true
                    appLoadExecutor.execute {
                        val apps = appRepository.loadInstalledApps()
                        runOnUiThread {
                            installedApps = apps
                            isLoadingApps = false
                            if (currentScreen == Screen.APPS) {
                                renderApps?.invoke(apps)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun openApps() {
        showScreen(Screen.APPS, if (lastHomeScreen == Screen.HOME) -1 else 1)
    }

    override fun openSettings() {
        showScreen(Screen.SETTINGS, -1)
    }

    private fun showAppPage(pageIndex: Int) {
        val targetPage = pageIndex.coerceIn(0, appPageCount - 1)
        if (targetPage == appPageIndex) return

        val pages = appsPages ?: return
        val outgoingView = pages.getChildAt(appPageIndex) ?: return
        val incomingView = pages.getChildAt(targetPage) ?: return
        val direction = if (targetPage > appPageIndex) -1 else 1
        pages.displayedChild = targetPage
        outgoingView.visibility = View.VISIBLE
        incomingView.visibility = View.VISIBLE
        homeSwipeSettling = true
        PageTransitionAnimator.animate(
            this,
            outgoingView,
            incomingView,
            direction,
            getPageTransitionEffect(),
            300L
        ) {
            pages.displayedChild = targetPage
            appPageIndex = targetPage
            updateAppPageIndicator()
            homeSwipeSettling = false
        }
    }

    private fun getPageTransitionEffect(): Int =
        getSharedPreferences(PageTransitionAnimator.PREFERENCES_NAME, MODE_PRIVATE)
            .getInt(PageTransitionAnimator.EFFECT_KEY, PageTransitionAnimator.DEFAULT)
            .coerceIn(PageTransitionAnimator.DEFAULT, PageTransitionAnimator.FADE)

    private fun startAppPageSwipe(direction: Int): AppPageSwipeTransition? {
        val pages = appsPages ?: return null
        val targetPage = appPageIndex + if (direction < 0) 1 else -1
        if (targetPage !in 0 until appPageCount) return null

        val outgoingView = pages.getChildAt(appPageIndex) ?: return null
        val incomingView = pages.getChildAt(targetPage) ?: return null
        val width = pages.width.takeIf { it > 0 }?.toFloat()
            ?: screenContainer.width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()

        outgoingView.animate().cancel()
        incomingView.animate().cancel()
        pages.displayedChild = targetPage
        outgoingView.visibility = View.VISIBLE
        incomingView.visibility = View.VISIBLE
        PageTransitionAnimator.applyProgress(
            this,
            outgoingView,
            incomingView,
            direction,
            getPageTransitionEffect(),
            0f
        )

        return AppPageSwipeTransition(
            appPageIndex,
            targetPage,
            outgoingView,
            incomingView,
            width,
            direction
        )
    }

    private fun updateAppPageSwipe(transition: AppPageSwipeTransition, distanceX: Float) {
        val resistedDistance = if (distanceX * transition.direction < 0f) {
            distanceX * 0.25f
        } else {
            distanceX
        }
        val offset = resistedDistance.coerceIn(-transition.width, transition.width)
        transition.progress = (offset * transition.direction / transition.width)
            .coerceIn(0f, 1f)
        PageTransitionAnimator.applyProgress(
            this,
            transition.outgoingView,
            transition.incomingView,
            transition.direction,
            getPageTransitionEffect(),
            transition.progress
        )
    }

    private fun settleAppPageSwipe(
        transition: AppPageSwipeTransition,
        commit: Boolean,
        velocityX: Float
    ) {
        val effect = getPageTransitionEffect()
        homeSwipeSettling = true
        val targetProgress = if (commit) 1f else 0f
        val distanceRemaining = abs(targetProgress - transition.progress)
        val velocityDuration = if (abs(velocityX) >= swipeMinVelocity) {
            distanceRemaining * transition.width / abs(velocityX) * 1000f
        } else {
            320f * distanceRemaining
        }
        val duration = velocityDuration.toLong().coerceIn(120L, 360L)
        PageTransitionAnimator.animateProgress(
            this,
            transition.outgoingView,
            transition.incomingView,
            transition.direction,
            effect,
            transition.progress,
            targetProgress,
            duration
        ) {
            val targetPage = if (commit) transition.toPage else transition.fromPage
            appsPages?.displayedChild = targetPage
            if (commit) appPageIndex = targetPage
            updateAppPageIndicator()
            appPageSwipeTransition = null
            homeSwipeSettling = false
        }
    }

    private fun moveAppPageWithSwipe(direction: Int) {
        val targetPage = appPageIndex + if (direction < 0) 1 else -1
        if (targetPage !in 0 until appPageCount) {
            showScreen(
                if (direction < 0) Screen.HOME_SECOND else Screen.HOME,
                if (direction < 0) 1 else -1
            )
            return
        }

        val pages = appsPages ?: return
        val outgoingView = pages.getChildAt(appPageIndex) ?: return
        val incomingView = pages.getChildAt(targetPage) ?: return
        val width = pages.width.takeIf { it > 0 }?.toFloat()
            ?: screenContainer.width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        val duration = 320L

        pages.displayedChild = targetPage
        outgoingView.visibility = View.VISIBLE
        incomingView.visibility = View.VISIBLE
        homeSwipeSettling = true

        PageTransitionAnimator.animate(
            this,
            outgoingView,
            incomingView,
            direction,
            getPageTransitionEffect(),
            duration
        ) {
            pages.displayedChild = targetPage
            appPageIndex = targetPage
            updateAppPageIndicator()
            homeSwipeSettling = false
        }
    }

    private fun updateAppPageIndicator() {
        val indicator = appsIndicator ?: return
        for (index in 0 until indicator.childCount) {
            val dot = indicator.getChildAt(index) as? TextView ?: continue
            val selected = index == appPageIndex
            dot.alpha = if (selected) 1f else 0.45f
            dot.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF9DB7D0.toInt())
        }
    }

    private fun showAppOptions(app: AppInfo, anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_app_actions, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setBackgroundDrawable(ColorDrawable(0x00000000))
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true
        popupWindow.elevation = 18f

        popupView.findViewById<View>(R.id.app_action_info).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Không thể mở thông tin ứng dụng", Toast.LENGTH_SHORT).show()
            }
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.app_action_select).setOnClickListener {
            isAppSelectionMode = true
            selectedAppPackages.clear()
            selectedAppPackages.add(app.packageName)
            installedApps?.let { renderApps?.invoke(it) }
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.app_action_hide).setOnClickListener {
            hiddenAppPackages.add(app.packageName)
            installedApps?.let { apps ->
                renderApps?.invoke(apps)
            }
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.app_action_edit).setOnClickListener {
            Toast.makeText(this, "Tính năng chỉnh sửa sẽ có trong phiên bản sau", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.app_action_delete).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Không thể xóa ứng dụng", Toast.LENGTH_SHORT).show()
            }
            popupWindow.dismiss()
        }

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val screenWidth = resources.displayMetrics.widthPixels
        val popupWidth = popupView.measuredWidth.takeIf { it > 0 } ?: dp(220)
        val x = (anchorLocation[0] + anchor.width / 2 - popupWidth / 2).coerceIn(0, screenWidth - popupWidth)
        val y = (anchorLocation[1] + anchor.height / 2).coerceAtLeast(0)
        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    private fun exitAppSelectionMode() {
        isAppSelectionMode = false
        selectedAppPackages.clear()
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun promptCreateAppFolder() {
        if (selectedAppPackages.size < 2) {
            Toast.makeText(this, "Chọn ít nhất hai ứng dụng để tạo thư mục.", Toast.LENGTH_SHORT).show()
            return
        }

        val nameInput = EditText(this).apply {
            setText("Thư mục")
            setSelection(text.length)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Tạo thư mục")
            .setView(nameInput)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Tạo") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Nhập tên thư mục.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                createAppFolder(name)
            }
            .show()
    }

    private fun createAppFolder(name: String) {
        val availablePackages = installedApps.orEmpty()
            .mapTo(mutableSetOf()) { it.packageName }
        val members = selectedAppPackages.filter { it in availablePackages }
        if (members.size < 2) {
            Toast.makeText(this, "Không đủ ứng dụng để tạo thư mục.", Toast.LENGTH_SHORT).show()
            return
        }

        appFolders += AppFolder(UUID.randomUUID().toString(), name, members)
        saveAppFolders()
        exitAppSelectionMode()
        Toast.makeText(this, "Đã tạo thư mục $name.", Toast.LENGTH_SHORT).show()
    }

    private fun showFolderContents(folder: AppFolder, apps: List<AppInfo>) {
        val folderApps = apps.filter { it.packageName in folder.packageNames }
        lateinit var popupWindow: PopupWindow
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xAA111018.toInt())
            setOnClickListener { popupWindow.dismiss() }
        }
        val folderCard = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xCC38373F.toInt())
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), 0x447D8EA1)
            }
            isClickable = true
            isFocusable = true
        }
        val grid = GridView(this).apply {
            numColumns = 4
            horizontalSpacing = dp(6)
            verticalSpacing = dp(2)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(16), dp(24), dp(16), dp(12))
            adapter = AppAdapter(
                this@MainActivity,
                { app ->
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                    popupWindow.dismiss()
                },
                gridRows = 3
            ).apply {
                setApps(folderApps)
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        folderCard.addView(grid)

        val title = TextView(this).apply {
            text = folder.name
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 30f
            setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), 0x99000000.toInt())
        }
        val cardSize = dp(300)
        overlay.addView(
            folderCard,
            FrameLayout.LayoutParams(cardSize, cardSize, Gravity.CENTER)
        )
        overlay.addView(
            title,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
                Gravity.CENTER
            ).apply {
                bottomMargin = cardSize + dp(8)
            }
        )

        popupWindow = PopupWindow(
            overlay,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(0x00000000))
            isOutsideTouchable = true
            elevation = dp(16).toFloat()
        }
        popupWindow.showAtLocation(screenContainer, Gravity.CENTER, 0, 0)
    }

    private fun createFolderIcon(apps: List<AppInfo>): Drawable {
        val size = (96 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9C8FF.toInt() }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.2f, size * 0.2f, paint)

        val iconSize = size * 0.34f
        val inset = size * 0.12f
        val positions = listOf(
            inset to inset,
            (size - inset - iconSize) to inset,
            inset to (size - inset - iconSize),
            (size - inset - iconSize) to (size - inset - iconSize)
        )
        apps.take(4).forEachIndexed { index, app ->
            val (left, top) = positions[index]
            val icon = AppIconUtils.getIcon(this, app)
                .constantState
                ?.newDrawable(resources)
                ?.mutate() ?: AppIconUtils.getIcon(this, app).mutate()
            icon.setBounds(left.toInt(), top.toInt(), (left + iconSize).toInt(), (top + iconSize).toInt())
            icon.draw(canvas)
        }
        return BitmapDrawable(resources, bitmap)
    }

    private fun loadAppFolders(): List<AppFolder> {
        val stored = getSharedPreferences(APP_FOLDER_PREFERENCES, MODE_PRIVATE)
            .getString(APP_FOLDERS_KEY, null) ?: return emptyList()
        return try {
            val jsonFolders = JSONArray(stored)
            buildList {
                for (index in 0 until jsonFolders.length()) {
                    val jsonFolder = jsonFolders.getJSONObject(index)
                    val packages = jsonFolder.getJSONArray("packages")
                    val packageNames = buildList {
                        for (packageIndex in 0 until packages.length()) {
                            add(packages.getString(packageIndex))
                        }
                    }
                    if (packageNames.size >= 2) {
                        add(
                            AppFolder(
                                jsonFolder.getString("id"),
                                jsonFolder.getString("name"),
                                packageNames
                            )
                        )
                    }
                }
            }
        } catch (exception: org.json.JSONException) {
            Log.w(TAG, "Could not read saved app folders", exception)
            emptyList()
        }
    }

    private fun saveAppFolders() {
        val jsonFolders = JSONArray()
        appFolders.forEach { folder ->
            jsonFolders.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("packages", JSONArray(folder.packageNames))
            )
        }
        getSharedPreferences(APP_FOLDER_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(APP_FOLDERS_KEY, jsonFolders.toString())
            .apply()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (ignoreTouchSequence) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                ignoreTouchSequence = false
            }
            return true
        }
        if (homeSwipeSettling) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                ignoreTouchSequence = true
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                downX = event.x
                downY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val deltaX = event.x - downX
                val deltaY = event.y - downY
                var transition = homeSwipeTransition
                if (transition == null &&
                    (currentScreen == Screen.HOME ||
                        currentScreen == Screen.HOME_SECOND) &&
                    abs(deltaX) > swipeTouchSlop &&
                    abs(deltaX) > abs(deltaY) * 1.2f
                ) {
                    val direction = if (deltaX < 0f) -1 else 1
                    transition = startHomeSwipe(direction)
                    if (transition != null) {
                        homeSwipeTransition = transition
                        val cancel = MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                if (transition != null) {
                    updateHomeSwipe(transition, deltaX)
                    return true
                }
                var appPageTransition = appPageSwipeTransition
                if (appPageTransition == null &&
                    currentScreen == Screen.APPS &&
                    abs(deltaX) > swipeTouchSlop &&
                    abs(deltaX) > abs(deltaY) * 1.2f
                ) {
                    val direction = if (deltaX < 0f) -1 else 1
                    appPageTransition = startAppPageSwipe(direction)
                    if (appPageTransition != null) {
                        appPageSwipeTransition = appPageTransition
                        val cancel = MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                if (appPageTransition != null) {
                    updateAppPageSwipe(appPageTransition, deltaX)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val velocityY = velocityTracker?.yVelocity ?: 0f
                val distance = event.x - downX
                var transition = homeSwipeTransition

                val appPageTransition = appPageSwipeTransition
                if (appPageTransition != null) {
                    val movedEnough =
                        distance * appPageTransition.direction / appPageTransition.width >= 0.5f
                    val flungFarEnough = abs(velocityX) >= swipeMinVelocity &&
                        velocityX * appPageTransition.direction > 0f
                    updateAppPageSwipe(appPageTransition, distance)
                    settleAppPageSwipe(
                        appPageTransition,
                        movedEnough || flungFarEnough,
                        velocityX
                    )
                    recycleVelocityTracker()
                    return true
                }

                if (transition == null && currentScreen == Screen.APPS) {
                    val width = screenContainer.width.takeIf { it > 0 }
                        ?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
                    val distanceSwipe = abs(distance) >= max(swipeTouchSlop * 2f, width * 0.18f) &&
                        abs(distance) > abs(event.y - downY) * 1.2f
                    val velocitySwipe = abs(velocityX) >= swipeMinVelocity &&
                        abs(velocityX) > abs(velocityY) * 1.2f
                    if (distanceSwipe || velocitySwipe) {
                        val direction = if ((distanceSwipe && distance < 0f) ||
                            (!distanceSwipe && velocityX < 0f)
                        ) -1 else 1
                        val cancel = MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        moveAppPageWithSwipe(direction)
                        recycleVelocityTracker()
                        return true
                    }
                }

                if (transition == null &&
                    (currentScreen == Screen.HOME || currentScreen == Screen.HOME_SECOND)
                ) {
                    val width = screenContainer.width.takeIf { it > 0 }
                        ?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
                    val distanceSwipe = abs(distance) >= max(swipeTouchSlop * 2f, width * 0.18f) &&
                        abs(distance) > abs(event.y - downY) * 1.2f
                    val velocitySwipe = abs(velocityX) >= swipeMinVelocity &&
                        abs(velocityX) > abs(velocityY) * 1.2f
                    if (distanceSwipe || velocitySwipe) {
                        val direction = if ((distanceSwipe && distance < 0f) ||
                            (!distanceSwipe && velocityX < 0f)
                        ) -1 else 1
                        transition = startHomeSwipe(direction)
                        if (transition != null) {
                            homeSwipeTransition = transition
                            val cancel = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                        }
                    }
                }

                if (transition != null) {
                    val movedEnough =
                        distance * transition.direction / transition.width >= 0.5f
                    val flungFarEnough = abs(velocityX) >= swipeMinVelocity &&
                        velocityX * transition.direction > 0f
                    updateHomeSwipe(transition, distance)
                    settleHomeSwipe(transition, movedEnough || flungFarEnough, velocityX)
                    recycleVelocityTracker()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                val appPageTransition = appPageSwipeTransition
                if (appPageTransition != null) {
                    settleAppPageSwipe(appPageTransition, commit = false, velocityX = 0f)
                    recycleVelocityTracker()
                    return true
                }
                val transition = homeSwipeTransition
                if (transition != null) {
                    settleHomeSwipe(transition, commit = false, velocityX = 0f)
                    recycleVelocityTracker()
                    return true
                }
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            recycleVelocityTracker()
        }
        return handled
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDestroy() {
        appLoadExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val APP_FOLDER_PREFERENCES = "launcher_app_folders"
        const val APP_FOLDERS_KEY = "folders"
        const val FOLDER_PACKAGE_PREFIX = "launcher.folder:"
        const val TAG = "MainActivity"
    }
}