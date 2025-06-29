package com.b3n00n.snorlax.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object PackageUtils {
    fun getInstalledPackages(context: Context, includeSystemApps: Boolean): List<String> {
        val pm = context.packageManager

        return pm.getInstalledPackages(0)
            .filter { packageInfo ->
                includeSystemApps || !isSystemApp(packageInfo.applicationInfo)
            }
            .map { it.packageName }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }
}