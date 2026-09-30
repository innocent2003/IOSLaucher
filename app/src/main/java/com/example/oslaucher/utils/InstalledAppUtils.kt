package com.example.oslaucher.utils

import android.content.Intent
import android.content.pm.PackageManager
import com.example.oslaucher.models.AppInfo
import java.text.Collator
import java.util.Locale

object InstalledAppUtils {
    fun queryLaunchableApps(packageManager: PackageManager): List<AppInfo> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())

        return packageManager.queryIntentActivities(launchIntent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { applicationInfo ->
                AppInfo(
                    packageName = applicationInfo.packageName,
                    name = packageManager.getApplicationLabel(applicationInfo).toString(),
                    icon = packageManager.getApplicationIcon(applicationInfo)
                )
            }
            .sortedWith { first, second -> collator.compare(first.name, second.name) }
    }

    fun loadCachedApp(packageManager: PackageManager, packageName: String, name: String): AppInfo? {
        return try {
            AppInfo(packageName, name, packageManager.getApplicationIcon(packageName))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}