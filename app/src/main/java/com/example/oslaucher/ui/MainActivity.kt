package com.example.oslaucher.ui

import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
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
import com.example.oslaucher.models.AppDragCandidate
import com.example.oslaucher.models.AppGridOrder
import com.example.oslaucher.models.AppFolder
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.models.AppPageSwipeTransition
import com.example.oslaucher.models.FolderDragState
import com.example.oslaucher.models.HomeSwipeTransition
import com.example.oslaucher.repositories.InstalledAppRepository
import com.example.oslaucher.repositories.LauncherLayoutRepository
import com.example.oslaucher.ui.fragments.HomeFragment
import com.example.oslaucher.ui.fragments.HomeScreenActions
import com.example.oslaucher.ui.fragments.HomeSecondFragment
import com.example.oslaucher.utils.AppIconUtils
import com.example.oslaucher.utils.LauncherWallpaper
import com.example.oslaucher.utils.Screen
import java.util.concurrent.Executors
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity(), HomeScreenActions {
    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val layoutRepository by lazy { LauncherLayoutRepository(applicationContext) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var screenContainer: FrameLayout
    private var installedApps: List<AppInfo>? = null
    private val installedAppsCallbacks = mutableListOf<(List<AppInfo>) -> Unit>()
    private var renderApps: ((List<AppInfo>) -> Unit)? = null
    private var appsPages: ViewFlipper? = null
    private var appsIndicator: LinearLayout? = null
    private var appsGridRendered = false
    private var appPageIndex = 0
    private var appPageCount = 1
    private var appGridPageSize = 1
    private var appGridPackages: List<String?> = emptyList()
    private var openAppsAtLastPage = false
    private val appGridOrderPackages = mutableListOf<String?>()
    private var dragCreatedEmptyAppPage = false
    private var appPageSwipeTransition: AppPageSwipeTransition? = null
    private var isLoadingApps = false
    private var isAppSelectionMode = false
    private val selectedAppPackages = mutableSetOf<String>()
    private val appFolders = mutableListOf<AppFolder>()
    private val dockFolderSlots = mutableMapOf<String, Int>()
    private val dockAppSlots = mutableMapOf<String, Int>()
    private val dockEmptySlots = mutableSetOf<Int>()
    private val dockRemovedAppPackages = mutableSetOf<String>()
    private var appsDock: LinearLayout? = null
    private var draggedApp: AppInfo? = null
    private var dragSource: View? = null
    private var dragSourceVisibility = View.VISIBLE
    private var appDragCandidate: AppDragCandidate? = null
    private var appOptionsPopup: PopupWindow? = null
    private var folderDragState: FolderDragState? = null
    private var dragShadow: ImageView? = null
    private var dragCheckBadge: TextView? = null
    private val dragWiggleAnimators = mutableListOf<ObjectAnimator>()
    private var dragTargetLabel: TextView? = null
    private var dragEdge = 0
    private var dragPageChangeRunnable: Runnable? = null
    private val dragPageHandler = Handler(Looper.getMainLooper())
    private var dragMoved = false
    private var dragStartedInAppGrid = false
    private var dragStartedInDock = false
    private var draggedDockSlot: Int? = null
    private var dragLastRawX = 0f
    private var dragLastRawY = 0f
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
        Screen.LOADING to R.layout.screen_loading,
        Screen.SETTINGS to R.layout.activity_main,
        Screen.APPS to R.layout.screen_apps
    )

    private var currentScreen = Screen.LOADING
    private var lastHomeScreen = Screen.HOME
    private var isAlphabeticalLibrary = false
    private var preloadedSettingsView: View? = null
    private var launchedAsHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchedAsHome = intent?.categories?.contains(Intent.CATEGORY_HOME) == true

        screenContainer = FrameLayout(this).apply {
            id = R.id.screen_container
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(screenContainer)
        appFolders.addAll(loadAppFolders())
        dockFolderSlots.putAll(loadDockFolderSlots())
        dockAppSlots.putAll(loadDockAppSlots())
        dockEmptySlots.addAll(loadDockEmptySlots())
        dockRemovedAppPackages.addAll(loadDockRemovedAppPackages())
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
                    Screen.HOME -> {
                        if (launchedAsHome) finish() else showScreen(Screen.SETTINGS, -1)
                    }
                    Screen.LOADING, Screen.SETTINGS -> finish()
                }
            }
        })

        showScreen(Screen.LOADING, 0)
        screenContainer.post {
            if (!launchedAsHome && !isFinishing && !isDestroyed &&
                currentScreen == Screen.LOADING
            ) {
                preloadedSettingsView = LayoutInflater.from(this)
                    .inflate(screenLayouts.getValue(Screen.SETTINGS), screenContainer, false)
            }
        }
        requestInstalledApps {
            if (!isFinishing && !isDestroyed && currentScreen == Screen.LOADING) {
                showScreen(if (launchedAsHome) Screen.HOME else Screen.SETTINGS, 1)
            }
        }
    }

    private fun requestDefaultHomeRole() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addCategory(Intent.CATEGORY_DEFAULT)
            startActivity(Intent.createChooser(homeIntent, null))
        } catch (exception: ActivityNotFoundException) {
            Log.w(TAG, "Unable to show home app chooser", exception)
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (fallbackException: ActivityNotFoundException) {
                Log.e(TAG, "Unable to open default home settings", fallbackException)
                Toast.makeText(
                    this,
                    R.string.home_settings_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
                contentDescription = getString(R.string.letter_accessibility, letter)
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
        val newView = if (screen == Screen.SETTINGS) {
            preloadedSettingsView.also { preloadedSettingsView = null }
                ?: LayoutInflater.from(this).inflate(layoutResId, screenContainer, false)
        } else {
            LayoutInflater.from(this).inflate(layoutResId, screenContainer, false)
        }
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
            fromScreen == Screen.HOME_SECOND && direction < 0 -> Screen.APPS
            fromScreen == Screen.APPS && !isAlphabeticalLibrary &&
                direction < 0 && appPageIndex == appPageCount - 1 -> Screen.HOME_SECOND
            fromScreen == Screen.APPS && !isAlphabeticalLibrary &&
                direction > 0 && appPageIndex == 0 -> Screen.HOME
            else -> return null
        }
        if (toScreen == Screen.APPS) isAlphabeticalLibrary = false
        val fragmentManager = supportFragmentManager
        val outgoingFragment = fragmentManager.findFragmentById(R.id.screen_container)
        val outgoingView = outgoingFragment?.view
            ?: screenContainer.childCount.takeIf { it > 0 }?.let(screenContainer::getChildAt)
            ?: return null
        val incomingFragment = when (toScreen) {
            Screen.HOME -> HomeFragment()
            Screen.HOME_SECOND -> HomeSecondFragment()
            Screen.APPS -> null
            Screen.LOADING, Screen.SETTINGS -> return null
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
            openAppsAtLastPage =
                fromScreen == Screen.HOME_SECOND && direction < 0
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
                    if (!appsGridRendered) {
                        openAppsAtLastPage = transition.fromScreen == Screen.HOME_SECOND
                        installedApps?.let { renderApps?.invoke(it) }
                    }
                } else if (transition.toScreen == Screen.HOME ||
                    transition.toScreen == Screen.HOME_SECOND
                ) {
                    lastHomeScreen = transition.toScreen
                }
                removeTransitionScreen(transition.outgoingFragment, transition.outgoingView)
                if (transition.toScreen == Screen.APPS &&
                    transition.fromScreen == Screen.HOME_SECOND
                ) {
                    animateAppPageEntrance(appPageIndex, transition.direction)
                }
            } else {
                openAppsAtLastPage = false
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
            Screen.LOADING -> Unit
            Screen.SETTINGS -> {
                view.findViewById<View>(R.id.open_home).setOnClickListener {
                    showScreen(Screen.HOME, 1)
                }
                view.findViewById<View>(R.id.default_home_screen_setting).setOnClickListener {
                    requestDefaultHomeRole()
                }
                view.findViewById<View>(R.id.default_home_screen_label).setOnClickListener {
                    requestDefaultHomeRole()
                }
                view.findViewById<View>(R.id.appearance_setting).setOnClickListener {
                    startActivity(Intent(this, AppearanceActivity::class.java))
                }
                view.findViewById<View>(R.id.general_setting).setOnClickListener {
                    startActivity(Intent(this, GeneralSettingsActivity::class.java))
                }
                view.findViewById<View>(R.id.language_setting).setOnClickListener {
                    startActivity(Intent(this, LanguageActivity::class.java))
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
                appsGridRendered = false
                appsPages = pages
                appsIndicator = pageIndicator
                if (!openAppsAtLastPage) appPageIndex = 0
                val dock = view.findViewById<LinearLayout>(R.id.apps_dock)
                appsDock = dock
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
                    if (isAppSelectionMode || app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)) {
                        startAppDrag(app, anchor)
                    } else {
                        appDragCandidate = AppDragCandidate(app, anchor)
                        showAppOptions(app, anchor)
                    }
                }

                fun buildHomeApps(apps: List<AppInfo>) {
                    if (isAppSelectionMode) stopDragWiggle()
                    (pages.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                        params.topMargin = dp(if (isAppSelectionMode) 8 else 22)
                        pages.layoutParams = params
                    }
                    val folderPackages = appFolders.flatMapTo(mutableSetOf()) { it.packageNames }
                    val visibleApps = apps.filterNot {
                        it.packageName in hiddenAppPackages || it.packageName in folderPackages
                    }
                    val folderTiles = appFolders.map { folder ->
                        val folderApps = apps.filter { it.packageName in folder.packageNames }
                        AppInfo(
                            packageName = FOLDER_PACKAGE_PREFIX + folder.id,
                            name = folder.name,
                            icon = createFolderIcon(folderApps)
                        )
                    }
                    val dockFolderTiles = folderTiles.filter {
                        it.packageName.removePrefix(FOLDER_PACKAGE_PREFIX) in dockFolderSlots
                    }.take(4)
                    val dockFolderPackages = dockFolderTiles.mapTo(mutableSetOf()) {
                        it.packageName
                    }
                    val gridFolderTiles = folderTiles.filterNot {
                        it.packageName in dockFolderPackages
                    }
                    val dockSlots = MutableList<AppInfo?>(4) { null }
                    dockFolderTiles.forEach { folder ->
                        val folderId = folder.packageName.removePrefix(FOLDER_PACKAGE_PREFIX)
                        val requestedSlot = dockFolderSlots[folderId]?.coerceIn(0, 3) ?: 3
                        val slot = if (dockSlots[requestedSlot] == null) {
                            requestedSlot
                        } else {
                            dockSlots.indexOfFirst { it == null }
                        }
                        if (slot >= 0) dockSlots[slot] = folder
                    }
                    if (dockAppSlots.isEmpty() && dockEmptySlots.isEmpty()) {
                        visibleApps.forEachIndexed { appIndex, app ->
                            if (app.packageName in dockRemovedAppPackages && appIndex < 4) {
                                dockEmptySlots += appIndex
                            }
                        }
                    }
                    dockAppSlots.toMap().forEach { (packageName, slot) ->
                        val app = visibleApps.firstOrNull { it.packageName == packageName }
                        if (app != null && slot in dockSlots.indices &&
                            dockSlots[slot] == null && slot !in dockEmptySlots
                        ) {
                            dockSlots[slot] = app
                        }
                    }
                    val placedDockPackages = dockSlots.filterNotNull()
                        .mapTo(mutableSetOf()) { it.packageName }
                    visibleApps.filterNot {
                        it.packageName in dockRemovedAppPackages ||
                            it.packageName in placedDockPackages ||
                            it.packageName in dockAppSlots
                    }.forEach { app ->
                        val slot = dockSlots.indices.firstOrNull {
                            dockSlots[it] == null && it !in dockEmptySlots
                        } ?: return@forEach
                        dockSlots[slot] = app
                        dockAppSlots[app.packageName] = slot
                    }
                    val dockPackages = dockSlots.filterNotNull()
                        .mapTo(mutableSetOf()) { it.packageName }
                    saveDockAppSlots()
                    saveDockEmptySlots()
                    val gridApps = orderAppGridApps(
                        visibleApps.filterNot { it.packageName in dockPackages } + gridFolderTiles
                    )
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
                    val appChunks = gridApps.chunked(appPageSize)
                        .filterIndexed { pageIndex, pageApps ->
                            pageApps.any { it != null } ||
                                (dragCreatedEmptyAppPage && pageIndex == gridApps.lastIndex / appPageSize)
                        }
                    appGridPackages = appChunks.flatten().map { it?.packageName }
                    if (appGridOrderPackages != appGridPackages) {
                        appGridOrderPackages.clear()
                        appGridOrderPackages.addAll(appGridPackages)
                        saveAppGridOrder()
                    }
                    appPageCount = appChunks.size
                    val showLastAppPage = openAppsAtLastPage
                    openAppsAtLastPage = false
                    appPageIndex = if (appPageCount == 0) {
                        0
                    } else if (showLastAppPage) {
                        appPageCount - 1
                    } else {
                        appPageIndex.coerceIn(0, appPageCount - 1)
                    }
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
                                { !it.packageName.startsWith(FOLDER_PACKAGE_PREFIX) },
                                onEmptySlotClick = {
                                    if (isAppSelectionMode) exitAppSelectionMode()
                                },
                                selectionBarHeight = selectionBar.layoutParams.height
                            ).apply {
                                setGridApps(
                                    pageApps + List(appPageSize - pageApps.size) { null }
                                )
                            }
                        }
                        gridView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            if (isAppSelectionMode) refreshSelectionWiggle()
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
                    selectionCount.text = getString(
                        R.string.selected_apps,
                        selectedAppPackages.size
                    )

                    dock.removeAllViews()
                    dockSlots.forEach { app ->
                        if (app == null) {
                            dock.addView(
                                FrameLayout(this).apply {
                                    isClickable = isAppSelectionMode
                                    setOnClickListener {
                                        if (isAppSelectionMode) exitAppSelectionMode()
                                    }
                                },
                                LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f
                                )
                            )
                            return@forEach
                        }
                        val icon = ImageView(this).apply {
                            setImageDrawable(AppIconUtils.getIcon(this@MainActivity, app))
                            contentDescription = app.name
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(dp(6), dp(4), dp(6), dp(4))
                        }
                        val iconContainer = FrameLayout(this).apply {
                            tag = app.packageName
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
                                    visibility = if (
                                        isAppSelectionMode &&
                                        !app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)
                                    ) View.VISIBLE else View.GONE
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
                            setOnLongClickListener {
                                if (isAppSelectionMode ||
                                    app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)
                                ) {
                                    startAppDrag(app, this)
                                } else {
                                    appDragCandidate = AppDragCandidate(app, this)
                                }
                                true
                            }
                        }
                        dock.addView(
                            iconContainer,
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        )
                    }
                    refreshSelectionWiggle()
                    appsGridRendered = true
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
            Toast.makeText(this, R.string.cant_open_app, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(launchIntent)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Unable to launch app $packageName", exception)
            Toast.makeText(this, R.string.cant_open_app, Toast.LENGTH_SHORT).show()
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
        if (appPageCount == 0) return
        val targetPage = pageIndex.coerceIn(0, appPageCount - 1)
        if (targetPage == appPageIndex) return

        val pages = appsPages ?: return
        val outgoingView = pages.getChildAt(appPageIndex) ?: return
        val incomingView = pages.getChildAt(targetPage) ?: return
        val direction = if (targetPage > appPageIndex) -1 else 1
        pages.displayedChild = targetPage
        appPageIndex = targetPage
        refreshSelectionWiggle()
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

    private fun animateAppPageEntrance(pageIndex: Int, direction: Int) {
        val page = appsPages?.getChildAt(pageIndex) ?: return
        val width = appsPages?.width?.takeIf { it > 0 }?.toFloat()
            ?: screenContainer.width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        page.animate().cancel()
        page.translationX = -direction * width
        page.alpha = 1f
        page.animate()
            .translationX(0f)
            .setDuration(260L)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0.2f, 1f))
            .start()
    }

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
            refreshSelectionWiggle()
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
            refreshSelectionWiggle()
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
        appOptionsPopup = popupWindow
        popupWindow.setOnDismissListener {
            if (appOptionsPopup === popupWindow) {
                appOptionsPopup = null
            }
            appDragCandidate = null
        }

        popupView.findViewById<View>(R.id.app_action_info).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, R.string.cant_open_app_info, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, R.string.edit_not_available, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, R.string.cant_remove_app, Toast.LENGTH_SHORT).show()
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
        draggedDockSlot = appsDock?.indexOfChild(source)?.takeIf { it >= 0 }
        dragStartedInDock = draggedDockSlot != null
        draggedApp = app
        dragSource = source
        dragSourceVisibility = source.visibility
        source.visibility = View.INVISIBLE
        dragEdge = 0
        dragMoved = false
        dragCreatedEmptyAppPage = false
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
        dragCheckBadge = createDragCheckBadge().also { badge ->
            screenContainer.addView(
                badge,
                FrameLayout.LayoutParams(dp(20), dp(20))
            )
        }
        startDragWiggle(appPageDragWiggleTargets(appPageIndex) + dragShadow!!)
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
        val screenLocation = IntArray(2)
        screenContainer.getLocationOnScreen(screenLocation)
        dragLastRawX = screenLocation[0] + downX
        dragLastRawY = screenLocation[1] + downY
    }

    private fun appPageDragWiggleTargets(pageIndex: Int): List<View> {
        val icons = mutableListOf<View>()
        val grid = appsPages?.getChildAt(pageIndex) as? GridView
        if (grid != null) {
            for (childIndex in 0 until grid.childCount) {
                val adapterPosition = grid.firstVisiblePosition + childIndex
                if (grid.adapter?.getItem(adapterPosition) != null) {
                    grid.getChildAt(childIndex)
                        .findViewById<ImageView>(R.id.app_icon)
                        ?.let(icons::add)
                }
            }
        }
        appsDock?.let { dock ->
            for (index in 0 until dock.childCount) {
                val child = dock.getChildAt(index)
                if (child.tag is String) {
                    child.findViewById<ImageView>(R.id.app_icon)?.let(icons::add)
                }
            }
        }
        return icons
    }

    private fun startAppPageDragWiggleWhenReady(pageIndex: Int) {
        val page = appsPages?.getChildAt(pageIndex) ?: return
        page.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    page.viewTreeObserver.takeIf { it.isAlive }
                        ?.removeOnPreDrawListener(this)
                    if (draggedApp != null && appPageIndex == pageIndex) {
                        startDragWiggle(
                            appPageDragWiggleTargets(pageIndex) + listOfNotNull(dragShadow)
                        )
                    }
                    return true
                }
            }
        )
    }

    private fun updateDragShadow(x: Float, y: Float) {
        val shadow = dragShadow ?: return
        val layoutParams = shadow.layoutParams as? FrameLayout.LayoutParams ?: return
        layoutParams.leftMargin = (x - dp(32)).toInt().coerceAtLeast(0)
        layoutParams.topMargin = (y - dp(32)).toInt().coerceAtLeast(0)
        shadow.layoutParams = layoutParams
        dragCheckBadge?.let { badge ->
            val badgeParams = badge.layoutParams as? FrameLayout.LayoutParams ?: return@let
            badgeParams.leftMargin = (layoutParams.leftMargin + dp(42))
                .coerceAtMost((screenContainer.width - dp(20)).coerceAtLeast(0))
            badgeParams.topMargin = (layoutParams.topMargin - dp(4)).coerceAtLeast(0)
            badge.layoutParams = badgeParams
        }
    }

    private fun createDragCheckBadge() = TextView(this).apply {
        text = "✓"
        gravity = Gravity.CENTER
        textSize = 13f
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF1685E8.toInt())
            setStroke(dp(1), 0xFFFFFFFF.toInt())
        }
        elevation = dp(14).toFloat()
    }

    private fun startDragWiggle(icons: List<View>) {
        stopDragWiggle()
        icons.forEachIndexed { index, icon ->
            ObjectAnimator.ofFloat(icon, View.ROTATION, -5f, 5f, -5f).apply {
                duration = 180L
                startDelay = (index % 4) * 30L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
                dragWiggleAnimators += this
            }
        }
    }

    private fun refreshSelectionWiggle() {
        if (!isAppSelectionMode) {
            stopDragWiggle()
            return
        }
        if (draggedApp != null || folderDragState != null) return

        val pages = appsPages
        if (pages != null) {
            for (pageIndex in 0 until pages.childCount) {
                val grid = pages.getChildAt(pageIndex) as? GridView ?: continue
                for (childIndex in 0 until grid.childCount) {
                    val adapterPosition = grid.firstVisiblePosition + childIndex
                    if (grid.adapter?.getItem(adapterPosition) != null) {
                        grid.getChildAt(childIndex)
                            .findViewById<ImageView>(R.id.app_icon)
                            ?.let(::startSelectionWiggle)
                    }
                }
            }
        }
        appsDock?.let { dock ->
            for (index in 0 until dock.childCount) {
                val child = dock.getChildAt(index)
                if (child.tag is String) {
                    child.findViewById<ImageView>(R.id.app_icon)?.let(::startSelectionWiggle)
                }
            }
        }
    }

    private fun startSelectionWiggle(icon: View) {
        if (dragWiggleAnimators.any { it.target === icon }) return
        ObjectAnimator.ofFloat(icon, View.ROTATION, -5f, 5f, -5f).apply {
            duration = 180L
            startDelay = (dragWiggleAnimators.size % 4) * 30L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
            dragWiggleAnimators += this
        }
    }

    private fun stopDragWiggle() {
        dragWiggleAnimators.forEach { animator ->
            animator.cancel()
            animator.target?.let { target ->
                (target as? View)?.rotation = 0f
            }
        }
        dragWiggleAnimators.clear()
    }

    private fun updateDragShadowFromScreen(x: Float, y: Float) {
        val location = IntArray(2)
        screenContainer.getLocationOnScreen(location)
        dragLastRawX = x
        dragLastRawY = y
        updateDragShadow(x - location[0], y - location[1])
    }

    private fun navigateAppGridDuringDrag(x: Float) {
        if (!dragStartedInAppGrid || currentScreen != Screen.APPS || isAlphabeticalLibrary) return
        val location = IntArray(2)
        screenContainer.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val right = left + screenContainer.width
        if (screenContainer.width <= 0) return
        val edgeWidth = dp(48).toFloat()
        val edge = when {
            x < left + edgeWidth -> -1
            x > right - edgeWidth -> 1
            else -> 0
        }
        if (edge == 0) {
            cancelDragPageChange()
            dragEdge = 0
            return
        }
        if (edge != dragEdge) {
            cancelDragPageChange()
            dragEdge = edge
        }
        if (dragPageChangeRunnable == null) {
            scheduleDragPageChange(edge)
        }
    }

    private fun scheduleDragPageChange(edge: Int) {
        val changePage = Runnable {
            dragPageChangeRunnable = null
            if (draggedApp == null || dragEdge != edge ||
                currentScreen != Screen.APPS || isAlphabeticalLibrary
            ) {
                return@Runnable
            }
            val targetPage = appPageIndex + if (edge > 0) 1 else -1
            if (targetPage >= appPageCount && edge > 0 && !dragCreatedEmptyAppPage) {
                createAppGridPageDuringDrag()
            } else if (targetPage !in 0 until appPageCount) {
                return@Runnable
            }

            showAppPage(targetPage)
            startAppPageDragWiggleWhenReady(targetPage)
            updateAppPageIndicator()
            updateDragTargetLabel()
            updateDragShadowFromScreen(dragLastRawX, dragLastRawY)
            dragShadow?.let(screenContainer::bringChildToFront)
            dragTargetLabel?.let(screenContainer::bringChildToFront)
            if (appPageIndex < appPageCount - 1) {
                scheduleDragPageChange(edge)
            }
        }
        dragPageChangeRunnable = changePage
        dragPageHandler.postDelayed(changePage, DRAG_PAGE_CHANGE_DELAY_MS)
    }

    private fun createAppGridPageDuringDrag() {
        val apps = installedApps ?: return
        val pageStart = appPageCount * appGridPageSize
        val orderedPackages = appGridPackages.toMutableList()
        while (orderedPackages.size < pageStart + appGridPageSize) {
            orderedPackages.add(null)
        }
        appGridPackages = orderedPackages
        appGridOrderPackages.clear()
        appGridOrderPackages.addAll(orderedPackages)
        saveAppGridOrder()
        dragCreatedEmptyAppPage = true
        renderApps?.invoke(apps)
    }

    private fun cancelDragPageChange() {
        dragPageChangeRunnable?.let(dragPageHandler::removeCallbacks)
        dragPageChangeRunnable = null
    }

    private fun updateDragTargetLabel() {
        val label = dragTargetLabel ?: return
        if (dragStartedInAppGrid && currentScreen == Screen.APPS && !isAlphabeticalLibrary) {
            label.text = getString(R.string.drop_on_app_grid, appPageIndex + 1)
            label.visibility = View.VISIBLE
        } else {
            label.visibility = View.GONE
        }
    }

    private fun dropDraggedApp(x: Float, y: Float): Boolean {
        val app = draggedApp ?: return false
        if (currentScreen != Screen.APPS || isAlphabeticalLibrary) {
            Toast.makeText(this, R.string.drop_apps_in_grid_only, Toast.LENGTH_SHORT).show()
            return false
        }
        val grid = appsPages
            ?.getChildAt(appPageIndex)
            ?.findViewWithTag<View>("$APP_GRID_TAG:$appPageIndex") as? GridView
        val gridLocation = IntArray(2)
        grid?.getLocationOnScreen(gridLocation)
        val insideGrid = grid != null &&
            x >= gridLocation[0] && x <= gridLocation[0] + grid.width &&
            y >= gridLocation[1] && y <= gridLocation[1] + grid.height
        val gridPosition = if (insideGrid) {
            grid?.pointToPosition(
                (x - gridLocation[0]).toInt(),
                (y - gridLocation[1]).toInt()
            ) ?: GridView.INVALID_POSITION
        } else {
            GridView.INVALID_POSITION
        }
        val dockTarget = appsDock?.let { dock ->
            (0 until dock.childCount)
                .mapNotNull(dock::getChildAt)
                .firstOrNull { target ->
                    val targetLocation = IntArray(2)
                    target.getLocationOnScreen(targetLocation)
                    x >= targetLocation[0] && x <= targetLocation[0] + target.width &&
                        y >= targetLocation[1] && y <= targetLocation[1] + target.height
                }
        }
        val dockPackage = dockTarget?.tag as? String
        val dockTargetIndex = dockTarget?.let { target ->
            appsDock?.indexOfChild(target)
        }?.takeIf { it >= 0 }
        val gridPackage = if (gridPosition != GridView.INVALID_POSITION) {
            appGridPackages.getOrNull(appPageIndex * appGridPageSize + gridPosition)
        } else {
            null
        }
        val targetPackage = dockPackage ?: gridPackage
        if (dockTargetIndex != null && dockPackage == null) {
            return moveGridIconToDock(app, dockTargetIndex)
        }
        if (targetPackage != null && targetPackage != app.packageName) {
            val targetFolderId = targetPackage
                .takeIf { it.startsWith(FOLDER_PACKAGE_PREFIX) }
                ?.removePrefix(FOLDER_PACKAGE_PREFIX)
            val targetFolderIndex = targetFolderId?.let { id ->
                appFolders.indexOfFirst { it.id == id }
            } ?: -1
            if (targetFolderIndex >= 0 && !app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)) {
                addAppToFolder(app, targetFolderIndex)
                return false
            }
            if (!app.packageName.startsWith(FOLDER_PACKAGE_PREFIX) &&
                !targetPackage.startsWith(FOLDER_PACKAGE_PREFIX)
            ) {
                createFolderFromDrop(
                    app,
                    targetPackage,
                    targetDockIndex = dockTargetIndex,
                    targetGridIndex = if (dockPackage == null) {
                        appPageIndex * appGridPageSize + gridPosition
                    } else {
                        -1
                    }
                )
                return false
            }
            if (app.packageName.startsWith(FOLDER_PACKAGE_PREFIX) &&
                !targetPackage.startsWith(FOLDER_PACKAGE_PREFIX)
            ) {
                val folderId = app.packageName.removePrefix(FOLDER_PACKAGE_PREFIX)
                val folderIndex = appFolders.indexOfFirst { it.id == folderId }
                val targetApp = installedApps.orEmpty().firstOrNull {
                    it.packageName == targetPackage
                }
                if (folderIndex >= 0 && targetApp != null) {
                    addAppToFolder(targetApp, folderIndex)
                    return false
                }
            }
        }
        if (grid == null || gridPosition == GridView.INVALID_POSITION) {
            if (grid != null && insideGrid && dragStartedInDock && targetPackage == null) {
                moveDockIconToGrid(
                    app,
                    appPageIndex * appGridPageSize + appGridPageSize - 1
                )
                return true
            }
            Toast.makeText(this, R.string.drop_app_on_icon_or_dock, Toast.LENGTH_SHORT).show()
            return false
        }
        if (dragStartedInDock && targetPackage == null) {
            moveDockIconToGrid(
                app,
                appPageIndex * appGridPageSize + gridPosition
            )
            return true
        }
        val dropAfterTarget = if (gridPosition == GridView.INVALID_POSITION) {
            false
        } else {
            val targetView = grid.getChildAt(gridPosition - grid.firstVisiblePosition)
            if (targetView == null) {
                false
            } else {
                val targetLocation = IntArray(2)
                targetView.getLocationOnScreen(targetLocation)
                x >= targetLocation[0] + targetView.width / 2f
            }
        }
        return reorderAppGridItem(app, appPageIndex, gridPosition, dropAfterTarget)
    }

    private fun moveGridIconToDock(app: AppInfo, targetSlot: Int): Boolean {
        val packageName = app.packageName
        val sourceIndex = appGridPackages.indexOf(packageName)
        val moved = if (dragStartedInDock) {
            draggedDockSlot != targetSlot
        } else {
            sourceIndex >= 0
        }
        if (dragStartedInDock) {
            draggedDockSlot?.takeIf { it != targetSlot }?.let { dockEmptySlots += it }
        }
        val folderId = packageName
            .takeIf { it.startsWith(FOLDER_PACKAGE_PREFIX) }
            ?.removePrefix(FOLDER_PACKAGE_PREFIX)

        dockEmptySlots.remove(targetSlot)
        if (folderId != null) {
            dockFolderSlots[folderId] = targetSlot
            saveDockFolderSlots()
        } else {
            dockRemovedAppPackages.remove(packageName)
            dockAppSlots[packageName] = targetSlot
            saveDockRemovedAppPackages()
            saveDockAppSlots()
        }
        saveDockEmptySlots()

        if (sourceIndex >= 0) {
            val orderedPackages = appGridPackages.toMutableList().apply {
                this[sourceIndex] = null
            }
            appGridPackages = orderedPackages
            appGridOrderPackages.clear()
            appGridOrderPackages.addAll(orderedPackages)
            saveAppGridOrder()
        }
        installedApps?.let { renderApps?.invoke(it) }
        return moved
    }

    private fun moveDockIconToGrid(app: AppInfo, targetIndex: Int) {
        val packageName = app.packageName
        draggedDockSlot?.let { dockEmptySlots += it }
        if (packageName.startsWith(FOLDER_PACKAGE_PREFIX)) {
            val folderId = packageName.removePrefix(FOLDER_PACKAGE_PREFIX)
            dockFolderSlots.remove(folderId)
            saveDockFolderSlots()
        } else {
            dockAppSlots.remove(packageName)
            dockRemovedAppPackages.add(packageName)
            saveDockRemovedAppPackages()
        }
        saveDockAppSlots()
        saveDockEmptySlots()

        val orderedPackages = appGridPackages.toMutableList()
        while (orderedPackages.size <= targetIndex) orderedPackages.add(null)
        orderedPackages[targetIndex] = packageName
        appGridPackages = orderedPackages
        appGridOrderPackages.clear()
        appGridOrderPackages.addAll(orderedPackages)
        saveAppGridOrder()
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun createFolderFromDrop(
        draggedApp: AppInfo,
        targetPackage: String,
        targetDockIndex: Int?,
        targetGridIndex: Int
    ) {
        val sourceIndex = appGridPackages.indexOf(draggedApp.packageName)
        if (targetPackage == draggedApp.packageName) return

        val folderId = UUID.randomUUID().toString()
        val folderPackage = FOLDER_PACKAGE_PREFIX + folderId
        appFolders += AppFolder(
            folderId,
            getString(R.string.folder),
            listOf(targetPackage, draggedApp.packageName)
        )
        draggedDockSlot?.takeIf { it != targetDockIndex }?.let { dockEmptySlots += it }
        dockAppSlots.remove(draggedApp.packageName)
        dockAppSlots.remove(targetPackage)
        dockRemovedAppPackages.remove(draggedApp.packageName)
        dockRemovedAppPackages.remove(targetPackage)
        if (targetDockIndex != null) {
            dockEmptySlots.remove(targetDockIndex)
            dockFolderSlots[folderId] = targetDockIndex
        }
        saveDockAppSlots()
        saveDockEmptySlots()
        saveDockRemovedAppPackages()
        saveDockFolderSlots()
        saveAppFolders()

        val orderedPackages = appGridPackages.toMutableList()
        if (sourceIndex >= 0) orderedPackages[sourceIndex] = null
        if (targetDockIndex == null && targetGridIndex in orderedPackages.indices) {
            orderedPackages[targetGridIndex] = folderPackage
        }
        appGridPackages = orderedPackages
        appGridOrderPackages.clear()
        appGridOrderPackages.addAll(orderedPackages)
        saveAppGridOrder()
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun addAppToFolder(app: AppInfo, folderIndex: Int) {
        val folder = appFolders[folderIndex]
        if (app.packageName in folder.packageNames) return

        if (dragStartedInDock && draggedApp?.packageName == app.packageName) {
            draggedDockSlot?.let { dockEmptySlots += it }
            dockAppSlots.remove(app.packageName)
            dockRemovedAppPackages.add(app.packageName)
            saveDockAppSlots()
            saveDockEmptySlots()
            saveDockRemovedAppPackages()
        }

        appFolders[folderIndex] = folder.copy(
            packageNames = folder.packageNames + app.packageName
        )
        saveAppFolders()

        val sourceIndex = appGridPackages.indexOf(app.packageName)
        if (sourceIndex >= 0) {
            val orderedPackages = appGridPackages.toMutableList().apply {
                this[sourceIndex] = null
            }
            appGridPackages = orderedPackages
            appGridOrderPackages.clear()
            appGridOrderPackages.addAll(orderedPackages)
            saveAppGridOrder()
        }
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun finishAppDrag() {
        val shouldRefreshGrid = dragCreatedEmptyAppPage
        dragCreatedEmptyAppPage = false
        stopDragWiggle()
        dragShadow?.let(screenContainer::removeView)
        dragCheckBadge?.let(screenContainer::removeView)
        dragTargetLabel?.let(screenContainer::removeView)
        dragSource?.visibility = dragSourceVisibility
        dragShadow = null
        dragCheckBadge = null
        dragTargetLabel = null
        draggedApp = null
        dragSource = null
        dragStartedInAppGrid = false
        dragStartedInDock = false
        draggedDockSlot = null
        cancelDragPageChange()
        dragEdge = 0
        dragMoved = false
        if (shouldRefreshGrid) {
            installedApps?.let { renderApps?.invoke(it) }
        }
        refreshSelectionWiggle()
    }

    private fun orderAppGridApps(apps: List<AppInfo>): List<AppInfo?> =
        AppGridOrder.orderItems(apps, appGridOrderPackages, AppInfo::packageName)

    private fun reorderAppGridItem(
        app: AppInfo,
        pageIndex: Int,
        pagePosition: Int,
        dropAfterTarget: Boolean
    ): Boolean {
        val reorderedPackages = AppGridOrder.moveApp(
            packages = appGridPackages,
            packageName = app.packageName,
            pageIndex = pageIndex,
            pagePosition = pagePosition.takeUnless { it == GridView.INVALID_POSITION },
            pageSize = appGridPageSize,
            dropAfterTarget = dropAfterTarget
        ) ?: return false
        appGridPackages = reorderedPackages
        appGridOrderPackages.clear()
        appGridOrderPackages.addAll(reorderedPackages)
        saveAppGridOrder()
        installedApps?.let { renderApps?.invoke(it) }
        return true
    }

    private fun loadAppGridOrder(): List<String?> = layoutRepository.loadAppGridOrder()

    private fun saveAppGridOrder() = layoutRepository.saveAppGridOrder(appGridOrderPackages)

    private fun promptCreateAppFolder() {
        if (selectedAppPackages.size < 2) {
            Toast.makeText(this, R.string.select_two_apps, Toast.LENGTH_SHORT).show()
            return
        }

        val nameInput = EditText(this).apply {
            setText(R.string.folder)
            setSelection(text.length)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.create_folder)
            .setView(nameInput)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.enter_folder_name, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, R.string.not_enough_apps, Toast.LENGTH_SHORT).show()
            return
        }

        appFolders += AppFolder(UUID.randomUUID().toString(), name, members)
        saveAppFolders()
        exitAppSelectionMode()
        Toast.makeText(
            this,
            getString(R.string.folder_created, name),
            Toast.LENGTH_SHORT
        ).show()
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
                    val currentApps = (0 until folderAdapter.count)
                        .mapNotNull(folderAdapter::getItem)
                    startFolderAppDrag(
                        currentFolder,
                        app,
                        currentApps,
                        this,
                        folderAdapter,
                        overlay,
                        popupWindow,
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
        popupWindow: PopupWindow,
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
            text = getString(R.string.drop_to_reorder)
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
        val checkBadge = createDragCheckBadge()
        root.addView(shadow, FrameLayout.LayoutParams(dp(64), dp(64)))
        root.addView(checkBadge, FrameLayout.LayoutParams(dp(20), dp(20)))
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
            popupWindow = popupWindow,
            source = source,
            sourceVisibility = source.visibility,
            shadow = shadow,
            checkBadge = checkBadge,
            label = label,
            downX = downX,
            downY = downY
        )
        startDragWiggle(
            (0 until grid.childCount).mapNotNull { childIndex ->
                val adapterPosition = grid.firstVisiblePosition + childIndex
                grid.getChildAt(childIndex).takeIf {
                    adapter.getItem(adapterPosition) != null
                }?.findViewById<ImageView>(R.id.app_icon)
            } + shadow
        )
        source.visibility = View.INVISIBLE
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
        val badgeParams = state.checkBadge.layoutParams as? FrameLayout.LayoutParams ?: return
        badgeParams.leftMargin = (layoutParams.leftMargin + dp(42))
            .coerceAtMost((state.root.width - dp(20)).coerceAtLeast(0))
        badgeParams.topMargin = (layoutParams.topMargin - dp(4)).coerceAtLeast(0)
        state.checkBadge.layoutParams = badgeParams
    }

    private fun dropFolderApp(state: FolderDragState, x: Float, y: Float) {
        val gridLocation = IntArray(2)
        state.grid.getLocationOnScreen(gridLocation)
        val insideGrid = x >= gridLocation[0] &&
            x <= gridLocation[0] + state.grid.width &&
            y >= gridLocation[1] &&
            y <= gridLocation[1] + state.grid.height
        if (!insideGrid) {
            extractFolderApp(state, x, y)
            return
        }

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

    private fun extractFolderApp(state: FolderDragState, x: Float, y: Float) {
        if (currentScreen != Screen.APPS || isAlphabeticalLibrary) return
        val grid = appsPages
            ?.getChildAt(appPageIndex)
            ?.findViewWithTag<View>("$APP_GRID_TAG:$appPageIndex") as? GridView
        val gridLocation = IntArray(2)
        grid?.getLocationOnScreen(gridLocation)
        val insideGrid = grid != null &&
            x >= gridLocation[0] && x <= gridLocation[0] + grid.width &&
            y >= gridLocation[1] && y <= gridLocation[1] + grid.height
        val gridPosition = if (insideGrid) {
            grid?.pointToPosition(
                (x - gridLocation[0]).toInt(),
                (y - gridLocation[1]).toInt()
            ) ?: GridView.INVALID_POSITION
        } else {
            GridView.INVALID_POSITION
        }
        val gridIndex = if (gridPosition != GridView.INVALID_POSITION) {
            appPageIndex * appGridPageSize + gridPosition
        } else {
            -1
        }
        val gridSlotIsEmpty = gridIndex >= 0 && appGridPackages.getOrNull(gridIndex) == null

        val dock = appsDock
        val dockSlot = dock?.let { dockView ->
            (0 until dockView.childCount)
                .mapNotNull(dockView::getChildAt)
                .firstOrNull { target ->
                    val targetLocation = IntArray(2)
                    target.getLocationOnScreen(targetLocation)
                    x >= targetLocation[0] && x <= targetLocation[0] + target.width &&
                        y >= targetLocation[1] && y <= targetLocation[1] + target.height
                }
        }?.let { target ->
            dock.indexOfChild(target).takeIf { target.tag == null }
        }?.takeIf { it >= 0 }

        if (!gridSlotIsEmpty && dockSlot == null) return
        val sourceIndex = appGridPackages.indexOf(state.draggedApp.packageName)
        val folderIndex = appFolders.indexOfFirst { it.id == state.folder.id }
        if (folderIndex < 0) return

        val remainingPackages = appFolders[folderIndex].packageNames
            .filterNot { it == state.draggedApp.packageName }
        val dissolvesFolder = remainingPackages.size < 2
        val folderGridIndex = if (dissolvesFolder) {
            appGridPackages.indexOf("$FOLDER_PACKAGE_PREFIX${state.folder.id}")
        } else {
            -1
        }
        val folderDockSlot = if (dissolvesFolder) {
            dockFolderSlots.remove(state.folder.id)
        } else {
            null
        }
        if (remainingPackages.size >= 2) {
            appFolders[folderIndex] = appFolders[folderIndex].copy(
                packageNames = remainingPackages
            )
        } else {
            appFolders.removeAt(folderIndex)
            saveDockFolderSlots()
        }
        saveAppFolders()

        val orderedPackages = appGridPackages.toMutableList()
        if (gridSlotIsEmpty) {
            while (orderedPackages.size <= gridIndex) orderedPackages.add(null)
        }
        when {
            dockSlot != null -> {
                dockEmptySlots.remove(dockSlot)
                dockRemovedAppPackages.remove(state.draggedApp.packageName)
                dockAppSlots[state.draggedApp.packageName] = dockSlot
                saveDockRemovedAppPackages()
                saveDockAppSlots()
                saveDockEmptySlots()
            }
            gridSlotIsEmpty -> {
                orderedPackages[gridIndex] = state.draggedApp.packageName
            }
        }
        if (dissolvesFolder && remainingPackages.size == 1) {
            val remainingPackage = remainingPackages.single()
            when {
                folderGridIndex >= 0 -> {
                    while (orderedPackages.size <= folderGridIndex) orderedPackages.add(null)
                    orderedPackages[folderGridIndex] = remainingPackage
                }
                folderDockSlot != null -> {
                    dockEmptySlots.remove(folderDockSlot)
                    dockRemovedAppPackages.remove(remainingPackage)
                    dockAppSlots[remainingPackage] = folderDockSlot
                    saveDockRemovedAppPackages()
                    saveDockAppSlots()
                    saveDockEmptySlots()
                }
            }
        }
        if (sourceIndex >= 0 && sourceIndex != gridIndex) {
            orderedPackages[sourceIndex] = null
            if (gridSlotIsEmpty && gridIndex >= 0) {
                orderedPackages[gridIndex] = state.draggedApp.packageName
            }
        }
        if (gridSlotIsEmpty || folderGridIndex >= 0 ||
            (sourceIndex >= 0 && sourceIndex != gridIndex)
        ) {
            saveAppGridOrderAfterUpdate(orderedPackages)
        }
        state.popupWindow.dismiss()
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun saveAppGridOrderAfterUpdate(orderedPackages: MutableList<String?>) {
        appGridPackages = orderedPackages
        appGridOrderPackages.clear()
        appGridOrderPackages.addAll(orderedPackages)
        saveAppGridOrder()
    }

    private fun finishFolderAppDrag() {
        val state = folderDragState ?: return
        folderDragState = null
        stopDragWiggle()
        state.source.visibility = state.sourceVisibility
        state.root.removeView(state.shadow)
        state.root.removeView(state.checkBadge)
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

    private fun loadAppFolders(): List<AppFolder> = layoutRepository.loadAppFolders()

    private fun saveAppFolders() = layoutRepository.saveAppFolders(appFolders)

    private fun loadDockFolderSlots(): Map<String, Int> =
        layoutRepository.loadDockFolderSlots(appFolders)

    private fun saveDockFolderSlots() = layoutRepository.saveDockFolderSlots(dockFolderSlots)

    private fun loadDockAppSlots(): Map<String, Int> = layoutRepository.loadDockAppSlots()

    private fun saveDockAppSlots() = layoutRepository.saveDockAppSlots(dockAppSlots)

    private fun loadDockEmptySlots(): Set<Int> = layoutRepository.loadDockEmptySlots()

    private fun saveDockEmptySlots() = layoutRepository.saveDockEmptySlots(dockEmptySlots)

    private fun loadDockRemovedAppPackages(): Set<String> =
        layoutRepository.loadDockRemovedAppPackages()

    private fun saveDockRemovedAppPackages() =
        layoutRepository.saveDockRemovedAppPackages(dockRemovedAppPackages)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val dragCandidate = appDragCandidate
        if (dragCandidate != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - downX) > swipeTouchSlop ||
                        abs(event.y - downY) > swipeTouchSlop
                    ) {
                        appDragCandidate = null
                        appOptionsPopup?.dismiss()
                        appOptionsPopup = null
                        startAppDrag(dragCandidate.app, dragCandidate.source)
                        dragMoved = true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    appDragCandidate = null
                }
            }
        }
        if (draggedApp != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    updateDragShadowFromScreen(event.rawX, event.rawY)
                    updateDragTargetLabel()
                    if (abs(event.x - downX) > swipeTouchSlop ||
                        abs(event.y - downY) > swipeTouchSlop
                    ) {
                        dragMoved = true
                    }
                    navigateAppGridDuringDrag(event.rawX)
                }
                MotionEvent.ACTION_UP -> {
                    val movedApp = if (dragMoved) {
                        draggedApp?.takeIf { dropDraggedApp(event.rawX, event.rawY) }
                    } else {
                        null
                    }
                    finishAppDrag()
                    movedApp
                        ?.takeUnless { it.packageName.startsWith(FOLDER_PACKAGE_PREFIX) }
                        ?.let { app ->
                            isAppSelectionMode = true
                            selectedAppPackages.clear()
                            selectedAppPackages.add(app.packageName)
                            installedApps?.let { renderApps?.invoke(it) }
                        }
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
        cancelDragPageChange()
        appLoadExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val APP_GRID_TAG = "app_grid_page"
        const val DRAG_PAGE_CHANGE_DELAY_MS = 500L
        const val FOLDER_PACKAGE_PREFIX = "launcher.folder:"
        const val TAG = "MainActivity"
    }
}