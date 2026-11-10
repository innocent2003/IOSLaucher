package com.example.oslaucher.utils

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import java.io.IOException

object LauncherWallpaper {
    const val ACTION_CHANGED = "com.example.oslaucher.action.WALLPAPER_CHANGED"

    fun applyTo(view: View): Boolean {
        return try {
            val usesSavedWallpaper = LauncherConfig.usesSavedWallpaper(view.context)
            val preferences = view.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val selectedUri =
                if (usesSavedWallpaper) preferences.getString(URI_KEY, null)?.let(Uri::parse) else null
            val selectedWallpaper = try {
                when {
                    selectedUri != null -> {
                        try {
                            view.context.contentResolver.openInputStream(selectedUri)?.use {
                                Drawable.createFromStream(it, null)
                            }.also { drawable ->
                                if (drawable == null) {
                                    Log.w(TAG, "Could not read the saved wallpaper URI: $selectedUri")
                                }
                            }
                        } catch (exception: SecurityException) {
                            Log.w(TAG, "Permission denied to read the saved wallpaper URI: $selectedUri", exception)
                            null
                        }
                    }
                    usesSavedWallpaper && preferences.contains(RESOURCE_KEY) ->
                        view.context.getDrawable(preferences.getInt(RESOURCE_KEY, 0))
                    usesSavedWallpaper -> {
                        saveResource(view.context, DEFAULT_WALLPAPER_RESOURCE)
                        view.context.getDrawable(DEFAULT_WALLPAPER_RESOURCE)
                    }
                    else -> null
                }
            } catch (exception: IOException) {
                Log.w(TAG, "Could not open the saved wallpaper URI: $selectedUri", exception)
                null
            }
            // Only call WallpaperManager if the required permission is already granted.
            // Calling it without permission causes an expensive Binder IPC SecurityException
            // on every invocation (e.g. on each swipe / onResume). When it is not granted we
            // fall back to the bundled wallpaper so the launcher never waits on a prompt.
            val wallpaper = selectedWallpaper ?: run {
                if (hasWallpaperPermission(view.context)) {
                    try {
                        WallpaperManager.getInstance(view.context).drawable
                    } catch (exception: SecurityException) {
                        Log.w(TAG, "Permission denied to read device wallpaper", exception)
                        null
                    }
                } else {
                    null
                }
            } ?: view.context.getDrawable(DEFAULT_WALLPAPER_RESOURCE) ?: return false
            view.background = CenterCropDrawable(copyDrawable(wallpaper, view))
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Permission is required to read the saved or device wallpaper", exception)
            false
        }
    }

    /** Returns true if the app currently holds the permission needed to read the device wallpaper. */
    fun hasWallpaperPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if the user has explicitly chosen a saved wallpaper (resource or URI).
     * Reading a saved wallpaper does not require storage permission.
     */
    fun hasSavedWallpaper(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.contains(URI_KEY) || prefs.contains(RESOURCE_KEY)
    }

    fun notifyChanged(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_CHANGED).setPackage(context.packageName)
        )
    }

    fun saveResource(context: Context, resourceId: Int) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(RESOURCE_KEY, resourceId)
            .remove(URI_KEY)
            .apply()
    }

    fun saveUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(URI_KEY, uri.toString())
            .remove(RESOURCE_KEY)
            .apply()
    }

    fun getSavedResource(context: Context): Int? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return if (preferences.contains(RESOURCE_KEY)) preferences.getInt(RESOURCE_KEY, 0) else null
    }

    fun getSavedUri(context: Context): Uri? =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(URI_KEY, null)
            ?.let(Uri::parse)

    private fun copyDrawable(drawable: Drawable, view: View): Drawable =
        drawable.constantState
            ?.newDrawable(view.resources)
            ?.mutate()
            ?: drawable.mutate()

    private const val PREFERENCES_NAME = "launcher_wallpaper_preferences"
    private const val RESOURCE_KEY = "wallpaper_resource"
    private const val URI_KEY = "wallpaper_uri"
    private val DEFAULT_WALLPAPER_RESOURCE = com.example.oslaucher.R.drawable.wallpaper_preview_1
    private const val TAG = "LauncherWallpaper"
}
