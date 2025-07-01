package com.b3n00n.snorlax.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

object PackageUtils {
    fun getInstalledPackages(context: Context, includeSystemApps: Boolean): List<String> {
        val pm = context.packageManager

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getLaunchableApps(pm, includeSystemApps)
            } else {
                getInstalledPackagesLegacy(pm, includeSystemApps)
            }
        } catch (e: Exception) {
            getLaunchableApps(pm, false)
        }
    }

    private fun getInstalledPackagesLegacy(pm: PackageManager, includeSystemApps: Boolean): List<String> {
        return try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
                .filter { packageInfo ->
                    val appInfo = packageInfo.applicationInfo
                    if (appInfo == null) {
                        false
                    } else {
                        includeSystemApps || !isSystemApp(appInfo)
                    }
                }
                .map { it.packageName }
        } catch (e: Exception) {
            getLaunchableApps(pm, includeSystemApps)
        }
    }

    private fun getLaunchableApps(pm: PackageManager, includeSystemApps: Boolean = false): List<String> {
        return try {
            val allPackages = mutableSetOf<String>()

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val launchableApps = pm.queryIntentActivities(mainIntent, 0)
                .map { it.activityInfo.packageName }

            allPackages.addAll(launchableApps)

            if (includeSystemApps) {
                try {
                    val categories = listOf(
                        Intent.CATEGORY_DEFAULT,
                        Intent.CATEGORY_BROWSABLE
                    )

                    for (category in categories) {
                        val intent = Intent(Intent.ACTION_MAIN, null).apply {
                            addCategory(category)
                        }
                        pm.queryIntentActivities(intent, 0)
                            .forEach { allPackages.add(it.activityInfo.packageName) }
                    }
                } catch (e: Exception) {
                }
            }

            allPackages.toList().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isSystemApp(appInfo: ApplicationInfo?): Boolean {
        return appInfo?.let { info ->
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } ?: true
    }
}