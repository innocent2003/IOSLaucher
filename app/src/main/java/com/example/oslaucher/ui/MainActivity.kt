package com.example.oslaucher.ui

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
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
import com.example.oslaucher.utils.Screen
import java.util.concurrent.Executors
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
    private var isLoadingApps = false
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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

        when (screen) {
            Screen.SETTINGS -> view.findViewById<View>(R.id.open_home).setOnClickListener {
                showScreen(Screen.HOME, 1)
            }
            Screen.HOME, Screen.HOME_SECOND -> Unit
            Screen.APPS -> {
                val pages = view.findViewById<ViewFlipper>(R.id.apps_pages)
                val pageIndicator = view.findViewById<LinearLayout>(R.id.apps_page_indicator)
                appsPages = pages
                appsIndicator = pageIndicator
                appPageIndex = 0
                val dock = view.findViewById<LinearLayout>(R.id.apps_dock)
                val appLauncher: (AppInfo) -> Unit = { app ->
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
                }
                val appLongPress: (AppInfo, View) -> Unit = { app, anchor ->
                    showAppOptions(app, anchor)
                }

                fun buildHomeApps(apps: List<AppInfo>) {
                    val visibleApps = apps.filterNot { it.packageName in hiddenAppPackages }
                    val dockApps = visibleApps.take(4)
                    val gridApps = visibleApps.drop(dockApps.size)
                    val appPageSize = 28
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
                            adapter = AppAdapter(this@MainActivity, appLauncher, appLongPress).apply {
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

                    dock.removeAllViews()
                    dockApps.forEach { app ->
                        val icon = ImageView(this).apply {
                            setImageDrawable(app.icon)
                            contentDescription = app.name
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(dp(6), dp(4), dp(6), dp(4))
                            setOnClickListener { appLauncher(app) }
                        }
                        dock.addView(
                            icon,
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        )
                    }
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
        appPageIndex = pageIndex.coerceIn(0, appPageCount - 1)
        appsPages?.displayedChild = appPageIndex
        updateAppPageIndicator()
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
        val easing = PathInterpolator(0.2f, 0f, 0.2f, 1f)
        val duration = 260L

        pages.displayedChild = targetPage
        outgoingView.visibility = View.VISIBLE
        incomingView.visibility = View.VISIBLE
        incomingView.translationX = -direction * width
        homeSwipeSettling = true

        outgoingView.animate()
            .translationX(direction * width)
            .setDuration(duration)
            .setInterpolator(easing)
            .withEndAction {
                outgoingView.translationX = 0f
                incomingView.translationX = 0f
                pages.displayedChild = targetPage
                appPageIndex = targetPage
                updateAppPageIndicator()
                homeSwipeSettling = false
            }
            .start()
        incomingView.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(easing)
            .start()
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
            Toast.makeText(this, "Đã chọn ${app.name}", Toast.LENGTH_SHORT).show()
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
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val velocityY = velocityTracker?.yVelocity ?: 0f
                val distance = event.x - downX
                var transition = homeSwipeTransition

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