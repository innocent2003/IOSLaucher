package com.example.oslaucher.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * Live icon for the phone's calendar app: black rounded square with today's weekday in red
 * ("TH 2") and the day of the month in large white digits ("23"), redrawn when the day changes.
 */
object CalendarIconUtils {
    private const val CORNER_RATIO = 0.225f
    private const val WEEKDAY_COLOR = 0xFFFF3B30.toInt()

    private val FALLBACK_CALENDAR_PACKAGES = listOf(
        "com.google.android.calendar",
        "com.coloros.calendar",
        "com.android.calendar",
        "com.samsung.android.calendar",
        "com.miui.calendar"
    )

    @Volatile private var calendarPackage: String? = null
    @Volatile private var packageResolved = false
    private var cachedKey: String? = null
    private var cachedIcon: Drawable? = null

    /** Today's key, e.g. "2026-10-23" + locale; changes at midnight. */
    fun dayKey(context: Context): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}-" +
            context.resources.configuration.locales[0]
    }

    fun isCalendarApp(context: Context, packageName: String): Boolean =
        packageName == resolveCalendarPackage(context)

    /** The live calendar icon when [packageName] is the calendar app, otherwise null. */
    fun iconFor(context: Context, packageName: String): Drawable? {
        if (!isCalendarApp(context, packageName)) return null
        val size = IconMetrics.iconSizePx(context).coerceIn(48, 192)
        val key = "${dayKey(context)}-$size"
        synchronized(this) {
            if (key == cachedKey) cachedIcon?.let { return it }
        }
        val icon = BitmapDrawable(context.resources, draw(context, size))
        synchronized(this) {
            cachedKey = key
            cachedIcon = icon
        }
        return icon
    }

    private fun resolveCalendarPackage(context: Context): String? {
        if (packageResolved) return calendarPackage
        val packageManager = context.packageManager
        val resolved = packageManager.resolveActivity(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName?.takeUnless { it == "android" }
            ?: FALLBACK_CALENDAR_PACKAGES.firstOrNull { candidate ->
                packageManager.getLaunchIntentForPackage(candidate) != null
            }
        calendarPackage = resolved
        packageResolved = true
        return resolved
    }

    private fun draw(context: Context, size: Int): Bitmap {
        val locale = context.resources.configuration.locales[0]
        val now = Date()
        val weekday = SimpleDateFormat("EEE", locale).format(now).uppercase(locale)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size * CORNER_RATIO
        canvas.drawRoundRect(
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
        )

        val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WEEKDAY_COLOR
            textAlign = Paint.Align.CENTER
            textSize = size * 0.17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(weekday, size / 2f, size * 0.29f, weekdayPaint)

        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            textSize = size * 0.56f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(day, size / 2f, size * 0.80f, dayPaint)
        return bitmap
    }
}
