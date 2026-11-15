package com.example.oslaucher.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.oslaucher.ui.PageTransitionAnimator
import com.example.oslaucher.ui.ScreenGridActivity

/**
 * Oslaucher restyles the device screens only while it is the default home app. Once another
 * launcher takes over, every screen falls back to the built-in configuration until Oslaucher
 * becomes the default home again.
 */
object LauncherConfig {
    fun isDefaultHome(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return true
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun gridRows(context: Context, defaultHomeActive: Boolean): Int {
        if (!defaultHomeActive) return ScreenGridActivity.DEFAULT_GRID_ROWS
        return context.getSharedPreferences(ScreenGridActivity.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(ScreenGridActivity.GRID_ROWS_KEY, ScreenGridActivity.DEFAULT_GRID_ROWS)
            .coerceIn(ScreenGridActivity.MIN_GRID_ROWS, ScreenGridActivity.MAX_GRID_ROWS)
    }

    fun pageTransitionEffect(context: Context): Int {
        return context.getSharedPreferences(PageTransitionAnimator.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(PageTransitionAnimator.EFFECT_KEY, PageTransitionAnimator.DEFAULT)
            .coerceIn(PageTransitionAnimator.DEFAULT, PageTransitionAnimator.FADE)
    }

    fun usesSavedWallpaper(context: Context): Boolean = isDefaultHome(context)
}
