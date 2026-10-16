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
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
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
import androidx.core.content.getSystemService
import com.example.oslaucher.R
import com.example.oslaucher.adapters.AppAdapter
import com.example.oslaucher.adapters.AlphabeticalAppAdapter
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
    private data class FolderDragState(
        val folder: AppFolder,
        val draggedApp: AppInfo,
        var apps: List<AppInfo>,
        val grid: GridView,
        val adapter: AppAdapter,
        val root: FrameLayout,
        val shadow: ImageView,
        val label: TextView,
        val downX: Float,
        val downY: Float,
        var moved: Boolean = false
    )

    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var screenContainer: FrameLayout
    private var installedApps: List<AppInfo>? = null
    private val installedAppsCallbacks = mutableListOf<(List<AppInfo>) -> Unit>()
    private var renderApps: ((List<AppInfo>) -> Unit)? = null
    private var appsPages: ViewFlipper? = null
    private var appsIndicator: LinearLayout? = null
    private var appPageIndex = 0
    private var appPageCount = 1
    private var appGridPageSize = 1
    private var appGridPackages: List<String> = emptyList()
    private val appGridOrderPackages = mutableListOf<String>()
    private var appPageSwipeTransition: AppPageSwipeTransition? = null
    private var isLoadingApps = false
    private var isAppSelectionMode = false
    private val selectedAppPackages = mutableSetOf<String>()
    private val appFolders = mutableListOf<AppFolder>()
    private var draggedApp: AppInfo? = null
    private var folderDragState: FolderDragState? = null
    private var dragShadow: ImageView? = null
    private var dragTargetLabel: TextView? = null
    private var dragEdge = 0
    private var dragMoved = false
    private var dragStartedInAppGrid = false
    private var downX = 0f
    private var downY = 0f
    private var homeSwipeTransition: HomeSwipeTransition? = null
    private var homeSwipeSettling = false
    private var ignoreTouchSequence = false
    private var velocityTracker: VelocityTracker? = null
    private val hiddenAppPackages = mutableSetOf<String>()

    private val swipeTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val swipeMinVelocity by lazy { ViewConfiguration.get(this).scaledMinimumFlingVelocity }

    private val screenLayouts = mapOf(
        Screen.SETTINGS to R.layout.activity_main,
        Screen.APPS to R.layout.screen_apps
    )

    private var currentScreen = Screen.SETTINGS
    private var lastHomeScreen = Screen.HOME
    private var isAlphabeticalLibrary = false

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
        appGridOrderPackages.addAll(loadAppGridOrder())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAppSelectionMode) {
                    exitAppSelectionMode()
                    return
                }
                when (currentScreen) {
                    Screen.APPS -> {
                        isAlphabeticalLibrary = false
                        showScreen(
                            lastHomeScreen,
                            if (lastHomeScreen == Screen.HOME) 1 else -1
                        )
                    }
                    Screen.HOME_SECOND -> showScreen(Screen.HOME, -1)
                    Screen.HOME -> showScreen(Screen.SETTINGS, -1)
                    Screen.SETTINGS -> finish()
                }
            }
        })

        showScreen(Screen.SETTINGS, 0)
    }

    private fun bindAlphabeticalAppLibrary(view: View) {
        val appList = view.findViewById<ListView>(R.id.app_library_list)
        val search = view.findViewById<EditText>(R.id.app_library_search)
        val adapter = AlphabeticalAppAdapter(
            this,
            { app -> launchHomeScreenApp(app.packageName) },
            { app, anchor -> showAppOptions(app, anchor) }
        )
        appList.adapter = adapter

        val alphabet = view.findViewById<LinearLayout>(R.id.app_library_alphabet)
        ('A'..'Z').forEach { letter ->
            alphabet.addView(TextView(this).apply {
                text = letter.toString()
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 11f
                contentDescription = "Chữ $letter"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                setOnClickListener {
                    adapter.positionFor(letter).takeIf { it >= 0 }?.let(appList::smoothScrollToPosition)
                }
            })
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(text: Editable?) {
                adapter.filter(text?.toString().orEmpty())
            }
        })

        view.findViewById<View>(R.id.app_library_cancel).setOnClickListener {
            isAlphabeticalLibrary = false
            getSystemService<InputMethodManager>()
                ?.hideSoftInputFromWindow(search.windowToken, 0)
            showScreen(
                lastHomeScreen,
                if (lastHomeScreen == Screen.HOME) 1 else -1
            )
        }

        requestInstalledApps { apps ->
            if (currentScreen == Screen.APPS && isAlphabeticalLibrary) {
                adapter.setApps(apps)
            }
        }
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
        val layoutResId = if (screen == Screen.APPS && isAlphabeticalLibrary) {
            R.layout.screen_app_library
        } else {
            screenLayouts.getValue(screen)
        }
        val newView = LayoutInflater.from(this).inflate(layoutResId, screenContainer, false)
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
        isAlphabeticalLibrary = false
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
            val view = LayoutInflater.from(this)
                .inflate(screenLayouts.getValue(toScreen), screenContainer, false)
                .also { bindScreen(it, toScreen) }
            screenContainer.addView(view)
            view
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
                if (isAlphabeticalLibrary) {
                    bindAlphabeticalAppLibrary(view)
                    return
                }
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
                    startAppDrag(app, anchor)
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
                    val gridApps = orderAppGridApps(
                        visibleApps.drop(dockApps.size) + folderTiles
                    )
                    appGridPackages = gridApps.map { it.packageName }
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
                    appGridPageSize = appPageSize
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
                            tag = "$APP_GRID_TAG:$pageIndex"
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
                } else {
                    requestInstalledApps { apps ->
                        if (currentScreen == Screen.APPS) {
                            renderApps?.invoke(apps)
                        }
                    }
                }
            }
        }
    }

    private fun requestInstalledApps(onLoaded: (List<AppInfo>) -> Unit) {
        installedApps?.let {
            onLoaded(it)
            return
        }

        installedAppsCallbacks += onLoaded
        if (isLoadingApps) return

        isLoadingApps = true
        appLoadExecutor.execute {
            val apps = appRepository.loadInstalledApps()
            runOnUiThread {
                installedApps = apps
                isLoadingApps = false
                val callbacks = installedAppsCallbacks.toList()
                installedAppsCallbacks.clear()
                callbacks.forEach { callback -> callback(apps) }
            }
        }
    }

    override fun loadHomeScreenApps(onLoaded: (List<AppInfo>) -> Unit) {
        requestInstalledApps(onLoaded)
    }

    override fun launchHomeScreenApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "Không thể mở ứng dụng", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(launchIntent)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Unable to launch app $packageName", exception)
            Toast.makeText(this, "Không thể mở ứng dụng", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openHomeScreenFolder(name: String, apps: List<AppInfo>) {
        showFolderContents(
            AppFolder("home:$name", name, apps.map { it.packageName }),
            apps
        )
    }

    override fun openApps() {
        if (currentScreen != Screen.APPS && currentScreen != Screen.SETTINGS) {
            lastHomeScreen = currentScreen
        }
        isAlphabeticalLibrary = true
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
        appPageIndex = targetPage
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

    private fun startAppDrag(app: AppInfo, source: View) {
        dragStartedInAppGrid = true
        draggedApp = app
        dragEdge = 0
        dragMoved = false
        dragShadow = ImageView(this).apply {
            setImageDrawable(AppIconUtils.getIcon(this@MainActivity, app))
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.9f
            elevation = dp(12).toFloat()
        }
        screenContainer.addView(
            dragShadow,
            FrameLayout.LayoutParams(dp(64), dp(64))
        )
        dragTargetLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(0xE6212733.toInt())
                setStroke(dp(1), 0xFF7DB8FF.toInt())
            }
            elevation = dp(16).toFloat()
            visibility = View.GONE
        }.also { label ->
            screenContainer.addView(
                label,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply {
                    topMargin = dp(56)
                }
            )
        }
        source.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        updateDragTargetLabel()
        updateDragShadow(downX, downY)
    }

    private fun updateDragShadow(x: Float, y: Float) {
        val shadow = dragShadow ?: return
        val layoutParams = shadow.layoutParams as? FrameLayout.LayoutParams ?: return
        layoutParams.leftMargin = (x - dp(32)).toInt().coerceAtLeast(0)
        layoutParams.topMargin = (y - dp(32)).toInt().coerceAtLeast(0)
        shadow.layoutParams = layoutParams
    }

    private fun navigateAppGridDuringDrag(x: Float) {
        if (!dragStartedInAppGrid || currentScreen != Screen.APPS || isAlphabeticalLibrary) return
        val width = screenContainer.width.toFloat()
        if (width <= 0f) return
        val edgeWidth = dp(48).toFloat()
        val edge = when {
            x < edgeWidth -> -1
            x > width - edgeWidth -> 1
            else -> 0
        }
        if (edge == 0) {
            dragEdge = 0
            return
        }
        if (edge == dragEdge) return
        dragEdge = edge

        val targetPage = appPageIndex + if (edge < 0) 1 else -1
        if (targetPage in 0 until appPageCount) {
            showAppPage(targetPage)
            appPageIndex = targetPage
            updateAppPageIndicator()
        }
        updateDragTargetLabel()
        dragShadow?.let(screenContainer::bringChildToFront)
        dragTargetLabel?.let(screenContainer::bringChildToFront)
    }

    private fun updateDragTargetLabel() {
        val label = dragTargetLabel ?: return
        if (dragStartedInAppGrid && currentScreen == Screen.APPS && !isAlphabeticalLibrary) {
            label.text = "Thả vào lưới ứng dụng ${appPageIndex + 1}"
            label.visibility = View.VISIBLE
        } else {
            label.visibility = View.GONE
        }
    }

    private fun dropDraggedApp(x: Float, y: Float) {
        val app = draggedApp ?: return
        val grid = appsPages
            ?.getChildAt(appPageIndex)
            ?.findViewWithTag<View>("$APP_GRID_TAG:$appPageIndex") as? GridView
        if (currentScreen != Screen.APPS || isAlphabeticalLibrary || grid == null) {
            Toast.makeText(this, "Chỉ có thể thả ứng dụng trong lưới ứng dụng.", Toast.LENGTH_SHORT).show()
            return
        }
        val gridLocation = IntArray(2)
        grid.getLocationOnScreen(gridLocation)
        val insideGrid = x >= gridLocation[0] &&
            x <= gridLocation[0] + grid.width &&
            y >= gridLocation[1] &&
            y <= gridLocation[1] + grid.height
        if (!insideGrid) {
            Toast.makeText(this, "Thả ứng dụng vào lưới ứng dụng.", Toast.LENGTH_SHORT).show()
            return
        }
        val position = grid.pointToPosition(
            (x - gridLocation[0]).toInt(),
            (y - gridLocation[1]).toInt()
        )
        val dropAfterTarget = if (position == GridView.INVALID_POSITION) {
            false
        } else {
            val targetView = grid.getChildAt(position - grid.firstVisiblePosition)
            if (targetView == null) {
                false
            } else {
                val targetLocation = IntArray(2)
                targetView.getLocationOnScreen(targetLocation)
                x >= targetLocation[0] + targetView.width / 2f
            }
        }
        reorderAppGridItem(app, appPageIndex, position, dropAfterTarget)
    }

    private fun finishAppDrag() {
        dragShadow?.let(screenContainer::removeView)
        dragTargetLabel?.let(screenContainer::removeView)
        dragShadow = null
        dragTargetLabel = null
        draggedApp = null
        dragStartedInAppGrid = false
        dragEdge = 0
        dragMoved = false
    }

    private fun orderAppGridApps(apps: List<AppInfo>): List<AppInfo> {
        val savedPositions = appGridOrderPackages.withIndex()
            .associate { it.value to it.index }
        return apps.sortedBy { savedPositions[it.packageName] ?: Int.MAX_VALUE }
    }

    private fun reorderAppGridItem(
        app: AppInfo,
        pageIndex: Int,
        pagePosition: Int,
        dropAfterTarget: Boolean
    ) {
        val sourceIndex = appGridPackages.indexOf(app.packageName)
        if (sourceIndex < 0) return

        val pageStart = pageIndex * appGridPageSize
        val pageEnd = minOf(pageStart + appGridPageSize, appGridPackages.size)
        val targetIndex = if (pagePosition == GridView.INVALID_POSITION) {
            pageEnd
        } else {
            (pageStart + pagePosition + if (dropAfterTarget) 1 else 0)
                .coerceIn(pageStart, pageEnd)
        }
        val reorderedPackages = appGridPackages.toMutableList().apply {
            removeAt(sourceIndex)
        }
        var insertionIndex = targetIndex
        if (sourceIndex < insertionIndex) insertionIndex--
        val adjustedPageStart = pageIndex * appGridPageSize
        val adjustedPageEnd = minOf(
            adjustedPageStart + appGridPageSize,
            reorderedPackages.size
        )
        insertionIndex = insertionIndex.coerceIn(adjustedPageStart, adjustedPageEnd)
        insertionIndex = insertionIndex.coerceIn(0, reorderedPackages.size)
        reorderedPackages.add(insertionIndex, app.packageName)

        appGridPackages = reorderedPackages
        val reorderedPackageSet = reorderedPackages.toSet()
        appGridOrderPackages.removeAll(reorderedPackageSet)
        appGridOrderPackages.addAll(0, reorderedPackages)
        saveAppGridOrder()
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun loadAppGridOrder(): List<String> {
        val stored = getSharedPreferences(APP_GRID_ORDER_PREFERENCES, MODE_PRIVATE)
            .getString(APP_GRID_ORDER_KEY, null) ?: return emptyList()
        return try {
            val packages = JSONArray(stored)
            buildList {
                for (index in 0 until packages.length()) {
                    packages.optString(index)
                        .takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }.distinct()
        } catch (exception: org.json.JSONException) {
            Log.w(TAG, "Could not read saved app grid order", exception)
            emptyList()
        }
    }

    private fun saveAppGridOrder() {
        getSharedPreferences(APP_GRID_ORDER_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(APP_GRID_ORDER_KEY, JSONArray(appGridOrderPackages).toString())
            .apply()
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
        val folderApps = orderFolderApps(folder, apps)
        lateinit var popupWindow: PopupWindow
        var touchDownX = 0f
        var touchDownY = 0f
        val overlay = object : FrameLayout(this) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (folderDragState != null) {
                    return handleFolderDragTouch(event)
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
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
        lateinit var folderAdapter: AppAdapter
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
                { app, anchor ->
                    val currentFolder = appFolders.firstOrNull { it.id == folder.id } ?: folder
                    val currentApps = (0 until folderAdapter.count).map(folderAdapter::getItem)
                    startFolderAppDrag(
                        currentFolder,
                        app,
                        currentApps,
                        this,
                        folderAdapter,
                        overlay,
                        anchor,
                        touchDownX,
                        touchDownY
                    )
                },
                gridRows = 3
            ).apply {
                setApps(folderApps)
            }.also { folderAdapter = it }
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

    private fun orderFolderApps(folder: AppFolder, apps: List<AppInfo>): List<AppInfo> {
        val appsByPackage = apps.associateBy { it.packageName }
        return folder.packageNames.mapNotNull(appsByPackage::get)
    }

    private fun startFolderAppDrag(
        folder: AppFolder,
        app: AppInfo,
        apps: List<AppInfo>,
        grid: GridView,
        adapter: AppAdapter,
        root: FrameLayout,
        source: View,
        downX: Float,
        downY: Float
    ) {
        val shadow = ImageView(this).apply {
            setImageDrawable(AppIconUtils.getIcon(this@MainActivity, app))
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.9f
            elevation = dp(12).toFloat()
            isClickable = false
        }
        val label = TextView(this).apply {
            text = "Thả để sắp xếp ứng dụng"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(0xE6212733.toInt())
                setStroke(dp(1), 0xFF7DB8FF.toInt())
            }
            elevation = dp(16).toFloat()
            isClickable = false
        }
        root.addView(shadow, FrameLayout.LayoutParams(dp(64), dp(64)))
        root.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(56)
            }
        )
        folderDragState = FolderDragState(
            folder = folder,
            draggedApp = app,
            apps = apps,
            grid = grid,
            adapter = adapter,
            root = root,
            shadow = shadow,
            label = label,
            downX = downX,
            downY = downY
        )
        source.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        updateFolderDragShadow(downX, downY)
        shadow.bringToFront()
        label.bringToFront()
    }

    private fun handleFolderDragTouch(event: MotionEvent): Boolean {
        val state = folderDragState ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - state.downX) > swipeTouchSlop ||
                    abs(event.rawY - state.downY) > swipeTouchSlop
                ) {
                    state.moved = true
                }
                updateFolderDragShadow(event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP -> {
                if (state.moved) {
                    dropFolderApp(state, event.rawX, event.rawY)
                }
                finishFolderAppDrag()
            }
            MotionEvent.ACTION_CANCEL -> finishFolderAppDrag()
        }
        return true
    }

    private fun updateFolderDragShadow(x: Float, y: Float) {
        val state = folderDragState ?: return
        val rootLocation = IntArray(2)
        state.root.getLocationOnScreen(rootLocation)
        val layoutParams = state.shadow.layoutParams as? FrameLayout.LayoutParams ?: return
        layoutParams.leftMargin = (x - rootLocation[0] - dp(32)).toInt().coerceAtLeast(0)
        layoutParams.topMargin = (y - rootLocation[1] - dp(32)).toInt().coerceAtLeast(0)
        state.shadow.layoutParams = layoutParams
    }

    private fun dropFolderApp(state: FolderDragState, x: Float, y: Float) {
        val gridLocation = IntArray(2)
        state.grid.getLocationOnScreen(gridLocation)
        val insideGrid = x >= gridLocation[0] &&
            x <= gridLocation[0] + state.grid.width &&
            y >= gridLocation[1] &&
            y <= gridLocation[1] + state.grid.height
        if (!insideGrid) return

        val position = state.grid.pointToPosition(
            (x - gridLocation[0]).toInt(),
            (y - gridLocation[1]).toInt()
        )
        val targetView = if (position == GridView.INVALID_POSITION) {
            null
        } else {
            state.grid.getChildAt(position - state.grid.firstVisiblePosition)
        }
        val dropAfterTarget = targetView?.let {
            val targetLocation = IntArray(2)
            it.getLocationOnScreen(targetLocation)
            x >= targetLocation[0] + it.width / 2f
        } ?: false

        val sourceIndex = state.apps.indexOfFirst {
            it.packageName == state.draggedApp.packageName
        }
        if (sourceIndex < 0) return

        val targetIndex = if (position == GridView.INVALID_POSITION) {
            state.apps.size
        } else {
            (position + if (dropAfterTarget) 1 else 0).coerceIn(0, state.apps.size)
        }
        val reorderedApps = state.apps.toMutableList().apply {
            removeAt(sourceIndex)
        }
        var insertionIndex = targetIndex
        if (sourceIndex < insertionIndex) insertionIndex--
        reorderedApps.add(insertionIndex.coerceIn(0, reorderedApps.size), state.draggedApp)

        val visiblePackages = state.apps.mapTo(mutableSetOf()) { it.packageName }
        val unavailablePackages = state.folder.packageNames.filterNot { it in visiblePackages }
        val updatedFolder = state.folder.copy(
            packageNames = reorderedApps.map { it.packageName } + unavailablePackages
        )
        val folderIndex = appFolders.indexOfFirst { it.id == updatedFolder.id }
        if (folderIndex >= 0) {
            appFolders[folderIndex] = updatedFolder
            saveAppFolders()
        }
        state.apps = reorderedApps
        state.adapter.setApps(reorderedApps)
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun finishFolderAppDrag() {
        val state = folderDragState ?: return
        folderDragState = null
        state.root.removeView(state.shadow)
        state.root.removeView(state.label)
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
        if (draggedApp != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    updateDragShadow(event.x, event.y)
                    updateDragTargetLabel()
                    if (abs(event.x - downX) > swipeTouchSlop ||
                        abs(event.y - downY) > swipeTouchSlop
                    ) {
                        dragMoved = true
                    }
                    navigateAppGridDuringDrag(event.x)
                }
                MotionEvent.ACTION_UP -> {
                    if (dragMoved) {
                        dropDraggedApp(event.rawX, event.rawY)
                    }
                    finishAppDrag()
                    recycleVelocityTracker()
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishAppDrag()
                    recycleVelocityTracker()
                }
            }
            return true
        }
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
                    (currentScreen == Screen.HOME ||
                        currentScreen == Screen.HOME_SECOND)
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
        const val APP_GRID_ORDER_PREFERENCES = "launcher_app_grid_order"
        const val APP_GRID_ORDER_KEY = "packages"
        const val APP_GRID_TAG = "app_grid_page"
        const val FOLDER_PACKAGE_PREFIX = "launcher.folder:"
        const val TAG = "MainActivity"
    }
}