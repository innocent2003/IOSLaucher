package com.example.oslaucher.repositories

import android.content.Context
import com.example.oslaucher.db.AppDatabase
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.models.CachedApp
import com.example.oslaucher.utils.InstalledAppUtils
import java.util.concurrent.Executors

class InstalledAppRepository(context: Context) {
    private val packageManager = context.packageManager
    private val database = AppDatabase(context.applicationContext)
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    fun loadInstalledApps(): List<AppInfo> {
        val cached = database.getCachedApps().mapNotNull { cachedApp ->
            InstalledAppUtils.loadCachedApp(packageManager, cachedApp.packageName, cachedApp.name)
        }
        if (cached.isNotEmpty()) {
            backgroundExecutor.execute {
                try {
                    val freshApps = InstalledAppUtils.queryLaunchableApps(packageManager)
                    database.replaceApps(freshApps.map { CachedApp(it.packageName, it.name) })
                } catch (_: Exception) {}
            }
            return cached
        }

        return try {
            InstalledAppUtils.queryLaunchableApps(packageManager).also { apps ->
                database.replaceApps(apps.map { CachedApp(it.packageName, it.name) })
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}