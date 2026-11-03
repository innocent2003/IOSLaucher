package com.example.oslaucher.ui

import android.app.Dialog
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import android.widget.BaseAdapter
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.example.oslaucher.models.HomeWidgetPlacement
import com.example.oslaucher.models.LauncherWidgetType
import com.example.oslaucher.models.AppPageSwipeTransition
import com.example.oslaucher.models.FolderDragState
import com.example.oslaucher.models.HomeSwipeTransition
import com.example.oslaucher.repositories.InstalledAppRepository
import com.example.oslaucher.repositories.LauncherLayoutRepository
import com.example.oslaucher.ui.fragments.HomeFragment
import com.example.oslaucher.ui.fragments.HomeScreenActions
import com.example.oslaucher.ui.fragments.HomeSecondFragment
import com.example.oslaucher.ui.widgets.LauncherWidgetFactory
import com.example.oslaucher.utils.AppIconUtils
import com.example.oslaucher.utils.HiddenAppsPreferences
import com.example.oslaucher.utils.LauncherConfig
import com.example.oslaucher.utils.LauncherWallpaper
import com.example.oslaucher.utils.Screen
import com.example.oslaucher.utils.WeatherWidgetPreferences
import java.util.concurrent.Executors
import java.util.UUID
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), HomeScreenActions {
    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val layoutRepository by lazy { LauncherLayoutRepository(applicationContext) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val appWidgetHost by lazy { AppWidgetHost(this, APP_WIDGET_HOST_ID) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var screenContainer: FrameLayout
    private var wallpaperReceiverRegistered = false
    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshWallpaperViews()
        }
    }
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
    private var widgetLayoutRetryPosted = false
    private var appPageSwipeTransition: AppPageSwipeTransition? = null
    private var widgetSwipeAnimator: ValueAnimator? = null
    private var isLoadingApps = false
    private var isAppSelectionMode = false
    private var isHomeEditMode = false
    private val selectedAppPackages = mutableSetOf<String>()
    private val appFolders = mutableListOf<AppFolder>()
    private val dockFolderSlots = mutableMapOf<String, Int>()
    private val dockAppSlots = mutableMapOf<String, Int>()
    private val dockEmptySlots = mutableSetOf<Int>()
    private val dockRemovedAppPackages = mutableSetOf<String>()
    private var appsDock: LinearLayout? = null
    private var appsWidgetContainer: FrameLayout? = null
    private val homeWidgets = mutableListOf<HomeWidgetPlacement>()
    private val appWidgetViews = mutableMapOf<Int, AppWidgetHostView>()
    private var draggedWidgetPlacement: HomeWidgetPlacement? = null
    private var draggedWidgetSource: View? = null
    private var dragShadowCenteredOnPointer = false
    private var dragCreatedWidgetPageIndex: Int? = null
    private var pendingWidgetId: Int? = null
    private var pendingWidgetPage = 0
    private var widgetPickerDialog: Dialog? = null
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
    private val dragPreviewViews = mutableListOf<View>()
    private var dragPreviewPosition: Int? = null
    private var dragPreviewDropAfter = false
    private var dragPreviewReorderedPackages: List<String?>? = null
    private var dragEdge = 0
    private var dragPageChangeRunnable: Runnable? = null
    private val dragPageHandler = Handler(Looper.getMainLooper())
    private val weatherRefreshHandler = Handler(Looper.getMainLooper())
    private val weatherRefreshRunnable = Runnable {
        if (
            currentScreen == Screen.APPS &&
            homeWidgets.any {
                it.launcherWidgetType == LauncherWidgetType.WEATHER &&
                    it.pageIndex == appPageIndex
            }
        ) {
            renderHomeWidgetViews()
        }
        scheduleWeatherRefresh()
    }
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

    private val widgetBindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val widgetId = pendingWidgetId ?: return@registerForActivityResult
            if (result.resultCode != RESULT_OK) {
                discardPendingWidget(widgetId)
                return@registerForActivityResult
            }
            val returnedId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                widgetId
            ) ?: widgetId
            if (returnedId != widgetId) appWidgetHost.deleteAppWidgetId(widgetId)
            pendingWidgetId = returnedId
            val provider = appWidgetManager.getAppWidgetInfo(returnedId)
            if (provider == null) {
                discardPendingWidget(returnedId)
                Toast.makeText(this, R.string.home_widget_bind_denied, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            configureOrAddWidget(returnedId, provider)
        }

    private val widgetConfigureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val widgetId = pendingWidgetId ?: return@registerForActivityResult
            pendingWidgetId = null
            if (result.resultCode == RESULT_OK) {
                addHomeWidget(widgetId, pendingWidgetPage)
            } else {
                appWidgetHost.deleteAppWidgetId(widgetId)
            }
        }

    private val uninstallAppLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                installedApps = null
                requestInstalledApps { apps ->
                    if (currentScreen == Screen.APPS) {
                        renderApps?.invoke(apps)
                    }
                }
            }
        }

    private val weatherSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val existingWeatherWidget = homeWidgets.firstOrNull {
                it.launcherWidgetType == LauncherWidgetType.WEATHER
            }
            if (existingWeatherWidget != null) {
                appPageIndex = existingWeatherWidget.pageIndex
                installedApps?.let { renderApps?.invoke(it) } ?: renderHomeWidgetViews()
            } else {
                val targetPage = result.data?.getIntExtra(
                    WeatherSettingsActivity.EXTRA_PAGE_INDEX,
                    appPageIndex
                ) ?: appPageIndex
                addLauncherWidget(LauncherWidgetType.WEATHER, targetPage)
            }
            scheduleWeatherRefresh()
        }

    private val homeRoleRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            returningFromOwnScreen = false
            if (LauncherConfig.isDefaultHome(this)) {
                defaultHomeActive = true
                if (currentScreen == Screen.HOME ||
                    currentScreen == Screen.HOME_SECOND ||
                    currentScreen == Screen.APPS
                ) {
                    showScreen(currentScreen, 0)
                }
                Toast.makeText(this, R.string.default_home_set, Toast.LENGTH_SHORT).show()
            } else {
                openHomeSettingsFallback()
            }
        }

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
    private var returningFromOwnScreen = false
    private var defaultHomeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchedAsHome = intent?.categories?.contains(Intent.CATEGORY_HOME) == true
        defaultHomeActive = LauncherConfig.isDefaultHome(this)
        hiddenAppPackages.addAll(HiddenAppsPreferences.packages(this))

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
        homeWidgets.addAll(layoutRepository.loadHomeWidgets())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isHomeEditMode) {
                    finishHomeEditMode()
                    return
                }
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
                        if (!launchedAsHome) showScreen(Screen.SETTINGS, -1)
                    }
                    Screen.LOADING -> finish()
                    Screen.SETTINGS -> {
                        if (launchedAsHome) showScreen(Screen.HOME, 1) else finish()
                    }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchedAsHome = intent.categories?.contains(Intent.CATEGORY_HOME) == true
        if (launchedAsHome && currentScreen != Screen.HOME) {
            showScreen(Screen.HOME, 0)
        }
    }

    private fun startOwnScreen(target: Class<*>) {
        returningFromOwnScreen = true
        startActivity(Intent(this, target))
    }

    private fun requestDefaultHomeRole() {
        AlertDialog.Builder(this)
            .setTitle(R.string.default_home_confirm_title)
            .setMessage(R.string.default_home_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.continue_action) { _, _ ->
                openDefaultHomeSelection()
            }
            .show()
    }

    private fun openDefaultHomeSelection() {
        returningFromOwnScreen = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                try {
                    homeRoleRequestLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    )
                    return
                } catch (exception: ActivityNotFoundException) {
                    Log.w(TAG, "Unable to request the default home role", exception)
                }
            }
        }

        openHomeSettingsFallback()
    }

    private fun openHomeSettingsFallback() {
        returningFromOwnScreen = true
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addCategory(Intent.CATEGORY_DEFAULT)
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (exception: ActivityNotFoundException) {
            Log.w(TAG, "Unable to open default home settings", exception)
            try {
                startActivity(Intent.createChooser(homeIntent, null))
            } catch (fallbackException: ActivityNotFoundException) {
                Log.e(TAG, "Unable to launch default home selection", fallbackException)
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
        val latestHiddenPackages = HiddenAppsPreferences.packages(this)
        val hiddenAppsChanged = hiddenAppPackages != latestHiddenPackages
        if (hiddenAppsChanged) {
            hiddenAppPackages.clear()
            hiddenAppPackages.addAll(latestHiddenPackages)
        }
        val defaultHomeNowActive = LauncherConfig.isDefaultHome(this)
        val homeRoleChanged = defaultHomeNowActive != defaultHomeActive
        defaultHomeActive = defaultHomeNowActive
        if (returningFromOwnScreen) {
            returningFromOwnScreen = false
        } else if (launchedAsHome && currentScreen == Screen.SETTINGS) {
            showScreen(Screen.HOME, 0)
            return
        }
        if (homeRoleChanged && currentScreen != Screen.LOADING) {
            preloadedSettingsView = null
            showScreen(currentScreen, 0)
            return
        }
        if (currentScreen == Screen.HOME ||
            currentScreen == Screen.HOME_SECOND ||
            currentScreen == Screen.APPS
        ) {
            refreshWallpaperViews()
        }
        if (currentScreen == Screen.APPS) {
            if (hiddenAppsChanged) {
                installedApps?.let { renderApps?.invoke(it) }
            } else if (
                homeWidgets.any {
                    it.launcherWidgetType == LauncherWidgetType.WEATHER &&
                        it.pageIndex == appPageIndex
                } && WeatherWidgetPreferences.isRefreshDue(this)
            ) {
                renderHomeWidgetViews()
            }
        }
        scheduleWeatherRefresh()
    }

    override fun onPause() {
        weatherRefreshHandler.removeCallbacks(weatherRefreshRunnable)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (currentScreen == Screen.LOADING) return
        preloadedSettingsView = null
        showScreen(currentScreen, 0)
    }

    private fun showScreen(screen: Screen, animationDirection: Int = 0) {
        if (screen != Screen.APPS) appsWidgetContainer = null
        if (screen != Screen.APPS && (isHomeEditMode || isAppSelectionMode)) {
            isHomeEditMode = false
            isAppSelectionMode = false
            selectedAppPackages.clear()
            stopDragWiggle()
        }
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
            // HOME_SECOND là trang ngoài cùng bên phải:
            // vuốt phải (direction > 0) = quay lại APPS trang cuối
            // vuốt trái (direction < 0) = dead-end, không làm gì
            fromScreen == Screen.HOME_SECOND && direction > 0 -> Screen.APPS
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
                fromScreen == Screen.HOME_SECOND
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
        if (transition.fromScreen == Screen.APPS) {
            setWidgetSwipeProgress(transition.direction, transition.width, progress)
        }
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
            if (transition.fromScreen == Screen.APPS) resetWidgetSwipe()
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
            } else {
                openAppsAtLastPage = false
                removeTransitionScreen(transition.incomingFragment, transition.incomingView)
            }
            homeSwipeTransition = null
            homeSwipeSettling = false
        }

        if (transition.fromScreen == Screen.APPS) {
            animateWidgetSwipe(
                transition.direction,
                transition.width,
                progress,
                targetProgress,
                duration
            )
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

    private fun refreshWallpaperViews() {
        if (currentScreen != Screen.HOME &&
            currentScreen != Screen.HOME_SECOND &&
            currentScreen != Screen.APPS
        ) {
            return
        }
        for (index in 0 until screenContainer.childCount) {
            screenContainer.getChildAt(index)
                .findViewById<View>(R.id.main)
                ?.let(LauncherWallpaper::applyTo)
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
                    startOwnScreen(AppearanceActivity::class.java)
                }
                view.findViewById<View>(R.id.general_setting).setOnClickListener {
                    startOwnScreen(GeneralSettingsActivity::class.java)
                }
                view.findViewById<View>(R.id.hidden_apps_setting).setOnClickListener {
                    startOwnScreen(HiddenAppsActivity::class.java)
                }
                view.findViewById<View>(R.id.language_setting).setOnClickListener {
                    startOwnScreen(LanguageActivity::class.java)
                }
                view.findViewById<View>(R.id.screen_grid_setting).setOnClickListener {
                    startOwnScreen(ScreenGridActivity::class.java)
                }
                view.findViewById<View>(R.id.page_transition_setting).setOnClickListener {
                    startOwnScreen(PageTransitionActivity::class.java)
                }
                view.findViewById<View>(R.id.wallpaper_setting).setOnClickListener {
                    startOwnScreen(WallpaperActivity::class.java)
                }
                view.findViewById<View>(R.id.weather_setting).setOnClickListener {
                    startOwnScreen(WeatherSettingsActivity::class.java)
                }
                view.findViewById<View>(R.id.app_effects_setting).setOnClickListener {
                    startOwnScreen(AppEffectsActivity::class.java)
                }
                view.findViewById<View>(R.id.change_app_icon_setting).setOnClickListener {
                    startOwnScreen(ChangeAppIconActivity::class.java)
                }
            }
            Screen.HOME, Screen.HOME_SECOND -> Unit
            Screen.APPS -> {
                appsWidgetContainer = null
                if (isAlphabeticalLibrary) {
                    bindAlphabeticalAppLibrary(view)
                    return
                }
                val pages = view.findViewById<ViewFlipper>(R.id.apps_pages)
                val pageIndicator = view.findViewById<LinearLayout>(R.id.apps_page_indicator)
                val editControls = view.findViewById<LinearLayout>(R.id.apps_edit_controls)
                val selectionBar = view.findViewById<LinearLayout>(R.id.apps_selection_bar)
                val selectionCount = view.findViewById<TextView>(R.id.apps_selection_count)
                appsGridRendered = false
                appsPages = pages
                appsIndicator = pageIndicator
                appsWidgetContainer = view.findViewById(R.id.apps_widget_container)
                if (!openAppsAtLastPage) appPageIndex = 0
                val dock = view.findViewById<LinearLayout>(R.id.apps_dock)
                appsDock = dock
                val appLauncher: (AppInfo) -> Unit = { app ->
                    if (isHomeEditMode && !isAppSelectionMode) {
                        Unit
                    } else if (app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)) {
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
                    if (isAppSelectionMode || isHomeEditMode ||
                        app.packageName.startsWith(FOLDER_PACKAGE_PREFIX)
                    ) {
                        startAppDrag(app, anchor)
                    } else {
                        appDragCandidate = AppDragCandidate(app, anchor)
                        showAppOptions(app, anchor)
                    }
                }

                fun buildHomeApps(apps: List<AppInfo>) {
                    if (isAppSelectionMode || isHomeEditMode) stopDragWiggle()
                    (pages.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                        params.topMargin = dp(
                            if (isAppSelectionMode || isHomeEditMode) 8 else 22
                        )
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
                    val orderedGridApps = orderAppGridApps(
                        visibleApps.filterNot { it.packageName in dockPackages } + gridFolderTiles
                    )
                    val gridRows = LauncherConfig.gridRows(this@MainActivity, defaultHomeActive)
                    val appPageSize = 4 * gridRows
                    appGridPageSize = appPageSize
                    val appItemCount = orderedGridApps.count { it != null }
                    val sourcePageCount =
                        (orderedGridApps.size + appPageSize - 1) / appPageSize
                    normalizeHomeWidgetPlacements(appsWidgetContainer)
                    val lastWidgetPage = homeWidgets.maxOfOrNull { it.pageIndex } ?: -1
                    val pageRows = mutableListOf<Int>()
                    val appChunks = mutableListOf<List<AppInfo?>>()
                    val reflowAroundWidgets = homeWidgets.isNotEmpty()
                    val pageCount = maxOf(
                        sourcePageCount,
                        lastWidgetPage + 1,
                        (dragCreatedWidgetPageIndex ?: -1) + 1,
                        if (dragCreatedEmptyAppPage) appItemCount / appPageSize + 1 else 0
                    )
                    val displacedApps = mutableListOf<AppInfo>()
                    var pageIndex = 0
                    while (
                        pageIndex < pageCount || displacedApps.isNotEmpty()
                    ) {
                        val pageStart = pageIndex * appPageSize
                        val pageEnd = minOf(pageStart + appPageSize, orderedGridApps.size)
                        val sourceSlots = if (pageStart < pageEnd) {
                            orderedGridApps.subList(pageStart, pageEnd)
                        } else {
                            emptyList()
                        }
                        if (reflowAroundWidgets) {
                            val blockedSlots = widgetBlockedAppSlots(pageIndex, gridRows)
                            val pendingApps = displacedApps.toMutableList()
                            displacedApps.clear()
                            appChunks.add(buildList {
                                repeat(appPageSize) { slot ->
                                    val sourceApp = sourceSlots.getOrNull(slot)
                                    when {
                                        slot in blockedSlots -> {
                                            if (sourceApp != null) pendingApps += sourceApp
                                            add(null)
                                        }
                                        pendingApps.isNotEmpty() -> {
                                            add(pendingApps.removeAt(0))
                                            if (sourceApp != null) pendingApps += sourceApp
                                        }
                                        else -> add(sourceApp)
                                    }
                                }
                            })
                            displacedApps.addAll(pendingApps)
                        } else {
                            appChunks += sourceSlots + List(appPageSize - sourceSlots.size) { null }
                        }
                        pageRows += gridRows
                        pageIndex++
                    }
                    while (appChunks.size > 1) {
                        val lastPageIndex = appChunks.lastIndex
                        val lastPageIsTemporary = lastPageIndex == dragCreatedWidgetPageIndex ||
                            (dragCreatedEmptyAppPage &&
                                lastPageIndex == appItemCount / appPageSize)
                        if (appChunks.last().any { it != null } ||
                            homeWidgets.any { it.pageIndex == lastPageIndex } ||
                            lastPageIsTemporary
                        ) {
                            break
                        }
                        appChunks.removeAt(lastPageIndex)
                        pageRows.removeAt(lastPageIndex)
                    }
                    if (reflowAroundWidgets && !widgetLayoutRetryPosted) {
                        val previousGeometry = widgetGridGeometryKey(pages)
                        widgetLayoutRetryPosted = true
                        pages.post {
                            widgetLayoutRetryPosted = false
                            if (currentScreen == Screen.APPS && homeWidgets.isNotEmpty() &&
                                previousGeometry != widgetGridGeometryKey(pages)
                            ) {
                                installedApps?.let { renderApps?.invoke(it) }
                            }
                        }
                    }
                    appGridPackages = MutableList<String?>(appChunks.size * appPageSize) { null }.also { packages ->
                        appChunks.forEachIndexed { page, pageApps ->
                            pageApps.forEachIndexed { position, app ->
                                packages[page * appPageSize + position] = app?.packageName
                            }
                        }
                    }
                    if (!reflowAroundWidgets && appGridOrderPackages != appGridPackages) {
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
                                pageRows[pageIndex],
                                selectedAppPackages,
                                { isAppSelectionMode },
                                { !it.packageName.startsWith(FOLDER_PACKAGE_PREFIX) },
                                onEmptySlotClick = {
                                    if (isAppSelectionMode) exitAppSelectionMode()
                                },
                                selectionBarHeight = selectionBar.layoutParams.height,
                                onEmptySlotLongClick = {
                                    isHomeEditMode = true
                                    installedApps?.let { renderApps?.invoke(it) }
                                }
                            ).apply {
                                setGridApps(pageApps)
                            }
                        }
                        gridView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            if (isAppSelectionMode || isHomeEditMode) refreshSelectionWiggle()
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
                    editControls.visibility = if (isHomeEditMode) View.VISIBLE else View.GONE
                    selectionCount.text = getString(
                        R.string.selected_apps,
                        selectedAppPackages.size
                    )

                    dock.removeAllViews()
                    dock.clipChildren = false
                    dock.clipToPadding = false
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
                            id = R.id.app_icon
                            setImageDrawable(AppIconUtils.getIcon(this@MainActivity, app))
                            contentDescription = app.name
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(dp(6), dp(4), dp(6), dp(4))
                        }
                        val hoverGlow = View(this@MainActivity).apply {
                            id = R.id.app_hover_glow
                            visibility = View.GONE
                            background = createHoverGlowDrawable()
                        }
                        val iconContainer = FrameLayout(this).apply {
                            tag = app.packageName
                            clipChildren = false
                            clipToPadding = false
                            addView(
                                icon,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            addView(
                                hoverGlow,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                ).apply {
                                    setMargins(dp(4), dp(2), dp(4), dp(2))
                                }
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
                                if (isAppSelectionMode || isHomeEditMode ||
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
                view.findViewById<View>(R.id.apps_edit_button).setOnClickListener {
                    showHomeEditMenu(it)
                }
                view.findViewById<View>(R.id.apps_edit_done).setOnClickListener {
                    finishHomeEditMode()
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
        LauncherConfig.pageTransitionEffect(this, defaultHomeActive)

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
        setWidgetSwipeProgress(
            transition.direction,
            transition.width,
            transition.progress
        )
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
        animateWidgetSwipe(
            transition.direction,
            transition.width,
            transition.progress,
            targetProgress,
            duration
        )
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
            resetWidgetSwipe()
            updateAppPageIndicator()
            appPageSwipeTransition = null
            homeSwipeSettling = false
            refreshSelectionWiggle()
        }

    }

    private fun setWidgetSwipeProgress(direction: Int, width: Float, progress: Float) {
        appsWidgetContainer?.translationX = direction * width * progress
    }

    private fun animateWidgetSwipe(
        direction: Int,
        width: Float,
        startProgress: Float,
        endProgress: Float,
        duration: Long
    ) {
        val container = appsWidgetContainer ?: return
        widgetSwipeAnimator?.cancel()
        widgetSwipeAnimator = ValueAnimator.ofFloat(startProgress, endProgress).apply {
            this.duration = duration
            interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener { animator ->
                container.translationX =
                    direction * width * (animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun resetWidgetSwipe() {
        widgetSwipeAnimator?.cancel()
        widgetSwipeAnimator = null
        appsWidgetContainer?.translationX = 0f
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
        animateWidgetSwipe(direction, width, 0f, 1f, duration)

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
            resetWidgetSwipe()
            updateAppPageIndicator()
            homeSwipeSettling = false
            refreshSelectionWiggle()
        }
    }

    private fun updateAppPageIndicator() {
        appsIndicator?.let { indicator ->
            for (index in 0 until indicator.childCount) {
                val dot = indicator.getChildAt(index) as? TextView ?: continue
                val selected = index == appPageIndex
                dot.alpha = if (selected) 1f else 0.45f
                dot.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF9DB7D0.toInt())
            }
        }
        renderHomeWidgetViews()
    }

    private sealed interface WidgetPickerItem {
        data class LauncherWidget(val type: LauncherWidgetType) : WidgetPickerItem
        data class AppShortcut(val app: AppInfo) : WidgetPickerItem
    }

    private inner class WidgetPickerAdapter(
        apps: List<AppInfo>
    ) : BaseAdapter() {
        private val launcherWidgets = LauncherWidgetType.entries.map(WidgetPickerItem::LauncherWidget)
        private val launcherWidgetPreviews = mutableMapOf<LauncherWidgetType, Drawable>()
        private var allItems: List<WidgetPickerItem> =
            launcherWidgets + apps.map(WidgetPickerItem::AppShortcut)
        private var visibleItems = allItems

        fun setApps(apps: List<AppInfo>) {
            allItems = launcherWidgets + apps.map(WidgetPickerItem::AppShortcut)
            filter(currentQuery)
        }

        private var currentQuery = ""

        fun filter(query: String) {
            currentQuery = query
            visibleItems = if (query.isBlank()) {
                allItems
            } else {
                allItems.filter { item ->
                    when (item) {
                        is WidgetPickerItem.LauncherWidget ->
                            getString(item.type.labelResId).contains(query.trim(), true)
                        is WidgetPickerItem.AppShortcut ->
                            item.app.name.contains(query.trim(), true)
                    }
                }
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = visibleItems.size

        override fun getItem(position: Int): WidgetPickerItem = visibleItems[position]

        override fun getItemId(position: Int): Long =
            when (val item = getItem(position)) {
                is WidgetPickerItem.LauncherWidget -> item.type.id.hashCode().toLong()
                is WidgetPickerItem.AppShortcut -> item.app.packageName.hashCode().toLong()
            }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = convertView
                ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_widget_picker, parent, false)
            val pickerItem = getItem(position)
            val preview = item.findViewById<ImageView>(R.id.widget_picker_preview)
            val label = when (pickerItem) {
                is WidgetPickerItem.LauncherWidget ->
                    getString(pickerItem.type.labelResId)
                is WidgetPickerItem.AppShortcut -> pickerItem.app.name
            }
            item.findViewById<TextView>(R.id.widget_picker_label).text = label
            item.contentDescription = label
            when (pickerItem) {
                is WidgetPickerItem.LauncherWidget -> {
                    preview.setImageDrawable(
                        launcherWidgetPreviews.getOrPut(pickerItem.type) {
                            createWidgetPreview(pickerItem.type)
                        }
                    )
                    preview.scaleType = ImageView.ScaleType.CENTER_CROP
                    preview.contentDescription = label
                }
                is WidgetPickerItem.AppShortcut -> {
                    preview.setImageDrawable(AppIconUtils.getIcon(this@MainActivity, pickerItem.app))
                    preview.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    preview.contentDescription = label
                }
            }
            return item
        }

        private fun createWidgetPreview(type: LauncherWidgetType): Drawable {
            val preview = LauncherWidgetFactory.create(this@MainActivity, type)
            val width = dp(320)
            val height = dp(220)
            preview.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            preview.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            preview.draw(Canvas(bitmap))
            return BitmapDrawable(resources, bitmap)
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
            HiddenAppsPreferences.hide(this, app.packageName)
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
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                uninstallAppLauncher.launch(intent)
            } catch (exception: ActivityNotFoundException) {
                Log.w(TAG, "No package uninstaller is available for ${app.packageName}", exception)
                Toast.makeText(this, R.string.cant_remove_app, Toast.LENGTH_SHORT).show()
            } catch (exception: SecurityException) {
                Log.w(TAG, "Not allowed to uninstall ${app.packageName}", exception)
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

    private fun showHomeEditMenu(anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_home_edit, null)
        val popupWindow = PopupWindow(
            popupView,
            dp(228),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(0x00000000))
            isOutsideTouchable = true
            elevation = dp(18).toFloat()
        }
        popupView.findViewById<View>(R.id.home_edit_add_widget).setOnClickListener {
            popupWindow.dismiss()
            showHomeWidgetPicker()
        }
        popupView.findViewById<View>(R.id.home_edit_customize).setOnClickListener {
            popupWindow.dismiss()
            startOwnScreen(ScreenGridActivity::class.java)
        }
        popupView.findViewById<View>(R.id.home_edit_wallpaper).setOnClickListener {
            popupWindow.dismiss()
            startOwnScreen(WallpaperActivity::class.java)
        }
        popupWindow.showAsDropDown(anchor, 0, dp(4))
    }

    private fun showHomeWidgetPicker() {
        val pickerView = LayoutInflater.from(this).inflate(R.layout.widget_picker_sheet, null)
        val grid = pickerView.findViewById<GridView>(R.id.widget_picker_grid)
        val emptyMessage = pickerView.findViewById<TextView>(R.id.widget_picker_empty)
        val adapter = WidgetPickerAdapter(emptyList())
        grid.adapter = adapter
        emptyMessage.visibility = View.GONE

        val dialog = Dialog(this).apply {
            setContentView(pickerView)
            setCancelable(true)
            setOnDismissListener {
                widgetPickerDialog = null
            }
        }
        widgetPickerDialog = dialog
        grid.setOnItemClickListener { _, _, position, _ ->
            when (val item = adapter.getItem(position)) {
                is WidgetPickerItem.LauncherWidget -> {
                    dialog.dismiss()
                    addLauncherWidget(item.type, appPageIndex)
                }
                is WidgetPickerItem.AppShortcut -> {
                    dialog.dismiss()
                    launchHomeScreenApp(item.app.packageName)
                }
            }
        }
        requestInstalledApps { apps ->
            if (widgetPickerDialog === dialog) {
                adapter.setApps(apps.filterNot { it.packageName in hiddenAppPackages })
                val hasItems = adapter.count > 0
                emptyMessage.visibility = if (hasItems) View.GONE else View.VISIBLE
                grid.visibility = if (hasItems) View.VISIBLE else View.GONE
            }
        }
        pickerView.findViewById<EditText>(R.id.widget_picker_search)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    adapter.filter(text?.toString().orEmpty())
                    val hasItems = adapter.count > 0
                    emptyMessage.visibility = if (hasItems) View.GONE else View.VISIBLE
                    grid.visibility = if (hasItems) View.VISIBLE else View.GONE
                }

                override fun afterTextChanged(text: Editable?) = Unit
            })
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0x00000000))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.82f).toInt()
            )
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun requestAddHomeWidget(provider: AppWidgetProviderInfo) {
        if (pendingWidgetId != null) return
        val widgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = widgetId
        pendingWidgetPage = findWidgetPage(appPageIndex)
        val widgetWidthDp = appScreenWidgetWidthDp()
        val widgetHeightDp = LAUNCHER_WIDGET_HEIGHT_DP
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widgetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widgetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, widgetHeightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, widgetHeightDp)
        }
        if (appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider.provider, options)) {
            configureOrAddWidget(widgetId, provider)
            return
        }

        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
        }
        try {
            widgetBindLauncher.launch(bindIntent)
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Unable to request widget binding", exception)
            discardPendingWidget(widgetId)
            Toast.makeText(this, R.string.home_widget_bind_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureOrAddWidget(widgetId: Int, provider: AppWidgetProviderInfo) {
        val configureActivity = provider.configure
        if (configureActivity == null) {
            pendingWidgetId = null
            addHomeWidget(widgetId, pendingWidgetPage)
            return
        }
        val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configureActivity
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        try {
            widgetConfigureLauncher.launch(configureIntent)
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Unable to launch widget configuration", exception)
            discardPendingWidget(widgetId)
            Toast.makeText(this, R.string.home_widget_bind_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addHomeWidget(widgetId: Int, pageIndex: Int) {
        homeWidgets.removeAll {
            it.launcherWidgetType == null && it.appWidgetId == widgetId
        }
        homeWidgets += HomeWidgetPlacement(widgetId, pageIndex)
        layoutRepository.saveHomeWidgets(homeWidgets)
        showAddedWidgetPage(pageIndex)
    }

    private fun addLauncherWidget(type: LauncherWidgetType, pageIndex: Int) {
        val targetPage = findWidgetPage(pageIndex)
        homeWidgets += HomeWidgetPlacement(
            appWidgetId = -1,
            pageIndex = targetPage,
            launcherWidgetType = type
        )
        layoutRepository.saveHomeWidgets(homeWidgets)
        showAddedWidgetPage(targetPage)
    }

    private fun findWidgetPage(pageIndex: Int): Int = pageIndex.coerceAtLeast(0)

    private fun launchWeatherSettings(pageIndex: Int) {
        returningFromOwnScreen = true
        weatherSettingsLauncher.launch(
            Intent(this, WeatherSettingsActivity::class.java).putExtra(
                WeatherSettingsActivity.EXTRA_PAGE_INDEX,
                pageIndex
            )
        )
    }

    private fun scheduleWeatherRefresh() {
        weatherRefreshHandler.removeCallbacks(weatherRefreshRunnable)
        if (
            currentScreen != Screen.APPS ||
            homeWidgets.none {
                it.launcherWidgetType == LauncherWidgetType.WEATHER &&
                    it.pageIndex == appPageIndex
            }
        ) return
        val intervalMillis =
            WeatherWidgetPreferences.refreshIntervalHours(this) * 60L * 60L * 1000L
        val nextRefreshAt = WeatherWidgetPreferences.lastRefreshTime(this) + intervalMillis
        val delayMillis = (nextRefreshAt - System.currentTimeMillis()).coerceAtLeast(0L)
        weatherRefreshHandler.postDelayed(
            weatherRefreshRunnable,
            delayMillis
        )
    }

    private fun appScreenWidgetWidthPx(
        container: FrameLayout? = appsWidgetContainer,
        placement: HomeWidgetPlacement? = null
    ): Int {
        val containerWidth = container?.width?.takeIf { it > 0 }
            ?: screenContainer.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val horizontalPadding = container?.let { it.paddingLeft + it.paddingRight }
            ?.takeIf { it > 0 } ?: dp(WIDGET_SIDE_INSET_DP * 2)
        val availableWidth = (containerWidth - horizontalPadding).coerceAtLeast(1)
        return if (placement?.launcherWidgetType == LauncherWidgetType.WEATHER) {
            availableWidth
        } else {
            ((availableWidth - dp(WIDGET_HORIZONTAL_GAP_DP)) / 2).coerceAtLeast(1)
        }
    }

    private fun appScreenWidgetWidthDp(
        container: FrameLayout? = appsWidgetContainer,
        placement: HomeWidgetPlacement? = null
    ): Int =
        (appScreenWidgetWidthPx(container, placement) / resources.displayMetrics.density)
            .roundToInt().coerceAtLeast(1)

    private fun widgetPositionMaxima(
        container: FrameLayout,
        widgetWidth: Int,
        widgetHeight: Int
    ): Pair<Int, Int> {
        val width = container.width.takeIf { it > 0 }
            ?: screenContainer.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val height = container.height.takeIf { it > 0 }
            ?: screenContainer.height.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        val containerLocation = IntArray(2)
        val pagesLocation = IntArray(2)
        container.getLocationOnScreen(containerLocation)
        val pages = appsPages?.takeIf { it.height > 0 }
        val maxWidgetBottom = if (pages != null) {
            pages.getLocationOnScreen(pagesLocation)
            pagesLocation[1] + pages.height
        } else {
            containerLocation[1] + height - container.paddingBottom
        }
        val minWidgetTop = containerLocation[1] + container.paddingTop
        val maxTop = (maxWidgetBottom - widgetHeight - minWidgetTop).coerceAtLeast(0)
        return (width - container.paddingLeft - container.paddingRight - widgetWidth)
            .coerceAtLeast(0) to maxTop
    }

    private fun widgetGridGeometryKey(pages: ViewFlipper): String {
        val location = IntArray(2)
        pages.getLocationOnScreen(location)
        return "${location[0]}:${location[1]}:${pages.width}:${pages.height}"
    }

    private fun widgetBlockedAppSlots(pageIndex: Int, gridRows: Int): Set<Int> {
        val container = appsWidgetContainer ?: return emptySet()
        val pages = appsPages ?: return emptySet()
        if (container.width <= 0 || container.height <= 0 ||
            pages.width <= 0 || pages.height <= 0
        ) return emptySet()
        val pageWidgets = homeWidgets.filter { it.pageIndex == pageIndex }
        if (pageWidgets.isEmpty()) return emptySet()

        val widgetHeight = dp(LAUNCHER_WIDGET_HEIGHT_DP).toFloat()
        val containerLocation = IntArray(2)
        val pagesLocation = IntArray(2)
        container.getLocationOnScreen(containerLocation)
        pages.getLocationOnScreen(pagesLocation)
        val columnSpacing = dp(6).toFloat()
        val gridCellWidth = (pages.width - columnSpacing * 3) / 4
        val gridCellHeight = (
            pages.height +
                if (isAppSelectionMode) dp(40) else 0
            ) / gridRows.coerceAtLeast(1)
        val blockedSlots = mutableSetOf<Int>()

        pageWidgets.forEachIndexed { index, placement ->
            val itemWidgetWidth = appScreenWidgetWidthPx(container, placement).toFloat()
            val defaultPosition = defaultWidgetPosition(
                index,
                placement,
                pageWidgets,
                container,
                itemWidgetWidth.toInt(),
                widgetHeight.toInt()
            )
            val (itemMaxLeft, itemMaxTop) = widgetPositionMaxima(
                container,
                itemWidgetWidth.toInt(),
                widgetHeight.toInt()
            )
            val left = containerLocation[0] + container.paddingLeft +
                ((placement.positionX ?: defaultPosition.first).coerceIn(0f, 1f) * itemMaxLeft)
            val top = containerLocation[1] + container.paddingTop +
                ((placement.positionY ?: defaultPosition.second).coerceIn(0f, 1f) * itemMaxTop)
            val widgetBounds = RectF(left, top, left + itemWidgetWidth, top + widgetHeight)

            repeat(gridRows) { row ->
                repeat(4) { column ->
                    val slot = row * 4 + column
                    val cellLeft = pagesLocation[0] + column * (gridCellWidth + columnSpacing)
                    val cellBounds = RectF(
                        cellLeft,
                        pagesLocation[1].toFloat() + row * gridCellHeight,
                        cellLeft + gridCellWidth,
                        pagesLocation[1].toFloat() + (row + 1) * gridCellHeight
                    )
                    if (RectF.intersects(widgetBounds, cellBounds)) blockedSlots += slot
                }
            }
        }
        return blockedSlots
    }

    private fun defaultWidgetPosition(
        index: Int,
        placement: HomeWidgetPlacement,
        pageWidgets: List<HomeWidgetPlacement>,
        container: FrameLayout,
        widgetWidth: Int,
        widgetHeight: Int
    ): Pair<Float, Float> {
        val (maxLeft, maxTop) = widgetPositionMaxima(container, widgetWidth, widgetHeight)
        val weatherWidget = pageWidgets.firstOrNull {
            it.launcherWidgetType == LauncherWidgetType.WEATHER
        }
        val isWeather = placement.launcherWidgetType == LauncherWidgetType.WEATHER
        val nonWeatherIndex = pageWidgets
            .filterNot { it.launcherWidgetType == LauncherWidgetType.WEATHER }
            .indexOf(placement)
            .coerceAtLeast(0)
        val left = when {
            isWeather -> 0
            else -> if (nonWeatherIndex % 2 == 0) 0 else maxLeft
        }
        val top = when {
            isWeather -> dp(WIDGET_DEFAULT_TOP_DP) +
                ((pageWidgets.size - 1).coerceAtLeast(0) + 1) / 2 *
                (widgetHeight + dp(WIDGET_VERTICAL_GAP_DP))
            weatherWidget != null ->
                dp(WIDGET_DEFAULT_TOP_DP) + widgetHeight + dp(WIDGET_VERTICAL_GAP_DP) +
                    (nonWeatherIndex / 2) * (widgetHeight + dp(WIDGET_VERTICAL_GAP_DP))
            else ->
                dp(WIDGET_DEFAULT_TOP_DP) +
                    (index / 2) * (widgetHeight + dp(WIDGET_VERTICAL_GAP_DP))
        }.coerceAtMost(maxTop)
        return (left.toFloat() / maxLeft.coerceAtLeast(1)) to
            (top.toFloat() / maxTop.coerceAtLeast(1))
    }

    private fun normalizeHomeWidgetPlacements(container: FrameLayout?): Boolean {
        val pages = appsPages ?: return false
        if (container == null || container.width <= 0 || container.height <= 0 ||
            pages.width <= 0 || pages.height <= 0
        ) return false

        val widgetHeight = dp(LAUNCHER_WIDGET_HEIGHT_DP)
        val gap = dp(WIDGET_VERTICAL_GAP_DP)
        val occupiedByPage = mutableMapOf<Int, MutableList<RectF>>()
        val originalWidgetsByPage = homeWidgets.groupBy { it.pageIndex }
        var changed = false

        homeWidgets.indices.forEach { index ->
            val placement = homeWidgets[index]
            val originalPage = placement.pageIndex
            val originalPageWidgets = originalWidgetsByPage[originalPage].orEmpty()
            val originalIndex = originalPageWidgets.indexOf(placement).coerceAtLeast(0)
            val width = appScreenWidgetWidthPx(container, placement)
            val (maxLeft, maxTop) = widgetPositionMaxima(container, width, widgetHeight)
            val defaultPosition = defaultWidgetPosition(
                originalIndex,
                placement,
                originalPageWidgets,
                container,
                width,
                widgetHeight
            )
            val preferredX = (placement.positionX ?: defaultPosition.first).coerceIn(0f, 1f)
            val preferredY = (placement.positionY ?: defaultPosition.second).coerceIn(0f, 1f)
            val preferredLeft = (preferredX * maxLeft).roundToInt()
            val preferredTop = (preferredY * maxTop).roundToInt()
            val isFullWidth = placement.launcherWidgetType == LauncherWidgetType.WEATHER
            val candidateLefts = if (isFullWidth || maxLeft == 0) {
                listOf(0)
            } else {
                listOf(0, maxLeft)
            }
            val candidateTops = buildList {
                var top = 0
                while (top < maxTop) {
                    add(top)
                    top += (widgetHeight + gap).coerceAtLeast(1)
                }
                if (isEmpty() || last() != maxTop) add(maxTop)
            }

            var resolvedPage = originalPage
            var resolvedPosition: Pair<Int, Int>? = null
            while (resolvedPosition == null) {
                val occupied = occupiedByPage.getOrPut(resolvedPage) { mutableListOf() }
                val preferredBounds = RectF(
                    preferredLeft.toFloat(),
                    preferredTop.toFloat(),
                    (preferredLeft + width).toFloat(),
                    (preferredTop + widgetHeight).toFloat()
                )
                if (resolvedPage == originalPage &&
                    occupied.none { widgetBoundsOverlap(preferredBounds, it, gap) }
                ) {
                    resolvedPosition = preferredLeft to preferredTop
                } else {
                    val candidates = candidateTops.flatMap { top ->
                        candidateLefts.map { left ->
                            val bounds = RectF(
                                left.toFloat(),
                                top.toFloat(),
                                (left + width).toFloat(),
                                (top + widgetHeight).toFloat()
                            )
                            Triple(left, top, bounds)
                        }
                    }.sortedBy { (left, top, _) ->
                        val dx = (left - preferredLeft).toLong()
                        val dy = (top - preferredTop).toLong()
                        dx * dx + dy * dy
                    }
                    resolvedPosition = candidates.firstOrNull { (_, _, bounds) ->
                        occupied.none { widgetBoundsOverlap(bounds, it, gap) }
                    }?.let { (left, top, _) -> left to top }
                }
                if (resolvedPosition == null) resolvedPage++
            }

            val (left, top) = resolvedPosition
            occupiedByPage.getOrPut(resolvedPage) { mutableListOf() } += RectF(
                left.toFloat(),
                top.toFloat(),
                (left + width).toFloat(),
                (top + widgetHeight).toFloat()
            )
            val resolvedPlacement = placement.copy(
                pageIndex = resolvedPage,
                positionX = left.toFloat() / maxLeft.coerceAtLeast(1),
                positionY = top.toFloat() / maxTop.coerceAtLeast(1)
            )
            if (resolvedPlacement != placement) {
                homeWidgets[index] = resolvedPlacement
                changed = true
            }
        }

        if (changed) layoutRepository.saveHomeWidgets(homeWidgets)
        return changed
    }

    private fun widgetBoundsOverlap(first: RectF, second: RectF, gap: Int): Boolean =
        first.left < second.right + gap &&
            first.right + gap > second.left &&
            first.top < second.bottom + gap &&
            first.bottom + gap > second.top

    private fun showAddedWidgetPage(pageIndex: Int) {
        normalizeHomeWidgetPlacements(appsWidgetContainer)
        appPageIndex = homeWidgets.lastOrNull()?.pageIndex ?: pageIndex
        installedApps?.let { renderApps?.invoke(it) } ?: renderHomeWidgetViews()
    }

    private fun discardPendingWidget(widgetId: Int) {
        pendingWidgetId = null
        appWidgetHost.deleteAppWidgetId(widgetId)
    }

    private fun renderHomeWidgetViews() {
        val container = appsWidgetContainer ?: return
        val pages = appsPages
        if (container.width <= 0 || container.height <= 0 ||
            pages == null || pages.width <= 0 || pages.height <= 0
        ) {
            container.post {
                if (currentScreen == Screen.APPS &&
                    container.width > 0 && container.height > 0 &&
                    appsPages?.width?.let { it > 0 } == true &&
                    appsPages?.height?.let { it > 0 } == true
                ) renderHomeWidgetViews()
            }
            return
        }
        if (normalizeHomeWidgetPlacements(container) && appsGridRendered) {
            installedApps?.let { renderApps?.invoke(it) }
            return
        }
        for (index in 0 until container.childCount) {
            stopSelectionWiggle(container.getChildAt(index))
        }
        container.removeAllViews()
        val pageWidgets = homeWidgets.filter { it.pageIndex == appPageIndex }
        container.visibility = View.VISIBLE
        val missingWidgets = mutableListOf<HomeWidgetPlacement>()
        var savedDefaultPositions = false
        val widgetHeight = dp(LAUNCHER_WIDGET_HEIGHT_DP)
        pageWidgets.forEachIndexed { index, placement ->
            val widgetWidth = appScreenWidgetWidthPx(container, placement)
            val (maxLeft, maxTop) = widgetPositionMaxima(container, widgetWidth, widgetHeight)
            val defaultPosition = defaultWidgetPosition(
                index,
                placement,
                pageWidgets,
                container,
                widgetWidth,
                widgetHeight
            )
            val positionX = placement.positionX ?: defaultPosition.first
            val positionY = placement.positionY ?: defaultPosition.second
            val positionedPlacement = if (
                placement.positionX == null || placement.positionY == null
            ) {
                placement.copy(positionX = positionX, positionY = positionY).also { updated ->
                    val storedIndex = homeWidgets.indexOf(placement)
                    if (storedIndex >= 0) homeWidgets[storedIndex] = updated
                    savedDefaultPositions = true
                }
            } else {
                placement
            }
            val widgetContent: View
            val launcherWidgetType = positionedPlacement.launcherWidgetType
            if (launcherWidgetType != null) {
                if (launcherWidgetType == LauncherWidgetType.WEATHER) {
                    WeatherWidgetPreferences.recordRefresh(this)
                }
                widgetContent = LauncherWidgetFactory.create(this, launcherWidgetType) {
                    if (launcherWidgetType == LauncherWidgetType.WEATHER) {
                        launchWeatherSettings(positionedPlacement.pageIndex)
                    }
                }
            } else {
                val provider = appWidgetManager.getAppWidgetInfo(positionedPlacement.appWidgetId)
                if (provider == null) {
                    Log.w(TAG, "Removing unavailable widget ${positionedPlacement.appWidgetId}")
                    missingWidgets += positionedPlacement
                    appWidgetViews.remove(positionedPlacement.appWidgetId)
                    appWidgetHost.deleteAppWidgetId(positionedPlacement.appWidgetId)
                    return@forEachIndexed
                }

                val widgetWidthDp = appScreenWidgetWidthDp(container, positionedPlacement)
                val widgetHeightDp = LAUNCHER_WIDGET_HEIGHT_DP
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widgetWidthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widgetWidthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, widgetHeightDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, widgetHeightDp)
                }
                appWidgetManager.updateAppWidgetOptions(positionedPlacement.appWidgetId, options)
                widgetContent = appWidgetViews.getOrPut(positionedPlacement.appWidgetId) {
                    appWidgetHost.createView(this, positionedPlacement.appWidgetId, provider)
                }
            }
            (widgetContent.parent as? ViewGroup)?.removeView(widgetContent)
            val widgetFrame = FrameLayout(this).apply {
                tag = positionedPlacement
                background = if (
                    positionedPlacement.launcherWidgetType == LauncherWidgetType.WEATHER
                ) null else getDrawable(R.drawable.bg_glass)
                clipChildren = false
                clipToPadding = false
                addView(
                    widgetContent,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                if (isHomeEditMode) {
                    addView(
                        TextView(this@MainActivity).apply {
                            text = "−"
                            gravity = Gravity.CENTER
                            textSize = 22f
                            setTextColor(0xFFFFFFFF.toInt())
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(0xFFE74C5B.toInt())
                            }
                            elevation = dp(8).toFloat()
                            setOnClickListener {
                                confirmRemoveHomeWidget(positionedPlacement)
                            }
                        },
                        FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.START)
                    )
                }
                setOnLongClickListener {
                    startWidgetDrag(positionedPlacement, this)
                    true
                }
            }
            attachWidgetLongPress(widgetContent, positionedPlacement, widgetFrame)
            container.addView(
                widgetFrame,
                FrameLayout.LayoutParams(
                    appScreenWidgetWidthPx(container, positionedPlacement),
                    widgetHeight,
                    Gravity.TOP or Gravity.LEFT
                ).apply {
                    leftMargin = (positionX * maxLeft).roundToInt()
                    topMargin = (positionY * maxTop).roundToInt()
                }
            )
            if (isHomeEditMode) startSelectionWiggle(widgetFrame)
        }
        if (missingWidgets.isNotEmpty()) {
            homeWidgets.removeAll(missingWidgets.toSet())
            savedDefaultPositions = true
        }
        if (savedDefaultPositions) {
            layoutRepository.saveHomeWidgets(homeWidgets)
        }
        scheduleWeatherRefresh()
    }

    private fun confirmRemoveHomeWidget(placement: HomeWidgetPlacement) {
        AlertDialog.Builder(this)
            .setTitle(R.string.home_widget_remove_title)
            .setMessage(R.string.home_widget_remove_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.home_widget_remove) { _, _ ->
                homeWidgets.removeAll { it == placement }
                layoutRepository.saveHomeWidgets(homeWidgets)
                if (placement.launcherWidgetType == null) {
                    appWidgetViews.remove(placement.appWidgetId)
                    appWidgetHost.deleteAppWidgetId(placement.appWidgetId)
                }
                renderHomeWidgetViews()
            }
            .show()
    }

    private fun exitAppSelectionMode() {
        isAppSelectionMode = false
        selectedAppPackages.clear()
        installedApps?.let { renderApps?.invoke(it) }
    }

    private fun finishHomeEditMode() {
        isHomeEditMode = false
        exitAppSelectionMode()
    }

    private fun startAppDrag(app: AppInfo, source: View) {
        setHoverTarget(null)
        dragShadowCenteredOnPointer = false
        dragStartedInAppGrid = true
        draggedDockSlot = appsDock?.indexOfChild(source)?.takeIf { it >= 0 }
        dragStartedInDock = draggedDockSlot != null
        draggedApp = app
        dragSource = source
        clearDragHoverPreview()
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

    private fun startWidgetDrag(placement: HomeWidgetPlacement, originalSource: View) {
        if (draggedWidgetPlacement != null || draggedApp != null) return
        val placementIndex = homeWidgets.indexOf(placement)
        var activePlacement = placement
        if (!isHomeEditMode) {
            isHomeEditMode = true
            installedApps?.let { renderApps?.invoke(it) }
            activePlacement = homeWidgets.getOrNull(placementIndex) ?: placement
        }
        val source = appsWidgetContainer
            ?.let { container ->
                (0 until container.childCount)
                    .map(container::getChildAt)
                    .firstOrNull { it.tag == activePlacement }
            }
            ?: originalSource
        if (source.width <= 0 || source.height <= 0) return

        val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        source.draw(Canvas(bitmap))
        draggedWidgetPlacement = activePlacement
        draggedWidgetSource = source
        dragSourceVisibility = source.visibility
        source.visibility = View.INVISIBLE
        dragCreatedWidgetPageIndex = null
        dragMoved = false
        dragEdge = 0
        dragShadowCenteredOnPointer = true
        dragShadow = ImageView(this).apply {
            setImageDrawable(BitmapDrawable(resources, bitmap))
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.9f
            elevation = dp(12).toFloat()
        }
        screenContainer.addView(
            dragShadow,
            FrameLayout.LayoutParams(
                source.width,
                source.height
            )
        )
        source.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        val screenLocation = IntArray(2)
        screenContainer.getLocationOnScreen(screenLocation)
        dragLastRawX = screenLocation[0] + downX
        dragLastRawY = screenLocation[1] + downY
        updateDragShadowFromScreen(dragLastRawX, dragLastRawY)
    }

    private fun attachWidgetLongPress(
        view: View,
        placement: HomeWidgetPlacement,
        widgetFrame: View
    ) {
        view.setOnLongClickListener {
            startWidgetDrag(placement, widgetFrame)
            true
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                attachWidgetLongPress(view.getChildAt(index), placement, widgetFrame)
            }
        }
    }

    private fun finishWidgetDrag(commit: Boolean) {
        val placement = draggedWidgetPlacement
        val source = draggedWidgetSource
        val appPositionsBeforeReflow = if (commit) {
            captureVisibleAppPositions(appPageIndex)
        } else {
            emptyMap()
        }
        val dropPosition = if (commit) {
            appsWidgetContainer?.let(::widgetPositionFromDrag)
        } else {
            null
        }
        val targetPage = if (commit && placement != null && appPageIndex != placement.pageIndex) {
            findWidgetPage(appPageIndex)
        } else {
            appPageIndex
        }
        val createdPage = dragCreatedWidgetPageIndex

        cancelDragPageChange()
        dragEdge = 0
        dragCreatedWidgetPageIndex = null
        source?.visibility = dragSourceVisibility
        dragShadow?.let(screenContainer::removeView)
        dragShadow = null
        draggedWidgetSource = null
        draggedWidgetPlacement = null
        dragShadowCenteredOnPointer = false
        if (commit && placement != null) {
            val index = homeWidgets.indexOf(placement)
            if (index >= 0) {
                homeWidgets[index] = placement.copy(
                    pageIndex = targetPage,
                    positionX = dropPosition?.first ?: placement.positionX,
                    positionY = dropPosition?.second ?: placement.positionY
                )
                normalizeHomeWidgetPlacements(appsWidgetContainer)
                layoutRepository.saveHomeWidgets(homeWidgets)
                appPageIndex = homeWidgets[index].pageIndex
            }
        }
        if (!commit && createdPage != null) {
            appPageIndex = appPageIndex.coerceAtMost((appPageCount - 1).coerceAtLeast(0))
        }
        dragMoved = false
        stopDragWiggle()
        installedApps?.let {
            renderApps?.invoke(it)
            if (commit && appPositionsBeforeReflow.isNotEmpty()) {
                animateAppReflow(appPageIndex, appPositionsBeforeReflow)
            }
        } ?: renderHomeWidgetViews()
        refreshSelectionWiggle()
    }

    private fun captureVisibleAppPositions(pageIndex: Int): Map<String, Pair<Float, Float>> {
        val grid = appsPages?.getChildAt(pageIndex) as? GridView ?: return emptyMap()
        return buildMap {
            for (childIndex in 0 until grid.childCount) {
                val app = grid.adapter?.getItem(grid.firstVisiblePosition + childIndex) as? AppInfo
                    ?: continue
                val child = grid.getChildAt(childIndex)
                val location = IntArray(2)
                child.getLocationOnScreen(location)
                put(app.packageName, location[0].toFloat() to location[1].toFloat())
            }
        }
    }

    private fun animateAppReflow(
        pageIndex: Int,
        previousPositions: Map<String, Pair<Float, Float>>
    ) {
        val pages = appsPages ?: return
        pages.post {
            val grid = pages.getChildAt(pageIndex) as? GridView ?: return@post
            for (childIndex in 0 until grid.childCount) {
                val app = grid.adapter?.getItem(grid.firstVisiblePosition + childIndex) as? AppInfo
                    ?: continue
                val previousPosition = previousPositions[app.packageName] ?: continue
                val child = grid.getChildAt(childIndex)
                val currentLocation = IntArray(2)
                child.getLocationOnScreen(currentLocation)
                val offsetX = previousPosition.first - currentLocation[0]
                val offsetY = previousPosition.second - currentLocation[1]
                if (offsetX == 0f && offsetY == 0f) continue
                child.animate().cancel()
                child.translationX = offsetX
                child.translationY = offsetY
                child.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(APP_REORDER_ANIMATION_DURATION_MS)
                    .start()
            }
        }
    }

    private fun widgetPositionFromDrag(container: FrameLayout): Pair<Float, Float>? {
        val shadow = dragShadow ?: return null
        val shadowParams = shadow.layoutParams as? FrameLayout.LayoutParams ?: return null
        if (shadowParams.width <= 0 || shadowParams.height <= 0) return null
        val containerLocation = IntArray(2)
        container.getLocationOnScreen(containerLocation)
        val screenLocation = IntArray(2)
        screenContainer.getLocationOnScreen(screenLocation)
        val (maxLeft, maxTop) = widgetPositionMaxima(
            container,
            shadowParams.width,
            shadowParams.height
        )
        val left = (screenLocation[0] + shadowParams.leftMargin -
            containerLocation[0] - container.paddingLeft)
            .coerceIn(0, maxLeft)
        val top = (screenLocation[1] + shadowParams.topMargin -
            containerLocation[1] - container.paddingTop)
            .coerceIn(0, maxTop)
        return left.toFloat() / maxLeft.coerceAtLeast(1) to top.toFloat() / maxTop.coerceAtLeast(1)
    }

    private fun appPageDragWiggleTargets(pageIndex: Int): List<View> {
        val icons = mutableListOf<View>()
        val grid = appsPages?.getChildAt(pageIndex) as? GridView
        if (grid != null) {
            for (childIndex in 0 until grid.childCount) {
                val adapterPosition = grid.firstVisiblePosition + childIndex
                val app = grid.adapter?.getItem(adapterPosition) as? AppInfo
                if (app != null && !app.isFolderTile()) {
                    grid.getChildAt(childIndex)
                        .findViewById<ImageView>(R.id.app_icon)
                        ?.let(icons::add)
                }
            }
        }
        appsDock?.let { dock ->
            for (index in 0 until dock.childCount) {
                val child = dock.getChildAt(index)
                if ((child.tag as? String)?.startsWith(FOLDER_PACKAGE_PREFIX) == false) {
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
        val shadowWidth = shadow.width.takeIf { it > 0 } ?: layoutParams.width
        val shadowHeight = shadow.height.takeIf { it > 0 } ?: layoutParams.height
        val offsetX = if (dragShadowCenteredOnPointer) shadowWidth / 2f else dp(32).toFloat()
        val offsetY = if (dragShadowCenteredOnPointer) shadowHeight / 2f else dp(32).toFloat()
        val left = (x - offsetX).toInt().coerceAtLeast(0)
        val top = (y - offsetY).toInt().coerceAtLeast(0)
        layoutParams.leftMargin = if (dragShadowCenteredOnPointer) {
            left.coerceAtMost((screenContainer.width - shadowWidth).coerceAtLeast(0))
        } else {
            left
        }
        layoutParams.topMargin = if (dragShadowCenteredOnPointer) {
            top.coerceAtMost((screenContainer.height - shadowHeight).coerceAtLeast(0))
        } else {
            top
        }
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
        if (!isAppSelectionMode && !isHomeEditMode) {
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
                    val app = grid.adapter?.getItem(adapterPosition) as? AppInfo
                    if (app != null && !app.isFolderTile()) {
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
                if ((child.tag as? String)?.startsWith(FOLDER_PACKAGE_PREFIX) == false) {
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

    private fun stopSelectionWiggle(view: View) {
        dragWiggleAnimators.filter { it.target === view }.forEach { animator ->
            animator.cancel()
            view.rotation = 0f
            dragWiggleAnimators.remove(animator)
        }
    }

    private fun AppInfo.isFolderTile(): Boolean =
        packageName.startsWith(FOLDER_PACKAGE_PREFIX)

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

    private var currentHoveredView: View? = null

    private fun createHoverGlowDrawable(): Drawable {
        val radius = dp(15).toFloat()
        val outerGlow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(5), 0x9940C4FF.toInt())
            setColor(0x3500E5FF)
        }
        val innerBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(2), 0xFFFFFFFF.toInt())
            setColor(0x00000000)
        }
        return LayerDrawable(arrayOf(outerGlow, innerBorder))
    }

    private fun findHoverTarget(x: Float, y: Float): View? {
        val dragged = draggedApp ?: folderDragState?.draggedApp ?: return null

        val dock = appsDock
        if (dock != null && currentScreen == Screen.APPS && !isAlphabeticalLibrary && folderDragState == null) {
            for (index in 0 until dock.childCount) {
                val child = dock.getChildAt(index) ?: continue
                val pkg = child.tag as? String ?: continue
                if (pkg == dragged.packageName) continue

                val loc = IntArray(2)
                child.getLocationOnScreen(loc)
                val width = child.width.takeIf { it > 0 } ?: dp(58)
                val height = child.height.takeIf { it > 0 } ?: dp(58)
                val centerX = loc[0] + width / 2f
                val centerY = loc[1] + height / 2f
                val dx = x - centerX
                val dy = y - centerY
                val radius = max(dp(28), (width * 0.55f).toInt()).toFloat()

                val insideBox = x >= loc[0] && x <= loc[0] + width &&
                    y >= loc[1] && y <= loc[1] + height
                if (insideBox || (dx * dx + dy * dy <= radius * radius)) {
                    return child
                }
            }
        }

        val grid = appsPages?.getChildAt(appPageIndex) as? GridView
        if (grid != null && currentScreen == Screen.APPS && !isAlphabeticalLibrary && folderDragState == null) {
            for (childIndex in 0 until grid.childCount) {
                val child = grid.getChildAt(childIndex) ?: continue
                if (child.visibility != View.VISIBLE) continue
                val position = grid.firstVisiblePosition + childIndex
                val app = grid.adapter?.getItem(position) as? AppInfo ?: continue
                if (app.packageName == dragged.packageName) continue

                val iconFrame = child.findViewById<View>(R.id.app_icon_frame)
                    ?: child.findViewById<View>(R.id.app_icon)
                    ?: child
                val loc = IntArray(2)
                iconFrame.getLocationOnScreen(loc)
                val width = iconFrame.width.takeIf { it > 0 } ?: dp(58)
                val height = iconFrame.height.takeIf { it > 0 } ?: dp(58)
                val centerX = loc[0] + width / 2f
                val centerY = loc[1] + height / 2f
                val dx = x - centerX
                val dy = y - centerY
                val radius = max(dp(28), (width * 0.55f).toInt()).toFloat()

                val insideBox = x >= loc[0] && x <= loc[0] + width &&
                    y >= loc[1] && y <= loc[1] + height
                if (insideBox || (dx * dx + dy * dy <= radius * radius)) {
                    return child
                }
            }
        }

        val folderGrid = folderDragState?.grid
        if (folderGrid != null) {
            for (childIndex in 0 until folderGrid.childCount) {
                val child = folderGrid.getChildAt(childIndex) ?: continue
                if (child.visibility != View.VISIBLE) continue
                val position = folderGrid.firstVisiblePosition + childIndex
                val app = folderDragState?.adapter?.getItem(position) ?: continue
                if (app.packageName == dragged.packageName) continue

                val iconFrame = child.findViewById<View>(R.id.app_icon_frame)
                    ?: child.findViewById<View>(R.id.app_icon)
                    ?: child
                val loc = IntArray(2)
                iconFrame.getLocationOnScreen(loc)
                val width = iconFrame.width.takeIf { it > 0 } ?: dp(58)
                val height = iconFrame.height.takeIf { it > 0 } ?: dp(58)
                val centerX = loc[0] + width / 2f
                val centerY = loc[1] + height / 2f
                val dx = x - centerX
                val dy = y - centerY
                val radius = max(dp(28), (width * 0.55f).toInt()).toFloat()

                val insideBox = x >= loc[0] && x <= loc[0] + width &&
                    y >= loc[1] && y <= loc[1] + height
                if (insideBox || (dx * dx + dy * dy <= radius * radius)) {
                    return child
                }
            }
        }

        return null
    }

    private fun setHoverTarget(target: View?) {
        if (currentHoveredView === target) return
        currentHoveredView?.let { setIconHoverGlow(it, false) }
        currentHoveredView = target
        currentHoveredView?.let { setIconHoverGlow(it, true) }
    }

    private fun setIconHoverGlow(targetView: View, isHovered: Boolean) {
        val iconFrame = targetView.findViewById<View>(R.id.app_icon_frame)
            ?: (targetView as? FrameLayout)
            ?: (targetView.findViewById<View>(R.id.app_icon)?.parent as? View)
            ?: targetView

        var glowView = targetView.findViewById<View>(R.id.app_hover_glow)
        if (glowView == null && isHovered) {
            (iconFrame as? ViewGroup)?.let { container ->
                glowView = View(this).apply {
                    id = R.id.app_hover_glow
                    background = createHoverGlowDrawable()
                    visibility = View.GONE
                    container.addView(
                        this,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }

        if (isHovered) {
            glowView?.let { g ->
                if (g.background == null) {
                    g.background = createHoverGlowDrawable()
                }
                g.animate().cancel()
                g.visibility = View.VISIBLE
                g.alpha = 0f
                g.scaleX = 0.94f
                g.scaleY = 0.94f
                g.animate()
                    .alpha(1f)
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .setDuration(150L)
                    .start()
            }
            iconFrame.animate().cancel()
            iconFrame.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(150L)
                .start()
        } else {
            glowView?.let { g ->
                g.animate().cancel()
                g.animate()
                    .alpha(0f)
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .setDuration(120L)
                    .withEndAction { g.visibility = View.GONE }
                    .start()
            }
            iconFrame.animate().cancel()
            iconFrame.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120L)
                .start()
        }
    }

    private fun updateDragHoverPreview(x: Float, y: Float) {
        val hoverTarget = findHoverTarget(x, y)
        if (hoverTarget != null) {
            setHoverTarget(hoverTarget)
            clearDragHoverPreview()
            return
        }
        setHoverTarget(null)

        val grid = appsPages?.getChildAt(appPageIndex) as? GridView
        val app = draggedApp
        if (grid == null || app == null || !dragStartedInAppGrid) {
            clearDragHoverPreview()
            return
        }

        var nearestTarget: View? = null
        var nearestDistanceSquared = Float.MAX_VALUE
        for (childIndex in 0 until grid.childCount) {
            val position = grid.firstVisiblePosition + childIndex
            val item = grid.adapter?.getItem(position) as? AppInfo ?: continue
            if (item.packageName == app.packageName) continue

            val child = grid.getChildAt(childIndex)
            val location = IntArray(2)
            child.getLocationOnScreen(location)
            val dx = x - (location[0] + child.width / 2f)
            val dy = y - (location[1] + child.height / 2f)
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared
                nearestTarget = child
            }
        }

        val target = nearestTarget
        val threshold = max(dp(72), target?.let { max(it.width, it.height) } ?: 0) * 0.8f
        if (target == null || nearestDistanceSquared > threshold * threshold) {
            clearDragHoverPreview()
            return
        }

        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val dropAfterTarget = x >= location[0] + target.width / 2f
        val position = grid.firstVisiblePosition +
            (0 until grid.childCount).first { grid.getChildAt(it) === target }
        if (position == dragPreviewPosition && dropAfterTarget == dragPreviewDropAfter) return

        val reorderedPackages = AppGridOrder.moveApp(
            packages = appGridPackages,
            packageName = app.packageName,
            pageIndex = appPageIndex,
            pagePosition = position,
            pageSize = appGridPageSize,
            dropAfterTarget = dropAfterTarget
        )
        if (reorderedPackages == null) {
            clearDragHoverPreview()
            return
        }

        clearDragHoverPreview()
        dragPreviewPosition = position
        dragPreviewDropAfter = dropAfterTarget
        dragPreviewReorderedPackages = reorderedPackages
        for (childIndex in 0 until grid.childCount) {
            val child = grid.getChildAt(childIndex)
            val childPosition = grid.firstVisiblePosition + childIndex
            val item = grid.adapter?.getItem(childPosition) as? AppInfo ?: continue
            if (item.packageName == app.packageName) continue

            val destination = reorderedPackages.indexOf(item.packageName) -
                appPageIndex * appGridPageSize
            val destinationChild = grid.getChildAt(destination - grid.firstVisiblePosition)
                ?: continue
            val offsetX = destinationChild.left - child.left
            val offsetY = destinationChild.top - child.top
            if (offsetX == 0 && offsetY == 0) continue
            child.animate().cancel()
            child.animate()
                .translationX(offsetX.toFloat())
                .translationY(offsetY.toFloat())
                .setDuration(160L)
                .start()
            dragPreviewViews += child
        }
    }

    private fun clearDragHoverPreview() {
        dragPreviewViews.forEach { view ->
            view.animate().cancel()
            view.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(120L)
                .start()
        }
        dragPreviewViews.clear()
        dragPreviewPosition = null
        dragPreviewDropAfter = false
        dragPreviewReorderedPackages = null
    }

    private fun navigateAppGridDuringDrag(x: Float) {
        if ((!dragStartedInAppGrid && draggedWidgetPlacement == null) ||
            currentScreen != Screen.APPS || isAlphabeticalLibrary
        ) return
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
            if ((draggedApp == null && draggedWidgetPlacement == null) || dragEdge != edge ||
                currentScreen != Screen.APPS || isAlphabeticalLibrary
            ) {
                return@Runnable
            }
            val targetPage = appPageIndex + if (edge > 0) 1 else -1
            if (targetPage >= appPageCount && edge > 0 && draggedWidgetPlacement != null &&
                dragCreatedWidgetPageIndex == null
            ) {
                dragCreatedWidgetPageIndex = appPageCount
                installedApps?.let { renderApps?.invoke(it) }
            } else if (targetPage >= appPageCount && edge > 0 && !dragCreatedEmptyAppPage) {
                createAppGridPageDuringDrag()
            } else if (targetPage !in 0 until appPageCount) {
                return@Runnable
            }

            setHoverTarget(null)
            showAppPage(targetPage)
            if (draggedApp != null) startAppPageDragWiggleWhenReady(targetPage)
            updateAppPageIndicator()
            updateDragTargetLabel()
            updateDragShadowFromScreen(dragLastRawX, dragLastRawY)
            dragShadow?.let(screenContainer::bringChildToFront)
            dragTargetLabel?.let(screenContainer::bringChildToFront)
            if (appPageIndex < appPageCount - 1 ||
                (draggedWidgetPlacement != null && dragCreatedWidgetPageIndex == null)
            ) {
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
        val geometricGridPosition = if (insideGrid) {
            grid?.pointToPosition(
                (x - gridLocation[0]).toInt(),
                (y - gridLocation[1]).toInt()
            ) ?: GridView.INVALID_POSITION
        } else {
            GridView.INVALID_POSITION
        }
        val previewHitPosition = if (dragPreviewPosition != null && grid != null) {
            (0 until grid.childCount).firstNotNullOfOrNull { childIndex ->
                val child = grid.getChildAt(childIndex)
                if (child.visibility != View.VISIBLE) return@firstNotNullOfOrNull null
                val childLocation = IntArray(2)
                child.getLocationOnScreen(childLocation)
                val hit = x >= childLocation[0] && x <= childLocation[0] + child.width &&
                    y >= childLocation[1] && y <= childLocation[1] + child.height
                if (hit) grid.firstVisiblePosition + childIndex else null
            }
        } else {
            null
        }
        val gridPosition = previewHitPosition ?: geometricGridPosition
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
        val gridPackage = if (dragPreviewPosition != null) {
            previewHitPosition?.let { position ->
                appGridPackages.getOrNull(appPageIndex * appGridPageSize + position)
            }
        } else if (gridPosition != GridView.INVALID_POSITION) {
            appGridPackages.getOrNull(appPageIndex * appGridPageSize + gridPosition)
        } else {
            null
        }

        val previewReordered = dragPreviewReorderedPackages
        if (currentHoveredView == null && insideGrid && previewReordered != null) {
            dragPreviewViews.forEach { view ->
                view.animate().cancel()
                view.translationX = 0f
                view.translationY = 0f
            }
            dragPreviewViews.clear()
            dragPreviewPosition = null
            dragPreviewDropAfter = false
            dragPreviewReorderedPackages = null

            if (dragStartedInDock) {
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
            }
            appGridPackages = previewReordered
            appGridOrderPackages.clear()
            appGridOrderPackages.addAll(previewReordered)
            saveAppGridOrder()
            installedApps?.let { renderApps?.invoke(it) }
            return true
        }

        val hoveredView = currentHoveredView
        val hoveredDockIndex = hoveredView?.let { appsDock?.indexOfChild(it) }?.takeIf { it >= 0 }
        val hoveredDockPackage = hoveredView?.tag as? String
        val hoveredGridPosition = if (hoveredDockIndex == null && hoveredView != null && grid != null) {
            (0 until grid.childCount).firstOrNull { grid.getChildAt(it) === hoveredView }?.let {
                grid.firstVisiblePosition + it
            }
        } else {
            null
        }
        val hoveredGridPackage = hoveredGridPosition?.let { pos ->
            appGridPackages.getOrNull(appPageIndex * appGridPageSize + pos)
        }
        val hoveredPackage = hoveredDockPackage ?: hoveredGridPackage
        val effectiveDockTargetIndex = hoveredDockIndex ?: dockTargetIndex
        val targetPackage = hoveredPackage ?: dockPackage
        val effectiveGridPosition = hoveredGridPosition ?: gridPosition

        if (effectiveDockTargetIndex != null && targetPackage == null) {
            return moveGridIconToDock(app, effectiveDockTargetIndex)
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
                    targetDockIndex = effectiveDockTargetIndex,
                    targetGridIndex = if (dockPackage == null && hoveredDockPackage == null) {
                        appPageIndex * appGridPageSize + effectiveGridPosition
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
        setHoverTarget(null)
        clearDragHoverPreview()
        stopDragWiggle()
        dragShadow?.let(screenContainer::removeView)
        dragCheckBadge?.let(screenContainer::removeView)
        dragTargetLabel?.let(screenContainer::removeView)
        dragSource?.visibility = dragSourceVisibility
        dragShadow = null
        dragShadowCenteredOnPointer = false
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
                val folderHover = findHoverTarget(event.rawX, event.rawY)
                setHoverTarget(folderHover)
                updateFolderDragPreview(state, event.rawX, event.rawY)
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

    private fun updateFolderDragPreview(state: FolderDragState, x: Float, y: Float) {
        val grid = state.grid
        var targetPosition = -1
        var nearestDistanceSquared = Float.MAX_VALUE
        var targetView: View? = null

        for (childIndex in 0 until grid.childCount) {
            val position = grid.firstVisiblePosition + childIndex
            val app = state.adapter.getItem(position) ?: continue
            if (app.packageName == state.draggedApp.packageName) continue

            val child = grid.getChildAt(childIndex)
            val location = IntArray(2)
            child.getLocationOnScreen(location)
            val dx = x - (location[0] + child.width / 2f)
            val dy = y - (location[1] + child.height / 2f)
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared
                targetPosition = position
                targetView = child
            }
        }

        val threshold = max(
            dp(72),
            targetView?.let { max(it.width, it.height) } ?: 0
        ) * 0.8f
        if (targetView == null || nearestDistanceSquared > threshold * threshold) {
            clearFolderDragPreview(state)
            return
        }

        val location = IntArray(2)
        targetView.getLocationOnScreen(location)
        val dropAfterTarget = x >= location[0] + targetView.width / 2f
        if (targetPosition == state.previewPosition &&
            dropAfterTarget == state.previewDropAfter
        ) return

        val reorderedApps = reorderedFolderApps(state, targetPosition, dropAfterTarget)
            ?: return
        clearFolderDragPreview(state)
        state.previewPosition = targetPosition
        state.previewDropAfter = dropAfterTarget

        for (childIndex in 0 until grid.childCount) {
            val child = grid.getChildAt(childIndex)
            val position = grid.firstVisiblePosition + childIndex
            val app = state.adapter.getItem(position) ?: continue
            if (app.packageName == state.draggedApp.packageName) continue

            val destination = reorderedApps.indexOfFirst {
                it.packageName == app.packageName
            }
            val destinationChild = grid.getChildAt(
                destination - grid.firstVisiblePosition
            ) ?: continue
            val offsetX = destinationChild.left - child.left
            val offsetY = destinationChild.top - child.top
            if (offsetX == 0 && offsetY == 0) continue
            child.animate().cancel()
            child.animate()
                .translationX(offsetX.toFloat())
                .translationY(offsetY.toFloat())
                .setDuration(160L)
                .start()
            state.previewViews += child
        }
    }

    private fun clearFolderDragPreview(state: FolderDragState) {
        state.previewViews.forEach { view ->
            view.animate().cancel()
            view.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(120L)
                .start()
        }
        state.previewViews.clear()
        state.previewPosition = null
    }

    private fun reorderedFolderApps(
        state: FolderDragState,
        targetPosition: Int,
        dropAfterTarget: Boolean
    ): List<AppInfo>? {
        val sourceIndex = state.apps.indexOfFirst {
            it.packageName == state.draggedApp.packageName
        }
        if (sourceIndex < 0) return null

        val targetIndex = (targetPosition + if (dropAfterTarget) 1 else 0)
            .coerceIn(0, state.apps.size)
        val reorderedApps = state.apps.toMutableList().apply {
            removeAt(sourceIndex)
        }
        var insertionIndex = targetIndex
        if (sourceIndex < insertionIndex) insertionIndex--
        reorderedApps.add(insertionIndex.coerceIn(0, reorderedApps.size), state.draggedApp)
        return reorderedApps
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

        val previewHitPosition = if (state.previewPosition != null) {
            (0 until state.grid.childCount).firstNotNullOfOrNull { childIndex ->
                val child = state.grid.getChildAt(childIndex)
                if (child.visibility != View.VISIBLE) return@firstNotNullOfOrNull null
                val location = IntArray(2)
                child.getLocationOnScreen(location)
                val hit = x >= location[0] && x <= location[0] + child.width &&
                    y >= location[1] && y <= location[1] + child.height
                if (hit) state.grid.firstVisiblePosition + childIndex else null
            }
        } else {
            null
        }
        val position = previewHitPosition ?: state.grid.pointToPosition(
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
        val reorderedApps = reorderedFolderApps(
            state,
            if (position == GridView.INVALID_POSITION) state.apps.size else position,
            dropAfterTarget
        ) ?: return

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
        setHoverTarget(null)
        val state = folderDragState ?: return
        folderDragState = null
        clearFolderDragPreview(state)
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
                    updateDragHoverPreview(event.rawX, event.rawY)
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
        if (draggedWidgetPlacement != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    updateDragShadowFromScreen(event.rawX, event.rawY)
                    if (abs(event.x - downX) > swipeTouchSlop ||
                        abs(event.y - downY) > swipeTouchSlop
                    ) {
                        dragMoved = true
                    }
                    navigateAppGridDuringDrag(event.rawX)
                }
                MotionEvent.ACTION_UP -> {
                    finishWidgetDrag(commit = dragMoved)
                    recycleVelocityTracker()
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishWidgetDrag(commit = false)
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
                    } else if (homeSwipeTransition == null && !isAlphabeticalLibrary) {
                        // Boundary: ở trang đầu/cuối → thử bắt đầu HomeSwipeTransition mượt
                        val homeTransition = startHomeSwipe(direction)
                        if (homeTransition != null) {
                            homeSwipeTransition = homeTransition
                            val cancel = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                        }
                    }
                }
                if (appPageTransition != null) {
                    updateAppPageSwipe(appPageTransition, deltaX)
                    return true
                }
                val boundaryHomeTransition = homeSwipeTransition
                if (boundaryHomeTransition != null && currentScreen == Screen.APPS) {
                    updateHomeSwipe(boundaryHomeTransition, deltaX)
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

                        // Ưu tiên settle HomeSwipeTransition đang chạy từ boundary drag
                        val boundaryTransition = homeSwipeTransition
                        if (boundaryTransition != null) {
                            val movedEnough =
                                distance * boundaryTransition.direction / boundaryTransition.width >= 0.5f
                            val flungFarEnough = abs(velocityX) >= swipeMinVelocity &&
                                velocityX * boundaryTransition.direction > 0f
                            updateHomeSwipe(boundaryTransition, distance)
                            settleHomeSwipe(boundaryTransition, movedEnough || flungFarEnough, velocityX)
                            recycleVelocityTracker()
                            return true
                        }

                        val targetPage = appPageIndex + if (direction < 0) 1 else -1
                        if (targetPage in 0 until appPageCount) {
                            // Trang nội bộ: dùng moveAppPageWithSwipe bình thường
                            moveAppPageWithSwipe(direction)
                        } else if (!isAlphabeticalLibrary) {
                            // Boundary: tạo HomeSwipeTransition với animation mượt
                            transition = startHomeSwipe(direction)
                            if (transition != null) {
                                homeSwipeTransition = transition
                            } else {
                                // Fallback không có outgoing view
                                moveAppPageWithSwipe(direction)
                            }
                        }
                        if (transition != null) {
                            val movedEnough =
                                distance * transition.direction / transition.width >= 0.5f
                            val flungFarEnough = abs(velocityX) >= swipeMinVelocity &&
                                velocityX * transition.direction > 0f
                            updateHomeSwipe(transition, distance)
                            settleHomeSwipe(transition, movedEnough || flungFarEnough, velocityX)
                            homeSwipeTransition = null
                        }
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

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
        val filter = IntentFilter().apply {
            addAction(LauncherWallpaper.ACTION_CHANGED)
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            wallpaperChangedReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        wallpaperReceiverRegistered = true
    }

    override fun onStop() {
        if (wallpaperReceiverRegistered) {
            unregisterReceiver(wallpaperChangedReceiver)
            wallpaperReceiverRegistered = false
        }
        appWidgetHost.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        cancelDragPageChange()
        appLoadExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val APP_GRID_TAG = "app_grid_page"
        const val APP_WIDGET_HOST_ID = 1024
        const val APP_REORDER_ANIMATION_DURATION_MS = 160L
        const val DRAG_PAGE_CHANGE_DELAY_MS = 500L
        const val FOLDER_PACKAGE_PREFIX = "launcher.folder:"
        const val LAUNCHER_WIDGET_HEIGHT_DP = 164
        const val TAG = "MainActivity"
        const val WIDGET_DEFAULT_TOP_DP = 10
        const val WIDGET_HORIZONTAL_GAP_DP = 14
        const val WIDGET_SIDE_INSET_DP = 22
        const val WIDGET_VERTICAL_GAP_DP = 12
    }
}