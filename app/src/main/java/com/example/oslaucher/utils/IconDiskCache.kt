package com.example.oslaucher.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Disk cache of the launcher's shaped app icons (cacheDir/app_icons).
 *
 * Loading an icon from PackageManager and reshaping it costs tens of milliseconds per app;
 * with the cache, later launches decode a small PNG instead, so the loading screen is short
 * and nothing has to be prepared again after it. An entry is keyed by package, the app's
 * last update time and the icon size, so an updated app or a new display size re-renders.
 */
object IconDiskCache {
    private const val TAG = "IconDiskCache"
    private const val DIRECTORY = "app_icons"

    fun shapedIcon(context: Context, packageManager: PackageManager, packageName: String): Drawable {
        val updatedAt = try {
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
        val size = IconMetrics.iconSizePx(context)
        val directory = File(context.cacheDir, DIRECTORY)
        val file = File(directory, "${packageName}_${updatedAt}_$size.png")
        if (file.isFile) {
            BitmapFactory.decodeFile(file.path)?.let { return BitmapDrawable(context.resources, it) }
        }
        val shaped = IconShapeUtils.normalize(context, packageManager.getApplicationIcon(packageName))
        (shaped as? BitmapDrawable)?.bitmap?.let { bitmap -> save(directory, packageName, file, bitmap) }
        return shaped
    }

    private fun save(directory: File, packageName: String, file: File, bitmap: Bitmap) {
        try {
            directory.mkdirs()
            // Drop older entries of this app (previous version or size).
            directory.listFiles { candidate -> candidate.name.startsWith("${packageName}_") }
                ?.forEach { it.delete() }
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (error: IOException) {
            Log.w(TAG, "Could not cache icon for $packageName", error)
        }
    }
}
