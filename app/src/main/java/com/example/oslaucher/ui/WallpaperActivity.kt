package com.example.oslaucher.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max

class WallpaperActivity : AppCompatActivity() {
    private val wallpaperResources = listOf(
        R.drawable.wallpaper_preview_1,
        R.drawable.wallpaper_preview_2,
        R.drawable.wallpaper_preview_3
    )
    private lateinit var lockPreview: ImageView
    private lateinit var homePreview: ImageView
    private lateinit var previews: View
    private lateinit var dots: List<TextView>
    private var selectedIndex = 0
    private var selectedImageUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private var downX = 0f
    private var downY = 0f
    private var trackingPreviewSwipe = false
    private var previewSwipeActive = false
    private var previewSwipeSettling = false
    private var ignoreTouchSequence = false
    private var velocityTracker: VelocityTracker? = null
    private val swipeTouchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val swipeMinVelocity by lazy { ViewConfiguration.get(this).scaledMinimumFlingVelocity }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var errorMessage: String? = null
            val bitmap = try {
                decodePreview(uri)
            } catch (exception: IOException) {
                errorMessage = "Không thể mở ảnh: ${exception.localizedMessage}"
                null
            }
            if (bitmap != null) {
                val previousBitmap = selectedBitmap
                selectedImageUri = uri
                selectedBitmap = bitmap
                updatePreview()
                previousBitmap?.recycle()
            } else {
                Toast.makeText(
                    this,
                    errorMessage ?: "Không thể mở ảnh đã chọn.",
                    if (errorMessage == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_wallpaper)

        val root = findViewById<View>(R.id.wallpaper_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lockPreview = findViewById(R.id.wallpaper_lock_preview)
        homePreview = findViewById(R.id.wallpaper_home_preview)
        previews = findViewById(R.id.wallpaper_previews)
        dots = listOf(
            findViewById(R.id.wallpaper_dot_0),
            findViewById(R.id.wallpaper_dot_1),
            findViewById(R.id.wallpaper_dot_2)
        )

        findViewById<View>(R.id.wallpaper_back).setOnClickListener { finish() }
        findViewById<View>(R.id.add_wallpaper).setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }
        findViewById<View>(R.id.set_wallpaper).setOnClickListener { applyWallpaper() }
        dots.forEachIndexed { index, dot ->
            dot.setOnClickListener {
                selectWallpaper(index)
            }
        }
        updatePreview()
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
        if (previewSwipeSettling) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                ignoreTouchSequence = true
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                recycleVelocityTracker()
                val location = IntArray(2)
                previews.getLocationOnScreen(location)
                trackingPreviewSwipe = event.rawX >= location[0] &&
                    event.rawX <= location[0] + previews.width &&
                    event.rawY >= location[1] &&
                    event.rawY <= location[1] + previews.height
                previewSwipeActive = false
                if (trackingPreviewSwipe) {
                    downX = event.rawX
                    downY = event.rawY
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (trackingPreviewSwipe) {
                    velocityTracker?.addMovement(event)
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!previewSwipeActive &&
                        abs(deltaX) > swipeTouchSlop &&
                        abs(deltaX) > abs(deltaY) * 1.2f
                    ) {
                        previewSwipeActive = true
                        val cancel = MotionEvent.obtain(event).apply {
                            action = MotionEvent.ACTION_CANCEL
                        }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                    if (previewSwipeActive) {
                        previews.translationX = deltaX
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (trackingPreviewSwipe) {
                    velocityTracker?.addMovement(event)
                    if (previewSwipeActive) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val velocityX = velocityTracker?.xVelocity ?: 0f
                        val distance = event.rawX - downX
                        val width = previews.width.takeIf { it > 0 }?.toFloat()
                            ?: resources.displayMetrics.widthPixels.toFloat()
                        val distanceSwipe = abs(distance) >= max(swipeTouchSlop * 2f, width * 0.18f) &&
                            abs(distance) > abs(event.rawY - downY) * 1.2f
                        val velocitySwipe = abs(velocityX) >= swipeMinVelocity &&
                            abs(velocityX) > abs(velocityTracker?.yVelocity ?: 0f) * 1.2f
                        val direction = if ((distanceSwipe && distance < 0f) ||
                            (!distanceSwipe && velocitySwipe && velocityX < 0f)
                        ) -1 else 1
                        val targetIndex = selectedIndex - direction
                        settlePreviewSwipe(
                            commit = (distanceSwipe || velocitySwipe) &&
                                targetIndex in wallpaperResources.indices,
                            direction = direction,
                            targetIndex = targetIndex
                        )
                        recycleVelocityTracker()
                        trackingPreviewSwipe = false
                        previewSwipeActive = false
                        return true
                    }
                    trackingPreviewSwipe = false
                    recycleVelocityTracker()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (trackingPreviewSwipe) {
                    if (previewSwipeActive) {
                        settlePreviewSwipe(commit = false, direction = 0, targetIndex = selectedIndex)
                        trackingPreviewSwipe = false
                        previewSwipeActive = false
                        recycleVelocityTracker()
                        return true
                    }
                    trackingPreviewSwipe = false
                    recycleVelocityTracker()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun settlePreviewSwipe(commit: Boolean, direction: Int, targetIndex: Int) {
        val width = previews.width.takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        val duration = 260L
        val easing = PathInterpolator(0.2f, 0f, 0.2f, 1f)
        previewSwipeSettling = true

        if (!commit) {
            previews.animate()
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(easing)
                .withEndAction {
                    previewSwipeSettling = false
                }
                .start()
            return
        }

        previews.animate()
            .translationX(direction * width)
            .setDuration(duration)
            .setInterpolator(easing)
            .withEndAction {
                selectWallpaper(targetIndex)
                previews.translationX = -direction * width
                previews.animate()
                    .translationX(0f)
                    .setDuration(duration)
                    .setInterpolator(easing)
                    .withEndAction {
                        previewSwipeSettling = false
                    }
                    .start()
            }
            .start()
    }

    private fun selectWallpaper(index: Int) {
        val previousBitmap = selectedBitmap
        selectedIndex = index
        selectedImageUri = null
        selectedBitmap = null
        updatePreview()
        previousBitmap?.recycle()
    }

    private fun updatePreview() {
        val uri = selectedImageUri
        val bitmap = selectedBitmap
        if (uri != null && bitmap != null) {
            lockPreview.setImageBitmap(bitmap)
            homePreview.setImageBitmap(bitmap)
        } else {
            lockPreview.setImageResource(wallpaperResources[selectedIndex])
            homePreview.setImageResource(wallpaperResources[selectedIndex])
        }
        dots.forEachIndexed { index, dot ->
            dot.setTextColor(if (index == selectedIndex && uri == null) 0xFFFFFFFF.toInt() else 0xFF777777.toInt())
        }
    }

    private fun decodePreview(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetSize = (resources.displayMetrics.widthPixels / 2).coerceAtLeast(1)
        val options = BitmapFactory.Options()
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetSize &&
            bounds.outHeight / (sampleSize * 2) >= targetSize
        ) {
            sampleSize *= 2
        }
        options.inSampleSize = sampleSize
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun applyWallpaper() {
        try {
            val uri = selectedImageUri
            if (uri != null) {
                contentResolver.openInputStream(uri)?.use {
                    WallpaperManager.getInstance(this).setStream(
                        it,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM
                    )
                } ?: run {
                    Toast.makeText(this, "Không thể mở ảnh đã chọn.", Toast.LENGTH_SHORT).show()
                    return
                }
            } else {
                val drawable = getDrawable(wallpaperResources[selectedIndex]) ?: run {
                    Toast.makeText(this, "Không thể tải hình nền.", Toast.LENGTH_SHORT).show()
                    return
                }
                val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).also { canvas ->
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
                try {
                    WallpaperManager.getInstance(this)
                        .setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                } finally {
                    bitmap.recycle()
                }
            }
            Toast.makeText(this, "Đã đặt hình nền màn hình chính.", Toast.LENGTH_SHORT).show()
        } catch (exception: IOException) {
            Toast.makeText(this, "Không thể đặt hình nền: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
        } catch (exception: SecurityException) {
            Toast.makeText(this, "Không có quyền đặt hình nền trên thiết bị này.", Toast.LENGTH_LONG).show()
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
