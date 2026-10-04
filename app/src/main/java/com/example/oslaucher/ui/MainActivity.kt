package com.example.oslaucher.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
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
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity(), HomeScreenActions {
    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector: GestureDetector
    private lateinit var screenContainer: FrameLayout
    private var appsPages: ViewFlipper? = null
    private var appsIndicator: LinearLayout? = null
    private var appPageIndex = 0
    private var appPageCount = 1
    private var downX = 0f
    private var downY = 0f
    private var homeSwipeTransition: HomeSwipeTransition? = null
    private var homeSwipeSettling = false
    private var ignoreTouchSequence = false
    private var velocityTracker: VelocityTracker? = null
    private val swipeTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val swipeMinVelocity by lazy { ViewConfiguration.get(this).scaledMinimumFlingVelocity }

    private data class HomeSwipeTransition(
        val fromScreen: Screen,
        val toScreen: Screen,
        val incomingFragment: Fragment,
        val incomingView: View,
        val outgoingFragment: Fragment,
        val outgoingView: View,
        val width: Float,
        val direction: Int
    )

    private val screenLayouts = mapOf(
        Screen.SETTINGS to R.layout.activity_main,
        Screen.APPS to R.layout.screen_apps
    )

    private enum class Screen { SETTINGS, HOME, HOME_SECOND, APPS }
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
            fromScreen == Screen.HOME && direction < 0 -> Screen.HOME_SECOND
            fromScreen == Screen.HOME_SECOND && direction > 0 -> Screen.HOME
            else -> return null
        }
        val fragmentManager = supportFragmentManager
        val outgoingFragment = fragmentManager.findFragmentById(R.id.screen_container) ?: return null
        val outgoingView = outgoingFragment.view ?: return null
        val incomingFragment = when (toScreen) {
            Screen.HOME -> HomeFragment()
            Screen.HOME_SECOND -> HomeSecondFragment()
            else -> return null
        }
        fragmentManager.beginTransaction()
            .add(R.id.screen_container, incomingFragment)
            .commitNow()
        val incomingView = incomingFragment.view ?: run {
            fragmentManager.beginTransaction().remove(incomingFragment).commitNow()
            return null
        }
        val width = screenContainer.width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        incomingView.translationX = -direction * width
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
        transition.outgoingView.translationX = offset
        transition.incomingView.translationX =
            offset - transition.direction * transition.width
    }

    private fun settleHomeSwipe(transition: HomeSwipeTransition, commit: Boolean, velocityX: Float) {
        homeSwipeSettling = true
        val outgoingTarget = if (commit) transition.direction * transition.width else 0f
        val incomingTarget = if (commit) 0f else -transition.direction * transition.width
        val outgoingDistance = abs(outgoingTarget - transition.outgoingView.translationX)
        val speed = max(abs(velocityX), transition.width / 0.36f)
        val duration = (outgoingDistance / speed * 1000f).toLong().coerceIn(150L, 360L)
        val easing = PathInterpolator(0.2f, 0f, 0.2f, 1f)

        fun finishTransition() {
            if (commit) {
                currentScreen = transition.toScreen
                lastHomeScreen = transition.toScreen
                supportFragmentManager.beginTransaction()
                    .remove(transition.outgoingFragment)
                    .commitNow()
            } else {
                supportFragmentManager.beginTransaction()
                    .remove(transition.incomingFragment)
                    .commitNow()
                transition.outgoingView.translationX = 0f
            }
            homeSwipeTransition = null
            homeSwipeSettling = false
        }

        transition.outgoingView.animate()
            .translationX(outgoingTarget)
            .setDuration(duration)
            .setInterpolator(easing)
            .withEndAction(::finishTransition)
            .start()
        transition.incomingView.animate()
            .translationX(incomingTarget)
            .setDuration(duration)
            .setInterpolator(easing)
            .start()
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
            Screen.HOME, Screen.HOME_SECOND -> Unit
            Screen.APPS -> {
                val appPagesView = view.findViewById<ViewFlipper>(R.id.apps_pages)
                val pageIndicator = view.findViewById<LinearLayout>(R.id.apps_page_indicator)
                appsPages = appPagesView
                appsIndicator = pageIndicator
                appPageIndex = 0
                val appCount = view.findViewById<TextView>(R.id.apps_count)
                val searchBox = view.findViewById<EditText>(R.id.app_search)
                val appLauncher: (AppInfo) -> Unit = { app ->
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                }

                fun buildPages(apps: List<AppInfo>, query: String = searchBox.text?.toString().orEmpty()) {
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

    override fun openApps() {
        showScreen(Screen.APPS, 2)
    }

    override fun openSettings() {
        showScreen(Screen.SETTINGS, -1)
    }

    private fun getCurrentAppListSnapshot(): List<AppInfo> {
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
                    (currentScreen == Screen.HOME || currentScreen == Screen.HOME_SECOND) &&
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
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                val transition = homeSwipeTransition
                if (transition != null) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val distance = event.x - downX
                    val movedEnough =
                        distance * transition.direction / transition.width >= 0.33f
                    val flungFarEnough = abs(velocityX) >= swipeMinVelocity &&
                        velocityX * transition.direction > 0f
                    updateHomeSwipe(transition, distance)
                    settleHomeSwipe(transition, movedEnough || flungFarEnough, velocityX)
                    recycleVelocityTracker()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                val transition = homeSwipeTransition
                if (transition != null) {
                    settleHomeSwipe(transition, commit = false, velocityX = 0f)
                    recycleVelocityTracker()
                    return true
                }
            }
        }

        val handled = super.dispatchTouchEvent(event)
        gestureDetector.onTouchEvent(event)
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
}