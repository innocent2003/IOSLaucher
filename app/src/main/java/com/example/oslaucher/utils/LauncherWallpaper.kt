package com.example.oslaucher.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat

object LauncherWallpaper {
    const val ACTION_CHANGED = "com.example.oslaucher.action.WALLPAPER_CHANGED"

    /**
     * Launcher screens are transparent: the window shows the phone's system wallpaper
     * (android:windowShowWallpaper), so the launcher always matches the wallpaper set on the
     * device — including one chosen in our wallpaper screen, which is applied system-wide.
     * No storage permission and no bitmap decoding are needed, and swipes stay light because
     * pages no longer carry a full-screen wallpaper bitmap.
     */
    fun applyTo(view: View): Boolean {
        if (view.background != null) view.background = null
        return true
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

    /** Forget the decoded wallpaper; call whenever the chosen or system wallpaper changes. */
    fun invalidateCache() = synchronized(this) {
        cachedKey = null
        cachedDrawable = null
    }

    /** The system wallpaper can change while the launcher is stopped; drop it on return. */
    fun invalidateSystemCache() = synchronized(this) {
        if (cachedKey == "system") {
            cachedKey = null
            cachedDrawable = null
        }
    }

    fun notifyChanged(context: Context) {
        invalidateCache()
        context.sendBroadcast(
            Intent(ACTION_CHANGED).setPackage(context.packageName)
        )
    }

    fun saveResource(context: Context, resourceId: Int) {
        invalidateCache()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(RESOURCE_KEY, resourceId)
            .remove(URI_KEY)
            .apply()
    }

    fun saveUri(context: Context, uri: Uri) {
        invalidateCache()
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

    private var cachedKey: String? = null
    private var cachedDrawable: Drawable? = null

    private const val PREFERENCES_NAME = "launcher_wallpaper_preferences"
    private const val RESOURCE_KEY = "wallpaper_resource"
    private const val URI_KEY = "wallpaper_uri"
    private val DEFAULT_WALLPAPER_RESOURCE = com.example.oslaucher.R.drawable.wallpaper_preview_1
    private const val TAG = "LauncherWallpaper"
}
