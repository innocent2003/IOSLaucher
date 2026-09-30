package com.example.oslaucher.repositories

import android.content.Context
import com.example.oslaucher.db.AppDatabase
import com.example.oslaucher.db.CachedApp
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.utils.InstalledAppUtils

class InstalledAppRepository(context: Context) {
    private val packageManager = context.packageManager
    private val database = AppDatabase(context.applicationContext)

    fun loadInstalledApps(): List<AppInfo> {
        return try {
            InstalledAppUtils.queryLaunchableApps(packageManager).also { apps ->
                database.replaceApps(apps.map { CachedApp(it.packageName, it.name) })
            }
        } catch (_: Exception) {
            database.getCachedApps().mapNotNull { cachedApp ->
                InstalledAppUtils.loadCachedApp(packageManager, cachedApp.packageName, cachedApp.name)
            }
        }
    }
}