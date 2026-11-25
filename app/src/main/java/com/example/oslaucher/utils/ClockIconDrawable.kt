package com.example.oslaucher.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.provider.AlarmClock
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Live icon for the phone's clock app: a black rounded square with an analog face whose
 * hands show the current time; the orange second hand ticks every second while the icon is
 * on screen (it stops ticking when hidden, so it costs nothing off-screen).
 */
class ClockIconDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBounds = Rect()
    private val rect = RectF()
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = min(b.width(), b.height()).toFloat()
        if (size <= 0f) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val half = size / 2f

        // Background.
        paint.style = Paint.Style.FILL
        paint.color = BACKGROUND
        rect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(rect, size * CORNER_RATIO, size * CORNER_RATIO, paint)

        val radius = half * 0.86f
        // Minute ticks.
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val angle = Math.toRadians(i * 6.0 - 90)
            val outer = radius
            val inner = radius - size * (if (major) 0.07f else 0.04f)
            paint.color = if (major) 0xFFFFFFFF.toInt() else 0xFF8E8E93.toInt()
            paint.strokeWidth = size * (if (major) 0.018f else 0.010f)
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer,
                paint
            )
        }

        // 12 / 3 / 6 / 9.
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = size * 0.15f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val numberRadius = radius - size * 0.15f
        listOf(12, 3, 6, 9).forEach { number ->
            val angle = Math.toRadians(number * 30.0 - 90)
            val text = number.toString()
            paint.getTextBounds(text, 0, text.length, textBounds)
            canvas.drawText(
                text,
                cx + cos(angle).toFloat() * numberRadius - textBounds.exactCenterX(),
                cy + sin(angle).toFloat() * numberRadius - textBounds.exactCenterY(),
                paint
            )
        }

        val now = Calendar.getInstance()
        val seconds = now.get(Calendar.SECOND)
        val minutes = now.get(Calendar.MINUTE) + seconds / 60f
        val hours = now.get(Calendar.HOUR) + minutes / 60f

        hand(canvas, cx, cy, hours * 30f, radius * 0.50f, 0f, size * 0.055f, 0xFFFFFFFF.toInt())
        hand(canvas, cx, cy, minutes * 6f, radius * 0.80f, 0f, size * 0.040f, 0xFFFFFFFF.toInt())
        hand(canvas, cx, cy, seconds * 6f, radius * 0.88f, radius * 0.18f, size * 0.016f, SECOND_HAND)

        paint.style = Paint.Style.FILL
        paint.color = SECOND_HAND
        canvas.drawCircle(cx, cy, size * 0.035f, paint)
        paint.color = BACKGROUND
        canvas.drawCircle(cx, cy, size * 0.014f, paint)

        scheduleNextTick()
    }

    private fun hand(
        canvas: Canvas, cx: Float, cy: Float, degrees: Float,
        length: Float, tail: Float, width: Float, color: Int
    ) {
        val angle = Math.toRadians(degrees - 90.0)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width
        paint.color = color
        canvas.drawLine(cx - dx * tail, cy - dy * tail, cx + dx * length, cy + dy * length, paint)
    }

    private fun scheduleNextTick() {
        if (ticking || callback == null || !isVisible) return
        ticking = true
        val now = SystemClock.uptimeMillis()
        // Land on the next full second.
        scheduleSelf(tick, now + (1000 - System.currentTimeMillis() % 1000))
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) {
            unscheduleSelf(tick)
            ticking = false
        } else if (changed) {
            invalidateSelf()
        }
        return changed
    }

    override fun getIntrinsicWidth(): Int = -1
    override fun getIntrinsicHeight(): Int = -1
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val CORNER_RATIO = 0.225f
        private const val BACKGROUND = 0xFF000000.toInt()
        private const val SECOND_HAND = 0xFFFF9500.toInt()

        private val FALLBACK_CLOCK_PACKAGES = listOf(
            "com.google.android.deskclock",
            "com.coloros.alarmclock",
            "com.oplus.alarmclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.BBKClock"
        )

        @Volatile private var clockPackage: String? = null
        @Volatile private var resolved = false

        /** A new live clock icon when [packageName] is the clock app, otherwise null. */
        fun iconFor(context: Context, packageName: String): Drawable? =
            if (packageName == resolveClockPackage(context)) ClockIconDrawable() else null

        private fun resolveClockPackage(context: Context): String? {
            if (resolved) return clockPackage
            val packageManager = context.packageManager
            clockPackage = packageManager.resolveActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName?.takeUnless { it == "android" }
                ?: FALLBACK_CLOCK_PACKAGES.firstOrNull {
                    packageManager.getLaunchIntentForPackage(it) != null
                }
            resolved = true
            return clockPackage
        }
    }
}
