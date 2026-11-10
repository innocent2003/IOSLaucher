package com.example.oslaucher.utils

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.oslaucher.models.AppInfo
import java.text.Collator
import java.util.Locale

object InstalledAppUtils {
    fun queryLaunchableApps(packageManager: PackageManager): List<AppInfo> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.getDefault())

        // Use the non-deprecated ResolveInfoFlags API on Android 13+ to avoid log warnings
        @Suppress("DEPRECATION")
        val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launchIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            packageManager.queryIntentActivities(launchIntent, 0)
        }

        return resolvedActivities
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
